package im.bigs.pg.external.pg

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

// 경고: 실제 운영 환경에서는 키/IV/AAD 관리가 더 복잡하며, DI를 통해 주입받아야 합니다.
// 여기서는 과제 수행을 위해 하드코드합니다.
object AesEncryptor {
    // 과제 요구사항에 따른 하드코드된 비밀 키 (32바이트)
    private const val ENCRYPTION_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg012345" // 32 chars
    
    // 과제 요구사항에 따른 하드코드된 초기화 벡터 (12바이트)
    private const val IV = "123456789012" // 12 chars
    
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128

    // 키와 IV를 Byte 배열로 준비
    private val keySpec = SecretKeySpec(ENCRYPTION_KEY.toByteArray(Charsets.UTF_8), "AES")
    private val ivBytes = IV.toByteArray(Charsets.UTF_8)

    fun encrypt(data: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, ivBytes)
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            
            // 암호화된 데이터 + GCM Tag를 Base64 인코딩하여 반환
            Base64.getEncoder().encodeToString(encryptedBytes)
            
        } catch (e: Exception) {
            throw RuntimeException("AES-GCM Encryption failed", e)
        }
    }
}