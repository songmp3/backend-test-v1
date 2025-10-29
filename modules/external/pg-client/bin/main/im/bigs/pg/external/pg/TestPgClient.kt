package im.bigs.pg.external.pg

import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.PaymentStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate // HTTP 클라이언트 사용
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

// TestPg API 응답 DTO
data class TestPgApproveResponse(
    val approvalCode: String,
    val approvedAt: String, // ISO 8601 형식 문자열을 수신한다고 가정
    val status: String
)

// TestPg API 요청 DTO
data class TestPgApproveRequestDto(
    val partnerId: Long,
    val card_number: String, // 암호화된 카드번호
    val amount: String,      // 암호화된 금액
    val product_name: String
)

@Component
class TestPgClient(
    private val restTemplate: RestTemplate,
    // application.yml 등에 설정된 PG URL을 주입받습니다.
    @Value("\${pg.test.url:http://localhost:8081}") private val testPgBaseUrl: String 
) : PgClientOutPort {
    
    // 이 클라이언트가 어떤 Partner ID를 지원하는지 정의 (기존 Mock 코드 재사용)
    override fun supports(partnerId: Long): Boolean = partnerId % 2L == 1L

    override fun approve(request: PgApproveRequest): PgApproveResult {
        
        // 1. 암호화
        // 카드번호는 cardBin + cardLast4로 구성합니다.
        val fullCardNumber = request.cardBin + request.cardLast4 
        val encryptedCardNumber = AesEncryptor.encrypt(fullCardNumber)
        val encryptedAmount = AesEncryptor.encrypt(request.amount.toPlainString())
        
        // 2. 요청 객체 생성
        val requestDto = TestPgApproveRequestDto(
            partnerId = request.partnerId,
            card_number = encryptedCardNumber,
            amount = encryptedAmount,
            product_name = request.productName ?: "Unknown Product"
        )
        
        // 3. HTTP 요청 설정
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(requestDto, headers)
        
        // 4. Rest API 호출 (URL은 API 문서 참고)
        val url = "$testPgBaseUrl/api/v1/pay/credit-card" 
        
        try {
            val response = restTemplate.postForEntity(url, entity, TestPgApproveResponse::class.java)
            val body = response.body 
                ?: throw IllegalStateException("PG Approval failed: Empty response body")

            // 5. 응답 결과 처리 및 변환
            val approvedStatus = when(body.status.uppercase()) {
                "SUCCESS" -> PaymentStatus.APPROVED
                else -> PaymentStatus.FAILED 
            }
            
            // ISO 8601 형식 문자열을 LocalDateTime으로 변환
            val approvedTime = LocalDateTime.parse(body.approvedAt) 

            return PgApproveResult(
                approvalCode = body.approvalCode,
                approvedAt = approvedTime,
                status = approvedStatus
            )
        } catch (e: Exception) {
            // 네트워크 오류, 타임아웃, 4xx/5xx 응답 등 모든 예외 처리
            throw RuntimeException("PG API call failed for partner ${request.partnerId}", e)
        }
    }
}