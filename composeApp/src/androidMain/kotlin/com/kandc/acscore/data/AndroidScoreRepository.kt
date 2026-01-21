package com.kandc.acscore.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kandc.acscore.data.local.ScoreDao
import com.kandc.acscore.data.local.ScoreEntity
import com.kandc.acscore.data.model.Score
import com.kandc.acscore.data.repository.ScoreRepository
import java.io.File
import java.util.UUID
import com.kandc.acscore.util.KoreanChosung

class AndroidScoreRepository(
    private val context: Context,
    private val dao: ScoreDao,
) : ScoreRepository {

    override suspend fun loadScores(): List<Score> =
        runCatching { dao.getAll().map { it.toModel() } }
            .getOrElse { emptyList() } // 크래시 방지: DB 문제면 빈 리스트

    override suspend fun importPdf(sourceUriString: String): Result<Score> =
        runCatching {
            val uri = Uri.parse(sourceUriString)
            val cr = context.contentResolver

            // 1) 파일명 추출(안전)
            val displayName = cr.safeDisplayName(uri) ?: "score.pdf"
            val baseName = sanitizeFileName(displayName.removeSuffix(".pdf"))
            val ext = "pdf"

            // 2) 저장 폴더
            val scoresDir = File(context.filesDir, "scores").apply { mkdirs() }

            // 3) 중복 파일명 처리
            val outFile = resolveUniqueFile(scoresDir, baseName, ext)

            // 4) 복사 (InputStream null 방어)
            cr.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Cannot open input stream for uri=$uri")

            // 5) DB 저장
            val now = System.currentTimeMillis()
            val score = Score(
                id = UUID.randomUUID().toString(),
                title = baseName.ifBlank { "Untitled" },
                fileName = outFile.name,
                createdAt = now
            )

            // insert 실패시(충돌 등) 파일도 정리해주면 좋음 → 최소 방어로 처리
            runCatching {
                val chosung = KoreanChosung.extract(score.title)

                dao.insert(
                    ScoreEntity(
                        id = score.id,
                        title = score.title,
                        fileName = score.fileName,
                        chosung = chosung,
                        createdAt = score.createdAt
                    )
                )
            }.onFailure {
                runCatching { outFile.delete() }
                throw it
            }

            score
        }

    private fun ScoreEntity.toModel() = Score(
        id = id,
        title = title,
        fileName = fileName,
        createdAt = createdAt
    )

    private fun Score.toEntity() = ScoreEntity(
        id = id,
        title = title,
        fileName = fileName,
        chosung = KoreanChosung.extract(title),
        createdAt = createdAt
    )

    private fun ContentResolver.safeDisplayName(uri: Uri): String? =
        runCatching {
            query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
        }.getOrNull()

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "score" }

    private fun resolveUniqueFile(dir: File, base: String, ext: String): File {
        var candidate = File(dir, "$base.$ext")
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$i.$ext")
            i++
        }
        return candidate
    }

    override suspend fun searchScores(query: String): List<Score> =
        runCatching {
            val q = query.trim()
            if (q.isEmpty()) return@runCatching dao.getAll().map { it.toModel() }

            val safeTitle = q
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")

            // 입력을 초성으로 변환하되, 공백 유지/제거 둘 다 준비
            val chosungWithSpace = KoreanChosung.extractForQueryPreserveSpaces(q)
            val chosungNoSpace = chosungWithSpace.replace(" ", "")

            dao.searchCombinedNormalized(
                titleQuery = safeTitle,
                chosungQueryWithSpace = chosungWithSpace,
                chosungQueryNoSpace = chosungNoSpace
            ).map { it.toModel() }
        }.getOrElse { emptyList() }
}