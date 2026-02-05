package com.kandc.acscore.share.crypto

import android.content.Context
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * ACSET(악보 번들) 암복호화용 "앱 공통 키" 제공자 (iOS 호환 규칙).
 *
 * 요구사항:
 * - 공유받은 사람은 우리 앱만 설치하면 바로 열려야 함
 * - Android ↔ iOS 상호 복호화 가능해야 함
 *
 * 공통 키 파생 규칙(플랫폼 공통):
 *   AES_KEY = SHA256("ACSET_KEY_V{KEY_VERSION}|{APP_SECRET}|{APP_ID}")
 *
 * - APP_ID:
 *   - Android: applicationId (context.packageName)
 *   - iOS: bundleIdentifier (Bundle.main.bundleIdentifier)
 *   → 두 플랫폼에서 문자열이 같아야 상호 복호화 가능
 */
object AppKeyProvider {

    /** 키 로테이션/포맷 변경 대비용 */
    const val KEY_VERSION: Int = 1

    /**
     * iOS와 동일하게 맞출 "앱 공통 시크릿".
     *
     * ⚠️ 완전한 DRM은 불가능하지만, 일반 사용자가 파일만으로 열람하는 것을 막는 목적엔 충분.
     * - iOS에서도 동일한 문자열을 넣어야 함
     * - 나중에 교체할 땐 KEY_VERSION 올리고 import에서 버전별 복호화 지원하면 됨
     */
    private const val APP_SECRET = "acscore_internal_secret_2026"

    /** AES-256 키 반환 */
    fun getAesKey(context: Context): SecretKey {
        val appId = context.packageName // = applicationId

        val seed = buildString {
            append("ACSET_KEY_V").append(KEY_VERSION).append('|')
            append(APP_SECRET).append('|')
            append(appId).append('|')

            // 상수 조각(노출을 조금이라도 줄이기 위한 분산)
            append(partA()).append('|')
            append(partB()).append('|')
            append(partC())
        }

        val keyBytes = sha256(seed.toByteArray(Charsets.UTF_8)) // 32 bytes
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes)
    }

    // 상수 조각들(난독화 전/후로 정적 분석을 조금이라도 어렵게)
    private fun partA(): String = listOf("AC", "SC", "OR", "E").joinToString("")
    private fun partB(): String = (1000 + 26).toString() + ":" + (5 * 2).toString()
    private fun partC(): String = charArrayOf('b', 'u', 'n', 'd', 'l', 'e').concatToString()
}