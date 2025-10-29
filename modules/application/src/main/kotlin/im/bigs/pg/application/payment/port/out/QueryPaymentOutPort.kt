package im.bigs.pg.application.payment.port.out

import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentSummary
import java.time.Instant

// 목록 결과 데이터 구조
data class PaymentPage(
    val items: List<Payment>,
    val nextCursor: String?,
    val hasNext: Boolean
)

// 목록과 통계를 합친 최종 결과 구조
data class PaymentQueryResult(
    val page: PaymentPage,
    val summary: PaymentSummary
)

interface QueryPaymentOutPort {
    /** 필터와 커서에 맞는 결제 목록과 통계를 동시에 조회합니다. */
    fun queryPayments(filter: QueryFilter, cursorId: Long?, cursorCreatedAt: Instant?): PaymentQueryResult
}