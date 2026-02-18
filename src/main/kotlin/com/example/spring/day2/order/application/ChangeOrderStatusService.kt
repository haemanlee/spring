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
    private val orderAuditService: OrderAuditService? = null,
) {
    fun change(command: ChangeOrderStatusCommand): ChangeOrderStatusResult {
        val order = orderRepository.findByIdForUpdate(command.orderId)
            ?: throw IllegalArgumentException("order not found: ${command.orderId}")

        var statusUpdated = false

        try {
            if (order.status == command.targetStatus) {
                return ChangeOrderStatusResult(
                    orderId = order.id,
                    changed = false,
                    currentStatus = order.status,
                )
            }

            val changedOrder = order.copy(status = command.targetStatus)
            orderRepository.save(changedOrder)
            statusUpdated = true

            orderStatusHistoryRepository.save(
                OrderStatusHistory(
                    orderId = order.id,
                    fromStatus = order.status,
                    toStatus = command.targetStatus,
                )
            )

            runCatching {
                orderAuditService?.recordStatusChangeSuccess(
                    orderId = changedOrder.id,
                    newStatus = changedOrder.status.name,
                )
            }

            return ChangeOrderStatusResult(
                orderId = changedOrder.id,
                changed = true,
                currentStatus = changedOrder.status,
            )
        } catch (exception: Exception) {
            if (statusUpdated) {
                runCatching { orderRepository.save(order) }
            }

            runCatching {
                orderAuditService?.recordStatusChangeFailure(
                    orderId = order.id,
                    reason = "status change failed: ${exception.message}",
                )
            }

            throw exception
        } finally {
            if (orderRepository is InMemoryOrderRepository) {
                orderRepository.unlock(command.orderId)
            }
        }
    }
}
