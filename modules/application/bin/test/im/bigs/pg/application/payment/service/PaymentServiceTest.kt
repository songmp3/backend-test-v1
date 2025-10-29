package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.partner.Partner
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class 결제서비스Test {
    
    private val partnerRepo = mockk<PartnerOutPort>()
    private val feeRepo = mockk<FeePolicyOutPort>()
    private val paymentRepo = mockk<PaymentOutPort>()
    private val mockPgClient = mockk<PgClientOutPort>(relaxed = true) // PgClient를 Mockk로 대체

    // PgClient 목록을 주입받도록 변경
    private val service = PaymentService(partnerRepo, feeRepo, paymentRepo, listOf(mockPgClient))

    private val partner1 = Partner(1L, "TEST", "Test", true)
    private val paymentCommand = PaymentCommand(partnerId = 1L, amount = BigDecimal("10000"), cardLast4 = "4242", cardBin = "4000")

    @Nested
    @DisplayName("결제 요청 시")
    inner class PayRequest {

        @Test
        @DisplayName("Partner가 존재하지 않으면 예외가 발생해야 한다")
        fun `partner_not_found_throws_exception`() {
            every { partnerRepo.findById(any()) } returns null
            assertFailsWith<IllegalStateException> {
                service.pay(paymentCommand)
            }
        }

        @Test
        @DisplayName("유효하지 않은 Partner는 예외가 발생해야 한다")
        fun `inactive_partner_throws_exception`() {
            every { partnerRepo.findById(1L) } returns partner1.copy(active = false)
            assertFailsWith<IllegalStateException> {
                service.pay(paymentCommand)
            }
        }
    }


    @Nested
    @DisplayName("성공적인 결제 시나리오")
    inner class SuccessfulPayment {

        // PaymentOutPort의 save 메서드를 캡처하여 Payment 객체의 id를 설정하고 반환
        private val savedSlot = slot<Payment>()
        private fun setupSaveMock(resultId: Long) {
            every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = resultId) }
        }

        @Test
        @DisplayName("수수료 정책 (3% + 100원)을 적용하고 DB에 저장해야 한다")
        fun `apply_3_percent_plus_100_fee_and_save`() {
            // Given: 3% + 100원 수수료 정책
            val policy3p100 = FeePolicy(
                id = 1L, partnerId = 1L, effectiveFrom = LocalDateTime.now().minusDays(1),
                percentage = BigDecimal("0.0300"), fixedFee = BigDecimal("100")
            )
            val expectedFee = BigDecimal("10000").multiply(policy3p100.percentage)
                .setScale(0, RoundingMode.HALF_UP) // 300원
                .add(policy3p100.fixedFee) // + 100원 = 400원

            // Mock 설정
            every { partnerRepo.findById(1L) } returns partner1
            every { feeRepo.findEffectivePolicy(1L, any()) } returns policy3p100
            
            // PgClient가 성공 응답을 반환하도록 설정
            every { mockPgClient.supports(any()) } returns true
            every { mockPgClient.approve(any()) } returns PgApproveResult(
                "PG-OK-1", LocalDateTime.now(), PaymentStatus.APPROVED
            )
            setupSaveMock(99L)

            // When: 결제 실행
            val res = service.pay(paymentCommand)

            // Then: 수수료와 순이익이 정확히 계산되었는지 검증
            assertEquals(99L, res.id)
            assertEquals(expectedFee, res.feeAmount) // 400원
            assertEquals(paymentCommand.amount.subtract(expectedFee), res.netAmount) // 9600원
            assertEquals(PaymentStatus.APPROVED, res.status)
            
            // PgClient가 호출되었는지 검증 (TestPgClient의 책임)
            verify(exactly = 1) { mockPgClient.approve(any()) }
        }

        @Test
        @DisplayName("수수료 정책 (5% + 50원)을 적용하고 DB에 저장해야 한다")
        fun `apply_5_percent_plus_50_fee_and_save`() {
            // Given: 5% + 50원 수수료 정책
            val policy5p50 = FeePolicy(
                id = 2L, partnerId = 1L, effectiveFrom = LocalDateTime.now().minusDays(1),
                percentage = BigDecimal("0.0500"), fixedFee = BigDecimal("50")
            )
            val expectedFee = BigDecimal("10000").multiply(policy5p50.percentage)
                .setScale(0, RoundingMode.HALF_UP) // 500원
                .add(policy5p50.fixedFee) // + 50원 = 550원

            // Mock 설정
            every { partnerRepo.findById(1L) } returns partner1
            every { feeRepo.findEffectivePolicy(1L, any()) } returns policy5p50
            
            // PgClient가 성공 응답을 반환하도록 설정
            every { mockPgClient.supports(any()) } returns true
            every { mockPgClient.approve(any()) } returns PgApproveResult(
                "PG-OK-2", LocalDateTime.now(), PaymentStatus.APPROVED
            )
            setupSaveMock(100L)

            // When: 결제 실행
            val res = service.pay(paymentCommand)

            // Then: 수수료와 순이익이 정확히 계산되었는지 검증
            assertEquals(100L, res.id)
            assertEquals(expectedFee, res.feeAmount) // 550원
            assertEquals(paymentCommand.amount.subtract(expectedFee), res.netAmount) // 9450원
        }
    }
}