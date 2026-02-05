package com.kandc.acscore.share

import android.content.Context
import android.net.Uri
import com.kandc.acscore.data.local.DbProvider
import com.kandc.acscore.data.local.ScoreEntity
import com.kandc.acscore.share.crypto.AcsetCrypto
import com.kandc.acscore.shared.share.AcsetManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class AcsetImporter(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

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
     * 4) contentHash로 중복 체크 후 filesDir/scores/에 저장 + DB upsert
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

                // zip 안의 pdf entry들을 임시로 저장해두기 (manifest를 먼저 읽어야 순서대로 처리 가능)
                val extracted = mutableMapOf<String, File>() // key=contentHash, value=tempPdfFile

                // 1) zip bytes 펼치기
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
                                    // scores/<hash>.pdf
                                    val hash = name.removePrefix("scores/").removeSuffix(".pdf")
                                    val tmp = File.createTempFile("acset_", ".pdf", context.cacheDir)
                                    FileOutputStream(tmp).use { out -> zis.copyTo(out) }
                                    extracted[hash] = tmp
                                }
                            }
                        }

                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                val manifestRaw = manifestJson ?: return@withContext Result.Failure("manifest.json이 없어요.")
                val manifest = json.decodeFromString(AcsetManifest.serializer(), manifestRaw)

                // 2) 순서대로 import
                val orderedIds = mutableListOf<String>()
                var imported = 0
                var dupSkipped = 0

                for (item in manifest.items) {
                    val hash = item.contentHash
                    val tempPdf = extracted[hash]
                        ?: return@withContext Result.Failure("악보가 누락됐어요: $hash")

                    // ✅ contentHash로 중복 확인
                    val existing = dao.findByContentHash(hash)
                    if (existing != null) {
                        val existingFile = File(scoresDir, existing.fileName)
                        if (existingFile.exists()) {
                            // 이미 있음 → 그대로 재사용
                            orderedIds += existing.id
                            dupSkipped++
                            continue
                        }
                        // DB는 있는데 파일이 없으면 복원해주자
                    }

                    // ✅ 저장 파일명 정책:
                    // - 같은 contentHash면 같은 파일로 수렴시키기 위해 "hash.pdf"로 고정
                    val finalFileName = "$hash.pdf"
                    val finalFile = File(scoresDir, finalFileName)

                    // overwrite 허용(파일이 없거나 손상된 경우 복원)
                    tempPdf.copyTo(finalFile, overwrite = true)

                    val id = existing?.id ?: UUID.randomUUID().toString()

                    // chosung은 기존 로직이 있으면 연결
                    val chosung = ""

                    val entity = ScoreEntity(
                        id = id,
                        title = item.title,
                        fileName = finalFileName,
                        chosung = chosung,
                        createdAt = System.currentTimeMillis(),
                        contentHash = hash
                    )

                    dao.upsert(entity)

                    orderedIds += id
                    imported++
                }

                // temp 정리
                extracted.values.forEach { runCatching { it.delete() } }

                Result.Success(
                    setlistTitle = manifest.setlistTitle,
                    orderedScoreIds = orderedIds,
                    importedCount = imported,
                    skippedDuplicateCount = dupSkipped
                )
            }.getOrElse { e ->
                Result.Failure("가져오기 실패: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }
}