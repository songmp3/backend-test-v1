package im.bigs.pg.application.payment.service

import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.payment.port.out.PaymentPage
import im.bigs.pg.application.payment.port.out.PaymentQuery
import im.bigs.pg.application.payment.port.out.PaymentSummaryFilter
import im.bigs.pg.application.payment.port.out.PaymentSummaryProjection
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.domain.payment.PaymentSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QueryPaymentsServiceTest {

    private val paymentRepo = mockk<PaymentOutPort>(relaxed = true)
    private val service = QueryPaymentsService(paymentRepo)

    // 테스트용 기본 결제 도메인 객체
    private val payment1 = Payment(
        id = 10L, partnerId = 1L, amount = BigDecimal("10000"), cardBin = "4000", cardLast4 = "1234",
        status = PaymentStatus.APPROVED, approvedAt = LocalDateTime.of(2025, 1, 1, 10, 0),
        createdAt = LocalDateTime.of(2025, 1, 1, 10, 0)
    )
    private val payment2 = payment1.copy(id = 9L, createdAt = LocalDateTime.of(2025, 1, 1, 9, 0))

    // 수동 커서 인코딩 함수 (테스트 검증용)
    private fun manualEncode(createdAt: LocalDateTime, id: Long): String {
        val instant = createdAt.toInstant(ZoneOffset.UTC)
        val raw = "${instant.toEpochMilli()}:$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    @BeforeEach
    fun setup() {
        // 모든 테스트에 적용될 기본 통계 Mockk 설정
        every { paymentRepo.summary(any()) } returns PaymentSummaryProjection(
            count = 50L,
            totalAmount = BigDecimal("500000"),
            totalNet = BigDecimal("480000")
        )
    }

    @Test
    @DisplayName("최초 조회 시 필터를 정확히 전달하고 통계 결과를 반환해야 한다")
    fun `initial_query_sends_filter_and_returns_summary`() {
        // Given: 필터 설정 (커서 없음)
        val filter = QueryFilter(
            partnerId = 1L,
            status = PaymentStatus.APPROVED,
            limit = 2,
            from = LocalDateTime.of(2025, 1, 1, 0, 0)
        )
        
        // When: PaymentOutPort.findBy 호출 시 다음 페이지가 없다고 가정 (2개 요청 -> 2개 반환)
        every { paymentRepo.findBy(any()) } returns PaymentPage(
            items = listOf(payment1, payment2),
            hasNext = false, // 다음 페이지 없음
            nextCursorCreatedAt = null,
            nextCursorId = null
        )

        // When: 서비스 호출
        val result = service.query(filter)

        // Then: 
        // 1. 목록 확인
        assertEquals(2, result.items.size)
        // 2. 통계 확인 (Mocked 값)
        assertEquals(50L, result.summary.count)
        assertEquals(BigDecimal("500000"), result.summary.totalAmount)
        // 3. 페이지네이션 확인
        assertFalse(result.hasNext)
        assertEquals(null, result.nextCursor)
        
        // 4. Persistence Adapter 호출 검증 (커서 없음)
        verify(exactly = 1) { paymentRepo.findBy(match<PaymentQuery> { 
            it.cursorId == null && it.cursorCreatedAt == null && it.partnerId == 1L
        }) }
        verify(exactly = 1) { paymentRepo.summary(match<PaymentSummaryFilter> {
            it.partnerId == 1L && it.status == PaymentStatus.APPROVED
        }) }
    }
    
    @Test
    @DisplayName("다음 페이지가 있을 경우 nextCursor를 정확히 인코딩하여 반환해야 한다")
    fun `when_has_next_page_should_return_encoded_cursor`() {
        // Given: 필터 설정
        val filter = QueryFilter(limit = 1)
        
        // When: PaymentOutPort.findBy 호출 시 다음 페이지가 있다고 가정 (1개 요청 -> 1개 반환, hasNext = true)
        every { paymentRepo.findBy(any()) } returns PaymentPage(
            items = listOf(payment1),
            hasNext = true, // 다음 페이지 있음
            nextCursorCreatedAt = payment2.createdAt, // 다음 커서 위치
            nextCursorId = payment2.id
        )

        // When: 서비스 호출
        val result = service.query(filter)

        // Then: 
        // 1. 페이지네이션 확인
        assertTrue(result.hasNext)
        assertNotNull(result.nextCursor)
        
        // 2. 커서 값 검증 (서비스의 인코딩 로직과 일치하는지)
        val expectedCursor = manualEncode(payment2.createdAt, payment2.id)
        assertEquals(expectedCursor, result.nextCursor)
    }
    
    @Test
    @DisplayName("커서 토큰을 입력받으면 정확히 디코딩하여 Persistence Adapter에 전달해야 한다")
    fun `query_with_cursor_should_decode_and_pass_to_adapter`() {
        // Given: 커서 생성 (payment2의 위치로 가리키는 커서)
        val testCursor = manualEncode(payment2.createdAt, payment2.id)
        val filter = QueryFilter(limit = 1, cursor = testCursor)
        
        // Mock 설정 (응답은 중요하지 않음)
        every { paymentRepo.findBy(any()) } returns PaymentPage(
            items = listOf(payment1), hasNext = false, nextCursorCreatedAt = null, nextCursorId = null
        )

        // When: 서비스 호출
        service.query(filter)

        // Then: 디코딩된 커서 값이 Persistence Adapter에 정확히 전달되었는지 검증
        verify(exactly = 1) { paymentRepo.findBy(match<PaymentQuery> { 
            it.cursorId == payment2.id && it.cursorCreatedAt == payment2.createdAt
        }) }
    }
}