package com.example.spring.day1.case1

import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

enum class OrderStatus {
    CREATED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELED,
}

data class Product(
    val id: Long,
    var name: String,
    var unitPrice: BigDecimal,
)

data class OrderItem(
    val productId: Long,
    val productNameSnapshot: String,
    val unitPriceSnapshot: BigDecimal,
    val quantity: Int,
)

data class Order(
    val id: Long,
    val orderNo: String,
    val memberId: Long,
    var status: OrderStatus = OrderStatus.CREATED,
    val items: List<OrderItem>,
)

data class OrderStatusHistory(
    val orderId: Long,
    val fromStatus: OrderStatus,
    val toStatus: OrderStatus,
    val changedBy: Long?,
    val createdAt: Instant = Instant.now(),
)

class ProductCatalog {
    private val products = linkedMapOf<Long, Product>()

    fun save(product: Product) {
        products[product.id] = product
    }

    fun updatePrice(productId: Long, newPrice: BigDecimal) {
        val product = products[productId] ?: throw IllegalArgumentException("product not found: $productId")
        products[productId] = product.copy(unitPrice = newPrice)
    }

    fun findById(productId: Long): Product {
        return products[productId] ?: throw IllegalArgumentException("product not found: $productId")
    }
}

class OrderCase1Service(
    private val productCatalog: ProductCatalog,
) {
    private val orderSequence = AtomicLong(0)
    private val orders = linkedMapOf<Long, Order>()
    private val statusHistories = mutableListOf<OrderStatusHistory>()

    fun createOrder(memberId: Long, items: List<Pair<Long, Int>>): Order {
        val orderItems = items.map { (productId, quantity) ->
            val product = productCatalog.findById(productId)
            OrderItem(
                productId = product.id,
                productNameSnapshot = product.name,
                unitPriceSnapshot = product.unitPrice,
                quantity = quantity,
            )
        }

        val nextOrderSequence = orderSequence.incrementAndGet()
        val order = Order(
            id = nextOrderSequence,
            orderNo = "ORD-${nextOrderSequence.toString().padStart(8, '0')}",
            memberId = memberId,
            status = OrderStatus.CREATED,
            items = orderItems,
        )
        orders[order.id] = order
        return order
    }

    fun calculateTotalAmount(order: Order): BigDecimal {
        return order.items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.unitPriceSnapshot.multiply(BigDecimal.valueOf(item.quantity.toLong()))
        }
    }

    fun changeStatus(orderId: Long, newStatus: OrderStatus, actorId: Long?): Boolean {
        val order = orders[orderId] ?: throw IllegalArgumentException("order not found: $orderId")
        val oldStatus = order.status

        if (oldStatus == newStatus) {
            return false
        }

        order.status = newStatus
        statusHistories += OrderStatusHistory(
            orderId = order.id,
            fromStatus = oldStatus,
            toStatus = newStatus,
            changedBy = actorId,
        )
        return true
    }

    fun findOrder(orderId: Long): Order {
        return orders[orderId] ?: throw IllegalArgumentException("order not found: $orderId")
    }

    fun findStatusHistories(orderId: Long): List<OrderStatusHistory> {
        return statusHistories
            .filter { it.orderId == orderId }
            .sortedBy { it.createdAt }
    }
}
