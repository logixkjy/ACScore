package com.kandc.acscore.share

import android.content.Context
import com.kandc.acscore.share.crypto.AcsetCrypto
import com.kandc.acscore.shared.share.SetlistBundleFile
import com.kandc.acscore.shared.share.SetlistBundleItem
import com.kandc.acscore.shared.share.SetlistBundleManifest
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SetlistBundleExporter(
    private val context: Context,
    private val json: Json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    sealed class Result {
        data class Success(val acsetFile: File) : Result()
        data class Failure(val reason: String, val cause: Throwable? = null) : Result()
    }

    /**
     * 악보 포함 공유 파일(.acset) 생성
     * - 내부적으로 zip(manifest.json + scores/<hash>.pdf)을 만든 뒤,
     * - zip 전체를 AES-GCM으로 암호화해서 .acset로 출력
     *
     * @param setlistTitle 표시용 세트리스트 제목
     * @param requests 곡 목록(파일 경로 포함)
     */
    fun exportAcset(
        setlistTitle: String,
        requests: List<ViewerOpenRequest>
    ): Result {
        return runCatching {
            if (setlistTitle.isBlank()) {
                return Result.Failure("세트리스트 제목이 비어있어요.")
            }
            if (requests.isEmpty()) {
                return Result.Failure("내보낼 악보가 없어요.")
            }

            // 1) 파일 존재 확인 + 해시 계산 (순서 유지)
            val valid = requests.mapNotNullIndexed { index, req ->
                val f = File(req.filePath)
                if (!f.exists() || !f.isFile) return@mapNotNullIndexed null
                val hash = PdfHashing.sha256(f)
                Triple(index, req, hash) // (orderIndex, request, hash)
            }
            if (valid.isEmpty()) {
                return Result.Failure("유효한 PDF 파일을 찾지 못했어요.")
            }

            // 2) hash 기준 중복 제거: hash -> 대표 파일
            val hashToFile: LinkedHashMap<String, File> = linkedMapOf()
            valid.forEach { (_, req, hash) ->
                if (!hashToFile.containsKey(hash)) {
                    hashToFile[hash] = File(req.filePath)
                }
            }

            // 3) manifest 구성
            val items = valid.map { (orderIndex, req, hash) ->
                SetlistBundleItem(
                    order = orderIndex + 1,
                    title = req.title,
                    scoreId = req.scoreId,
                    fileHash = hash,
                    originalFileName = File(req.filePath).name
                )
            }

            val files = hashToFile.entries.map { (hash, file) ->
                val pure = hash.removePrefix("sha256:")
                SetlistBundleFile(
                    fileHash = hash,
                    zipPath = "scores/$pure.pdf",
                    size = file.length()
                )
            }

            val manifest = SetlistBundleManifest(
                version = 1,
                title = setlistTitle,
                createdAtEpochMs = System.currentTimeMillis(),
                items = items,
                files = files
            )

            // 4) 임시 작업 폴더
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }

            // 4-1) manifest.json 임시 파일
            val manifestFile = File(exportDir, "manifest_${System.currentTimeMillis()}.json")
            manifestFile.writeText(json.encodeToString(manifest))

            // 4-2) zip 임시 생성
            val tempZip = File(exportDir, "bundle_${System.currentTimeMillis()}.zip")
            val zipEntries = buildList {
                add(ZipBundler.FileEntry(zipPath = "manifest.json", sourceFile = manifestFile))
                hashToFile.entries.forEach { (hash, src) ->
                    val pure = hash.removePrefix("sha256:")
                    add(ZipBundler.FileEntry(zipPath = "scores/$pure.pdf", sourceFile = src))
                }
            }
            ZipBundler.createZip(tempZip, zipEntries)

            // 5) zip -> .acset 암호화
            val zipBytes = tempZip.readBytes()
            val acsetBytes = AcsetCrypto.encryptZipToAcsetBytes(context, zipBytes)

            val safeTitle = setlistTitle
                .trim()
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .take(40)
                .ifBlank { "setlist" }

            val outAcset = File(
                exportDir,
                "$safeTitle.acset"
            )
            outAcset.writeBytes(acsetBytes)

            // cleanup
            runCatching { manifestFile.delete() }
            runCatching { tempZip.delete() }

            Result.Success(outAcset)
        }.getOrElse { e ->
            Result.Failure("내보내기에 실패했어요.", e)
        }
    }
}

private inline fun <T, R : Any> List<T>.mapNotNullIndexed(
    transform: (index: Int, T) -> R?
): List<R> {
    val out = ArrayList<R>(size)
    for (i in indices) {
        val v = transform(i, this[i])
        if (v != null) out.add(v)
    }
    return out
}