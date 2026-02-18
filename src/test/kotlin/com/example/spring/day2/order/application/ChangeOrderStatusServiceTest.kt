package com.example.spring.day2.order.application

import com.example.spring.day2.order.domain.CatalogProduct
import com.example.spring.day2.order.domain.Order
import com.example.spring.day2.order.domain.OrderAuditLog
import com.example.spring.day2.order.domain.OrderItem
import com.example.spring.day2.order.domain.OrderStatus
import com.example.spring.day2.order.domain.OrderStatusHistory
import com.example.spring.day2.order.infra.InMemoryOrderAuditLogRepository
import com.example.spring.day2.order.infra.InMemoryOrderRepository
import com.example.spring.day2.order.infra.InMemoryOrderStatusHistoryRepository
import com.example.spring.day2.order.infra.InMemoryProductCatalogRepository
import com.example.spring.day2.order.infra.OrderAuditLogRepository
import com.example.spring.day2.order.infra.OrderRepository
import com.example.spring.day2.order.infra.OrderStatusHistoryRepository
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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

    @Test
    fun `2-2 이력 저장 실패 시 주문 상태는 원복되어 원자성이 유지된다`() {
        val catalogRepository = InMemoryProductCatalogRepository().apply {
            save(CatalogProduct(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))
        }
        val orderRepository = InMemoryOrderRepository()
        val createOrderService = CreateOrderService(catalogRepository, orderRepository)
        val service = ChangeOrderStatusService(orderRepository, FailingOrderStatusHistoryRepository())

        val order = createOrderService.create(
            CreateOrderCommand(
                memberId = 101L,
                items = listOf(CreateOrderItemCommand(productId = 1L, quantity = 1)),
            )
        )

        assertFailsWith<IllegalStateException> {
            service.change(ChangeOrderStatusCommand(orderId = order.orderId, targetStatus = OrderStatus.PAID))
        }

        val savedOrder = requireNotNull(orderRepository.findById(order.orderId))
        assertEquals(OrderStatus.CREATED, savedOrder.status)
    }

    @Test
    fun `2-3 메인 상태 변경 실패여도 감사 로그는 기록된다`() {
        val catalogRepository = InMemoryProductCatalogRepository().apply {
            save(CatalogProduct(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))
        }
        val orderRepository = InMemoryOrderRepository()
        val createOrderService = CreateOrderService(catalogRepository, orderRepository)
        val auditLogRepository = InMemoryOrderAuditLogRepository()
        val auditService = OrderAuditService(auditLogRepository)
        val service = ChangeOrderStatusService(
            orderRepository = orderRepository,
            orderStatusHistoryRepository = FailingOrderStatusHistoryRepository(),
            orderAuditService = auditService,
        )

        val order = createOrderService.create(
            CreateOrderCommand(
                memberId = 101L,
                items = listOf(CreateOrderItemCommand(productId = 1L, quantity = 1)),
            )
        )

        assertFailsWith<IllegalStateException> {
            service.change(ChangeOrderStatusCommand(orderId = order.orderId, targetStatus = OrderStatus.PAID))
        }

        val auditLogs = auditLogRepository.findByOrderId(order.orderId)
        assertEquals(1, auditLogs.size)
        assertTrue(auditLogs.first().message.contains("status change failed"))
    }

    @Test
    fun `2-3 감사 로그 저장 실패가 발생해도 메인 상태 변경은 성공한다`() {
        val catalogRepository = InMemoryProductCatalogRepository().apply {
            save(CatalogProduct(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))
        }
        val orderRepository = InMemoryOrderRepository()
        val createOrderService = CreateOrderService(catalogRepository, orderRepository)
        val historyRepository = InMemoryOrderStatusHistoryRepository()
        val auditService = OrderAuditService(FailingOrderAuditLogRepository())
        val service = ChangeOrderStatusService(
            orderRepository = orderRepository,
            orderStatusHistoryRepository = historyRepository,
            orderAuditService = auditService,
        )

        val order = createOrderService.create(
            CreateOrderCommand(
                memberId = 101L,
                items = listOf(CreateOrderItemCommand(productId = 1L, quantity = 1)),
            )
        )

        val result = service.change(ChangeOrderStatusCommand(orderId = order.orderId, targetStatus = OrderStatus.PAID))

        assertTrue(result.changed)
        assertEquals(OrderStatus.PAID, result.currentStatus)
        assertEquals(1, historyRepository.findByOrderId(order.orderId).size)
    }

    @Test
    fun `2-3 주문 상태 저장이 실패해도 감사 로그는 기록된다`() {
        val orderRepository = FailingStatusUpdateOrderRepository(
            initialOrder = Order(
                id = 1L,
                orderNo = "ORD-00000001",
                memberId = 101L,
                status = OrderStatus.CREATED,
                items = listOf(
                    OrderItem(
                        productId = 1L,
                        productNameSnapshot = "텀블러",
                        unitPriceSnapshot = BigDecimal("10000"),
                        quantity = 1,
                    )
                ),
            )
        )
        val auditLogRepository = InMemoryOrderAuditLogRepository()
        val auditService = OrderAuditService(auditLogRepository)
        val service = ChangeOrderStatusService(
            orderRepository = orderRepository,
            orderStatusHistoryRepository = InMemoryOrderStatusHistoryRepository(),
            orderAuditService = auditService,
        )

        assertFailsWith<IllegalStateException> {
            service.change(ChangeOrderStatusCommand(orderId = 1L, targetStatus = OrderStatus.PAID))
        }

        val auditLogs = auditLogRepository.findByOrderId(1L)
        assertEquals(1, auditLogs.size)
        assertTrue(auditLogs.first().message.contains("order save failed"))
    }

    private class FailingOrderStatusHistoryRepository : OrderStatusHistoryRepository {
        override fun save(history: OrderStatusHistory) {
            throw IllegalStateException("history save failed")
        }

        override fun findByOrderId(orderId: Long): List<OrderStatusHistory> = emptyList()
    }

    private class FailingOrderAuditLogRepository : OrderAuditLogRepository {
        override fun save(log: OrderAuditLog) {
            throw IllegalStateException("audit log save failed")
        }

        override fun findByOrderId(orderId: Long): List<OrderAuditLog> = emptyList()
    }

    private class FailingStatusUpdateOrderRepository(
        private var currentOrder: Order,
    ) : OrderRepository {
        override fun nextId(): Long = currentOrder.id

        override fun save(order: Order) {
            if (order.status != currentOrder.status) {
                throw IllegalStateException("order save failed")
            }
            currentOrder = order
        }

        override fun findById(orderId: Long): Order? = currentOrder.takeIf { it.id == orderId }

        override fun findByIdForUpdate(orderId: Long): Order? = currentOrder.takeIf { it.id == orderId }
    }

}
