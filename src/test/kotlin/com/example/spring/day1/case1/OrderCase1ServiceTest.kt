package com.example.spring.day1.case1

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderCase1ServiceTest {

    @Test
    fun `사례1 - 상품 가격이 변경되어도 주문 스냅샷 가격은 유지된다`() {
        val catalog = ProductCatalog()
        catalog.save(
            Product(
                id = 1L,
                name = "텀블러",
                unitPrice = BigDecimal("10000"),
            )
        )

        val service = OrderCase1Service(catalog)

        val order = service.createOrder(memberId = 101L, items = listOf(1L to 2))

        catalog.updatePrice(productId = 1L, newPrice = BigDecimal("12000"))

        assertEquals(BigDecimal("10000"), order.items.first().unitPriceSnapshot)
        assertEquals(BigDecimal("20000"), service.calculateTotalAmount(order))
        assertEquals("ORD-00000001", order.orderNo)
        assertEquals(OrderStatus.CREATED, order.status)
    }
}
