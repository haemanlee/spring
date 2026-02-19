package com.example.spring.day2.order.application

import com.example.spring.day2.order.domain.CatalogProduct
import com.example.spring.day2.order.infra.InMemoryOrderRepository
import com.example.spring.day2.order.infra.InMemoryProductCatalogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GetOrderQueryServiceTest {
    @Test
    fun `given member orders when query summaries then returns newest first with snapshot total`() {
        // given
        val productCatalogRepository = InMemoryProductCatalogRepository()
        productCatalogRepository.save(
            CatalogProduct(
                id = 1L,
                name = "Notebook",
                unitPrice = BigDecimal("12000"),
            )
        )

        val orderRepository = InMemoryOrderRepository()
        val createOrderService = CreateOrderService(productCatalogRepository, orderRepository)
        createOrderService.create(
            CreateOrderCommand(
                memberId = 100L,
                items = listOf(CreateOrderItemCommand(productId = 1L, quantity = 1)),
            )
        )
        createOrderService.create(
            CreateOrderCommand(
                memberId = 100L,
                items = listOf(CreateOrderItemCommand(productId = 1L, quantity = 2)),
            )
        )

        val getOrderQueryService = GetOrderQueryService(orderRepository)

        // when
        val summaries = getOrderQueryService.getOrderSummaries(memberId = 100L)

        // then
        assertEquals(2, summaries.size)
        assertEquals("ORD-00000002", summaries[0].orderNo)
        assertEquals(BigDecimal("24000"), summaries[0].totalAmount)
        assertEquals("ORD-00000001", summaries[1].orderNo)
        assertEquals(BigDecimal("12000"), summaries[1].totalAmount)
    }
}
