package com.kandc.acscore.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.kandc.acscore.data.local.DbProvider
import com.kandc.acscore.data.local.ScoreEntity
import com.kandc.acscore.share.crypto.AcsetCrypto
import com.kandc.acscore.shared.share.AcsetManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class AcsetImporter(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        private const val TAG = "AcsetImporter"
    }

    sealed class Result {
        data class Success(
            val setlistTitle: String?,
            val orderedScoreIds: List<String>,
            val importedCount: Int,
            val skippedDuplicateCount: Int,
        ) : Result()

        data class Failure(val reason: String, val error: Throwable? = null) : Result()
    }

    /**
     * ✅ .acset Uri를 받아서:
     * 1) bytes 로드
     * 2) 복호화하여 zip bytes 획득
     * 3) zip에서 manifest + pdf 추출
     * 4) (contentHash 없으면 sha256로 생성)
     * 5) contentHash로 중복 체크 후 filesDir/scores/에 저장 + DB upsert
     */
    suspend fun importFromUri(context: Context, acsetUri: Uri): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver

                val acsetBytes = resolver.openInputStream(acsetUri)?.use { it.readBytes() }
                    ?: return@withContext Result.Failure("파일을 열 수 없어요 (InputStream null)")

                // ✅ acset → zip bytes 복호화 (context 필요)
                val zipBytes = AcsetCrypto.decryptAcsetToZipBytes(context, acsetBytes)

                val scoresDir = File(context.filesDir, "scores").apply { mkdirs() }
                val dao = DbProvider.get(context).scoreDao()

                var manifestJson: String? = null

                // zip 안의 pdf entry들을 임시로 저장
                // 1) key 매칭용: (scores/<name>.pdf 에서 name)
                val extractedByKey = mutableMapOf<String, File>()
                // 2) 순서 fallback용
                val extractedQueue = mutableListOf<Pair<String, File>>() // (entryName, tmpFile)

                ZipInputStream(zipBytes.inputStream()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name

                        if (!entry.isDirectory) {
                            when {
                                name.equals("manifest.json", ignoreCase = true) -> {
                                    manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                                }

                                name.startsWith("scores/") && name.endsWith(".pdf", ignoreCase = true) -> {
                                    val key = name.removePrefix("scores/").removeSuffix(".pdf")
                                    val tmp = File.createTempFile("acset_", ".pdf", context.cacheDir)
                                    FileOutputStream(tmp).use { out -> zis.copyTo(out) }

                                    // key로도 보관하고, 순서 큐에도 넣는다
                                    extractedByKey[key] = tmp
                                    extractedQueue += (name to tmp)
                                }
                            }
                        }

                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                val manifestRaw = manifestJson ?: return@withContext Result.Failure("manifest.json이 없어요.")
                val manifest = json.decodeFromString(AcsetManifest.serializer(), manifestRaw)

                if (manifest.items.isEmpty()) {
                    // items가 비어있는데 pdf가 있으면? -> 여기서는 실패로 두는 게 안전
                    extractedByKey.values.forEach { runCatching { it.delete() } }
                    return@withContext Result.Failure("manifest에 곡 정보(items)가 없어요.")
                }

                // 2) 순서대로 import
                val orderedIds = mutableListOf<String>()
                var imported = 0
                var dupSkipped = 0

                // queue 사용을 위해 "아직 안 쓴 tmpFile" 추적
                val usedTmpFiles = HashSet<File>()

                fun takeTempPdfForItem(item: AcsetManifest.Item): File? {
                    // 1) manifest의 contentHash가 있으면 그걸 우선 키로 찾는다
                    val rawHash = item.contentHash.trim()
                    if (rawHash.isNotBlank()) {
                        extractedByKey[rawHash]?.let { return it }
                    }

                    // 2) fileName이 있으면 scores/<fileName> 기반으로도 한번 시도
                    //    (zip 제작자가 scores/original.pdf 형태로 넣었을 수도 있어서)
                    val fileName = item.fileName?.trim().orEmpty()
                    if (fileName.isNotBlank()) {
                        val base = fileName.removeSuffix(".pdf")
                        extractedByKey[base]?.let { return it }
                    }

                    // 3) 마지막 fallback: 아직 안쓴 pdf를 순서대로 하나 꺼낸다
                    val next = extractedQueue.firstOrNull { (_, f) -> !usedTmpFiles.contains(f) }?.second
                    return next
                }

                for (item in manifest.items) {
                    val tempPdf = takeTempPdfForItem(item)
                        ?: return@withContext Result.Failure("악보가 누락됐어요: ${item.title}")

                    usedTmpFiles += tempPdf

                    // ✅ contentHash가 없으면 "PDF 내용 기반 sha256"로 생성
                    val computedHash = run {
                        val raw = item.contentHash.trim()
                        if (raw.isNotBlank()) raw else sha256Hex(tempPdf.readBytes())
                    }

                    // ✅ contentHash로 중복 확인
                    val existing = dao.findByContentHash(computedHash)
                    if (existing != null) {
                        val existingFile = File(scoresDir, existing.fileName)
                        if (existingFile.exists()) {
                            orderedIds += existing.id
                            dupSkipped++
                            continue
                        }
                        // DB는 있는데 파일이 없으면 복원
                    }

                    // ✅ 저장 파일명 정책:
                    // - 실제 저장 파일명은 원본 파일명(or title) 기반
                    // - 충돌 시 (2), (3) ...
                    // - dedup은 contentHash(없으면 sha256)로 유지
                    val desiredName = item.fileName?.trim().takeUnless { it.isNullOrBlank() }
                        ?: item.title
                    val finalFileName = if (existing != null) {
                        // 기존 레코드가 있으면 기존 파일명을 유지(복원 목적)
                        existing.fileName
                    } else {
                        makeUniqueFileName(scoresDir, desiredName)
                    }
                    val finalFile = File(scoresDir, finalFileName)

                    // overwrite 허용(파일이 없거나 손상된 경우 복원)
                    tempPdf.copyTo(finalFile, overwrite = true)

                    val id = existing?.id ?: UUID.randomUUID().toString()

                    // chosung은 기존 로직이 있으면 연결 (지금은 빈값 유지)
                    val chosung = ""

                    val entity = ScoreEntity(
                        id = id,
                        title = item.title,
                        fileName = finalFileName,
                        chosung = chosung,
                        createdAt = System.currentTimeMillis(),
                        contentHash = computedHash
                    )
                    dao.upsert(entity)

                    orderedIds += id
                    imported++
                }

                // temp 정리
                extractedByKey.values.forEach { runCatching { it.delete() } }

                val fallbackTitle = displayNameWithoutExt(context, acsetUri)
                val resolvedTitle = manifest.setlistTitle?.trim().takeUnless { it.isNullOrBlank() }
                    ?: fallbackTitle

                Result.Success(
                    setlistTitle = resolvedTitle,
                    orderedScoreIds = orderedIds,
                    importedCount = imported,
                    skippedDuplicateCount = dupSkipped
                )
            }.getOrElse { e ->
                Log.w(TAG, "가져오기 실패: ${e.message ?: e.javaClass.simpleName}", e)
                Result.Failure("가져오기 실패: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }


    private fun displayNameWithoutExt(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx < 0) return@use null
                    c.getString(idx)
                }
        }.getOrNull()
            ?.trim()
            ?.takeUnless { it.isNullOrBlank() }
            ?.removeSuffix(".acset")
            ?.removeSuffix(".zip")
    }

    private fun sanitizePdfFileName(raw: String): String {
        val trimmed = raw.trim().ifBlank { "Score.pdf" }
        val withExt = if (trimmed.endsWith(".pdf", ignoreCase = true)) trimmed else "$trimmed.pdf"
        // 파일명에 들어가면 문제되는 문자 최소 제거
        return withExt.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun makeUniqueFileName(dir: File, desired: String): String {
        val safe = sanitizePdfFileName(desired)
        val base = safe.removeSuffix(".pdf")
        var candidate = "$base.pdf"
        if (!File(dir, candidate).exists()) return candidate
        var n = 2
        while (true) {
            candidate = "$base ($n).pdf"
            if (!File(dir, candidate).exists()) return candidate
            n++
        }
    }
}