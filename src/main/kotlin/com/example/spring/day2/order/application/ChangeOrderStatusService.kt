package com.example.spring.day2.order.application

import com.example.spring.day2.order.domain.OrderStatus
import com.example.spring.day2.order.domain.OrderStatusHistory
import com.example.spring.day2.order.infra.InMemoryOrderRepository
import com.example.spring.day2.order.infra.OrderRepository
import com.example.spring.day2.order.infra.OrderStatusHistoryRepository

data class ChangeOrderStatusCommand(
    val orderId: Long,
    val targetStatus: OrderStatus,
)

data class ChangeOrderStatusResult(
    val orderId: Long,
    val changed: Boolean,
    val currentStatus: OrderStatus,
)

class ChangeOrderStatusService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
) {
    fun change(command: ChangeOrderStatusCommand): ChangeOrderStatusResult {
        val order = orderRepository.findByIdForUpdate(command.orderId)
            ?: throw IllegalArgumentException("order not found: ${command.orderId}")

        try {
            // 동일 상태 재요청은 멱등하게 no-op 처리한다.
            if (order.status == command.targetStatus) {
                return ChangeOrderStatusResult(
                    orderId = order.id,
                    changed = false,
                    currentStatus = order.status,
                )
            }

            val changedOrder = order.copy(status = command.targetStatus)
            orderRepository.save(changedOrder)

            orderStatusHistoryRepository.save(
                OrderStatusHistory(
                    orderId = order.id,
                    fromStatus = order.status,
                    toStatus = command.targetStatus,
                )
            )

            return ChangeOrderStatusResult(
                orderId = changedOrder.id,
                changed = true,
                currentStatus = changedOrder.status,
            )
        } finally {
            // In-memory 잠금 구현의 해제를 위해 실제 구현체를 사용할 때만 unlock을 수행한다.
            if (orderRepository is InMemoryOrderRepository) {
                orderRepository.unlock(command.orderId)
            }
        }
    }
}
