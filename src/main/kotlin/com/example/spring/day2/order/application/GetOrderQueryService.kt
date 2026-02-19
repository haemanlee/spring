package com.example.spring.day2.order.application

import com.example.spring.day2.order.infra.OrderRepository
import java.math.BigDecimal

data class OrderSummary(
    val orderId: Long,
    val orderNo: String,
    val status: String,
    val totalAmount: BigDecimal,
)

class GetOrderQueryService(
    private val orderRepository: OrderRepository,
) {
    fun getOrderSummaries(memberId: Long): List<OrderSummary> {
        return orderRepository.findByMemberId(memberId)
            .map { order ->
                OrderSummary(
                    orderId = order.id,
                    orderNo = order.orderNo,
                    status = order.status.name,
                    totalAmount = order.totalAmount(),
                )
            }
    }
}
