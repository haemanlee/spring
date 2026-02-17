package com.example.spring.day2.order.domain

import java.math.BigDecimal

enum class OrderStatus {
    CREATED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELED,
}

data class CatalogProduct(
    val id: Long,
    val name: String,
    val unitPrice: BigDecimal,
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
    val status: OrderStatus,
    val items: List<OrderItem>,
) {
    fun totalAmount(): BigDecimal {
        return items.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.unitPriceSnapshot.multiply(BigDecimal.valueOf(item.quantity.toLong()))
        }
    }
}
