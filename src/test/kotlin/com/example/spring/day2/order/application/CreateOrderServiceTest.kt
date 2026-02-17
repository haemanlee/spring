package com.example.spring.day2.order.application

import com.example.spring.day2.order.domain.CatalogProduct
import com.example.spring.day2.order.infra.InMemoryOrderRepository
import com.example.spring.day2.order.infra.InMemoryProductCatalogRepository
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateOrderServiceTest {

    @Test
    fun `2-1 주문 생성 시 상품 스냅샷 기준으로 총액을 계산한다`() {
        val catalogRepository = InMemoryProductCatalogRepository()
        catalogRepository.save(CatalogProduct(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))
        catalogRepository.save(CatalogProduct(id = 2L, name = "컵받침", unitPrice = BigDecimal("2000")))

        val orderRepository = InMemoryOrderRepository()
        val service = CreateOrderService(catalogRepository, orderRepository)

        val result = service.create(
            CreateOrderCommand(
                memberId = 101L,
                items = listOf(
                    CreateOrderItemCommand(productId = 1L, quantity = 2),
                    CreateOrderItemCommand(productId = 2L, quantity = 3),
                ),
            )
        )

        assertEquals("ORD-00000001", result.orderNo)
        assertEquals(BigDecimal("26000"), result.totalAmount)
    }

    @Test
    fun `2-1 카탈로그 가격이 변경되어도 주문 총액은 스냅샷 기준으로 유지된다`() {
        val catalogRepository = InMemoryProductCatalogRepository()
        catalogRepository.save(CatalogProduct(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))

        val orderRepository = InMemoryOrderRepository()
        val service = CreateOrderService(catalogRepository, orderRepository)

        val result = service.create(
            CreateOrderCommand(
                memberId = 101L,
                items = listOf(CreateOrderItemCommand(productId = 1L, quantity = 2)),
            )
        )

        catalogRepository.updatePrice(productId = 1L, newPrice = BigDecimal("12000"))

        val savedOrder = requireNotNull(orderRepository.findById(result.orderId))
        assertEquals(BigDecimal("20000"), savedOrder.totalAmount())
        assertEquals(BigDecimal("10000"), savedOrder.items.first().unitPriceSnapshot)
    }

    @Test
    fun `2-1 주문 항목이 비어있으면 주문을 생성할 수 없다`() {
        val service = CreateOrderService(InMemoryProductCatalogRepository(), InMemoryOrderRepository())

        assertFailsWith<IllegalArgumentException> {
            service.create(CreateOrderCommand(memberId = 101L, items = emptyList()))
        }
    }
}
