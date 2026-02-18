package com.example.spring.day2.order.application

import com.example.spring.day2.order.domain.OrderAuditLog
import com.example.spring.day2.order.domain.OrderAuditType
import com.example.spring.day2.order.infra.OrderAuditLogRepository

class OrderAuditService(
    private val orderAuditLogRepository: OrderAuditLogRepository,
) {
    /**
     * 실서비스에서는 @Transactional(propagation = REQUIRES_NEW) 경계가 들어갈 자리다.
     * 학습용 인메모리 구현에서는 저장 책임만 분리해 독립 커밋 개념을 표현한다.
     */
    fun recordStatusChangeSuccess(orderId: Long, newStatus: String) {
        orderAuditLogRepository.save(
            OrderAuditLog(
                orderId = orderId,
                type = OrderAuditType.STATUS_CHANGE_SUCCEEDED,
                message = "status changed to $newStatus",
            )
        )
    }

    /**
     * 실서비스에서는 @Transactional(propagation = REQUIRES_NEW) 경계를 적용해,
     * 메인 트랜잭션 실패와 관계없이 감사 로그를 남긴다.
     */
    fun recordStatusChangeFailure(orderId: Long, reason: String) {
        orderAuditLogRepository.save(
            OrderAuditLog(
                orderId = orderId,
                type = OrderAuditType.STATUS_CHANGE_FAILED,
                message = reason,
            )
        )
    }
}
