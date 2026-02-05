package com.kandc.acscore.share.crypto

import android.content.Context
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * .acset 파일 포맷 (binary):
 *
 * [MAGIC 6 bytes] = "ACSET1"
 * [keyVersion 1 byte] = AppKeyProvider.KEY_VERSION (0~255)
 * [ivLen 1 byte] = 12 (권장)
 * [iv ivLen bytes]
 * [ciphertext ...] = AES/GCM/NoPadding 결과(암호문 + GCM tag 포함)
 *
 * - plaintext: zip bytes
 * - key: AppKeyProvider.getAesKey(context) (AES-256)
 */
object AcsetCrypto {

    private val MAGIC = byteArrayOf(
        'A'.code.toByte(),
        'C'.code.toByte(),
        'S'.code.toByte(),
        'E'.code.toByte(),
        'T'.code.toByte(),
        '1'.code.toByte()
    )

    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12 // 96-bit, GCM 권장
    private val rng = SecureRandom()

    class AcsetFormatException(message: String) : IllegalArgumentException(message)

    // ------------------------------------------------------------
    // ✅ Public API (Exporter/Importer에서 쓰기 좋은 이름)
    // ------------------------------------------------------------

    /** Exporter용: zipBytes -> acsetBytes */
    fun encryptZipToAcsetBytes(context: Context, zipBytes: ByteArray): ByteArray {
        val key = AppKeyProvider.getAesKey(context)
        return encryptZipToAcsetBytes(key, AppKeyProvider.KEY_VERSION, zipBytes)
    }

    /** Importer용: acsetBytes -> zipBytes */
    fun decryptAcsetToZipBytes(context: Context, acsetBytes: ByteArray): ByteArray {
        // 기존 함수 alias
        return decryptAcsetBytesToZip(context, acsetBytes)
    }

    // ------------------------------------------------------------
    // ✅ Core implementation
    // ------------------------------------------------------------

    fun encryptZipToAcsetBytes(key: SecretKey, keyVersion: Int, zipBytes: ByteArray): ByteArray {
        require(keyVersion in 0..255) { "keyVersion must be 0..255" }

        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }

        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val ciphertext = cipher.doFinal(zipBytes)

        // header + ciphertext
        val totalLen = MAGIC.size + 1 + 1 + iv.size + ciphertext.size
        val out = ByteBuffer.allocate(totalLen)
        out.put(MAGIC)
        out.put(keyVersion.toByte())
        out.put(iv.size.toByte())
        out.put(iv)
        out.put(ciphertext)
        return out.array()
    }

    /**
     * (기존 이름 유지) .acset bytes를 복호화하여 zipBytes(plaintext)를 얻는다.
     */
    fun decryptAcsetBytesToZip(context: Context, acsetBytes: ByteArray): ByteArray {
        val key = AppKeyProvider.getAesKey(context)
        return decryptAcsetBytesToZip(
            key = key,
            expectedKeyVersion = AppKeyProvider.KEY_VERSION,
            acsetBytes = acsetBytes
        )
    }

    fun decryptAcsetBytesToZip(
        key: SecretKey,
        expectedKeyVersion: Int,
        acsetBytes: ByteArray
    ): ByteArray {
        if (acsetBytes.size < MAGIC.size + 1 + 1 + IV_LEN + 16) {
            // 최소: magic + ver + ivLen + iv + tag(16)
            throw AcsetFormatException("ACSET file too small")
        }

        val buf = ByteBuffer.wrap(acsetBytes)

        val magic = ByteArray(MAGIC.size)
        buf.get(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw AcsetFormatException("Invalid ACSET magic")
        }

        val keyVersion = (buf.get().toInt() and 0xFF)
        if (keyVersion != expectedKeyVersion) {
            throw AcsetFormatException("Unsupported keyVersion=$keyVersion (expected=$expectedKeyVersion)")
        }

        val ivLen = (buf.get().toInt() and 0xFF)
        if (ivLen <= 0 || ivLen > 32) {
            throw AcsetFormatException("Invalid ivLen=$ivLen")
        }
        if (buf.remaining() < ivLen + 16) {
            throw AcsetFormatException("ACSET truncated (ivLen=$ivLen)")
        }

        val iv = ByteArray(ivLen)
        buf.get(iv)

        val ciphertext = ByteArray(buf.remaining())
        buf.get(ciphertext)

        val cipher = Cipher.getInstance(AES_MODE)
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        // GCM 인증 실패 시 AEADBadTagException 발생 (상위에서 처리)
        return cipher.doFinal(ciphertext)
    }

    fun isAcsetBytes(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size) return false
        for (i in MAGIC.indices) {
            if (bytes[i] != MAGIC[i]) return false
        }
        return true
    }
}