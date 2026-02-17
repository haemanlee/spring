package com.example.spring.day2.order.application

import com.example.spring.day2.order.domain.Order
import com.example.spring.day2.order.domain.OrderItem
import com.example.spring.day2.order.domain.OrderStatus
import com.example.spring.day2.order.infra.OrderRepository
import com.example.spring.day2.order.infra.ProductCatalogRepository
import java.math.BigDecimal

data class CreateOrderCommand(
    val memberId: Long,
    val items: List<CreateOrderItemCommand>,
)

data class CreateOrderItemCommand(
    val productId: Long,
    val quantity: Int,
)

data class CreateOrderResult(
    val orderId: Long,
    val orderNo: String,
    val totalAmount: BigDecimal,
)

class CreateOrderService(
    private val productCatalogRepository: ProductCatalogRepository,
    private val orderRepository: OrderRepository,
) {
    fun create(command: CreateOrderCommand): CreateOrderResult {
        require(command.items.isNotEmpty()) { "order items must not be empty" }

        val snapshots = command.items.map { item ->
            require(item.quantity > 0) { "quantity must be positive: ${item.quantity}" }
            val product = productCatalogRepository.findById(item.productId)
            OrderItem(
                productId = product.id,
                productNameSnapshot = product.name,
                unitPriceSnapshot = product.unitPrice,
                quantity = item.quantity,
            )
        }

        val orderId = orderRepository.nextId()
        val order = Order(
            id = orderId,
            orderNo = "ORD-${orderId.toString().padStart(8, '0')}",
            memberId = command.memberId,
            status = OrderStatus.CREATED,
            items = snapshots,
        )

        orderRepository.save(order)

        return CreateOrderResult(
            orderId = order.id,
            orderNo = order.orderNo,
            totalAmount = order.totalAmount(),
        )
    }
}
