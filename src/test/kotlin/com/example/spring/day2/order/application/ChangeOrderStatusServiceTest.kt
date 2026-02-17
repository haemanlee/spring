package com.example.spring.day2.order.application

import com.example.spring.day2.order.domain.CatalogProduct
import com.example.spring.day2.order.domain.OrderStatus
import com.example.spring.day2.order.infra.InMemoryOrderRepository
import com.example.spring.day2.order.infra.InMemoryOrderStatusHistoryRepository
import com.example.spring.day2.order.infra.InMemoryProductCatalogRepository
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangeOrderStatusServiceTest {

    @Test
    fun `2-2 CREATED에서 PAID SHIPPED로 변경하면 이력이 누적된다`() {
        val catalogRepository = InMemoryProductCatalogRepository().apply {
            save(CatalogProduct(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))
        }
        val orderRepository = InMemoryOrderRepository()
        val createOrderService = CreateOrderService(catalogRepository, orderRepository)
        val historyRepository = InMemoryOrderStatusHistoryRepository()
        val service = ChangeOrderStatusService(orderRepository, historyRepository)

        val order = createOrderService.create(
            CreateOrderCommand(
                memberId = 101L,
                items = listOf(CreateOrderItemCommand(productId = 1L, quantity = 1)),
            )
        )

        val paid = service.change(ChangeOrderStatusCommand(orderId = order.orderId, targetStatus = OrderStatus.PAID))
        val shipped = service.change(ChangeOrderStatusCommand(orderId = order.orderId, targetStatus = OrderStatus.SHIPPED))

        assertTrue(paid.changed)
        assertTrue(shipped.changed)
        assertEquals(OrderStatus.SHIPPED, shipped.currentStatus)

        val histories = historyRepository.findByOrderId(order.orderId)
        assertEquals(2, histories.size)
        assertEquals(OrderStatus.CREATED, histories[0].fromStatus)
        assertEquals(OrderStatus.PAID, histories[0].toStatus)
        assertEquals(OrderStatus.PAID, histories[1].fromStatus)
        assertEquals(OrderStatus.SHIPPED, histories[1].toStatus)
    }

    @Test
    fun `2-2 PAID 상태에 다시 PAID를 요청하면 no-op으로 처리되고 이력이 생성되지 않는다`() {
        val catalogRepository = InMemoryProductCatalogRepository().apply {
            save(CatalogProduct(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))
        }
        val orderRepository = InMemoryOrderRepository()
        val createOrderService = CreateOrderService(catalogRepository, orderRepository)
        val historyRepository = InMemoryOrderStatusHistoryRepository()
        val service = ChangeOrderStatusService(orderRepository, historyRepository)

        val order = createOrderService.create(
            CreateOrderCommand(
                memberId = 101L,
                items = listOf(CreateOrderItemCommand(productId = 1L, quantity = 1)),
            )
        )

        service.change(ChangeOrderStatusCommand(orderId = order.orderId, targetStatus = OrderStatus.PAID))
        val noOp = service.change(ChangeOrderStatusCommand(orderId = order.orderId, targetStatus = OrderStatus.PAID))

        assertFalse(noOp.changed)
        assertEquals(OrderStatus.PAID, noOp.currentStatus)

        val histories = historyRepository.findByOrderId(order.orderId)
        assertEquals(1, histories.size)
        assertEquals(OrderStatus.CREATED, histories[0].fromStatus)
        assertEquals(OrderStatus.PAID, histories[0].toStatus)
    }
}
