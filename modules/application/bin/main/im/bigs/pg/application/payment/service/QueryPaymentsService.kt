package im.bigs.pg.application.payment.service

import im.bigs.pg.application.payment.port.`in`.*
import im.bigs.pg.application.payment.port.out.PaymentOutPort // <--- 새로 주입
import im.bigs.pg.application.payment.port.out.PaymentQuery // <--- 사용
import im.bigs.pg.application.payment.port.out.PaymentSummaryFilter // <--- 사용
import im.bigs.pg.domain.payment.PaymentSummary
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * 결제 이력 조회 유스케이스 구현체.
 * - 커서 토큰은 createdAt/id를 안전하게 인코딩해 전달/복원합니다.
 * - 통계는 조회 조건과 동일한 집합을 대상으로 계산됩니다.
 */
@Service
class QueryPaymentsService(
    private val paymentRepository: PaymentOutPort // <--- PaymentPersistenceAdapter 주입
) : QueryPaymentsUseCase {
    
    override fun query(filter: QueryFilter): QueryResult {
        
        // 1. 커서 복원 (String -> LocalDateTime + Long)
        val (cursorCreatedAt, cursorId) = decodeCursor(filter.cursor)
        
        // 2. 목록 조회를 위한 PaymentQuery 객체 생성
        val query = PaymentQuery(
            partnerId = filter.partnerId,
            status = filter.status,
            from = filter.from,
            to = filter.to,
            limit = filter.limit,
            cursorCreatedAt = cursorCreatedAt,
            cursorId = cursorId
        )
        
        // 3. 통계 조회를 위한 PaymentSummaryFilter 객체 생성 (커서 제외)
        val summaryFilter = PaymentSummaryFilter(
            partnerId = filter.partnerId,
            status = filter.status,
            from = filter.from,
            to = filter.to
        )
        
        // 4. Persistence Adapter 호출
        val page = paymentRepository.findBy(query)
        val summaryProjection = paymentRepository.summary(summaryFilter)
        
        // 5. 커서 인코딩 및 결과 변환
        val nextCursor = encodeCursor(page.nextCursorCreatedAt, page.nextCursorId)
        
        return QueryResult(
            items = page.items,
            summary = PaymentSummary(
                count = summaryProjection.count,
                totalAmount = summaryProjection.totalAmount,
                totalNetAmount = summaryProjection.totalNetAmount
            ),
            nextCursor = nextCursor,
            hasNext = page.hasNext,
        )
    }

    // 커서 인코딩 로직 (기존 코드 유지)
    /** 다음 페이지 이동을 위한 커서 인코딩. */
    private fun encodeCursor(createdAt: LocalDateTime?, id: Long?): String? {
        if (createdAt == null || id == null) return null
        // LocalDateTime을 Instant로 변환 후 Epoch Milli 사용
        val raw = "${createdAt.toInstant(ZoneOffset.UTC).toEpochMilli()}:$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    // 커서 디코딩 로직 (기존 코드 유지)
    /** 요청으로 전달된 커서 복원. 유효하지 않으면 null 커서로 간주합니다. */
    private fun decodeCursor(cursor: String?): Pair<LocalDateTime?, Long?> {
        if (cursor.isNullOrBlank()) return null to null
        return try {
            val raw = String(Base64.getUrlDecoder().decode(cursor))
            val parts = raw.split(":")
            val ts = parts[0].toLong()
            val id = parts[1].toLong()
            
            // Instant를 LocalDateTime으로 변환
            LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC) to id
        } catch (e: Exception) {
            null to null
        }
    }
}