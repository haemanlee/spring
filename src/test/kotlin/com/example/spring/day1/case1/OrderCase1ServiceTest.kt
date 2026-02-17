package com.example.spring.day1.case1

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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

    @Test
    fun `동시 주문 생성 시 order id와 orderNo는 중복되지 않는다`() {
        val catalog = ProductCatalog()
        catalog.save(Product(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))

        val service = OrderCase1Service(catalog)
        val executor = Executors.newFixedThreadPool(8)
        val ids = ConcurrentHashMap.newKeySet<Long>()
        val orderNos = ConcurrentHashMap.newKeySet<String>()

        repeat(200) {
            executor.submit {
                val order = service.createOrder(memberId = 101L, items = listOf(1L to 1))
                ids.add(order.id)
                orderNos.add(order.orderNo)
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(5, TimeUnit.SECONDS)

        assertTrue(finished)
        assertEquals(200, ids.size)
        assertEquals(200, orderNos.size)
    }

    @Test
    fun `사례2 - 상태 변경 시 이력이 누적된다`() {
        val catalog = ProductCatalog()
        catalog.save(Product(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))

        val service = OrderCase1Service(catalog)
        val order = service.createOrder(memberId = 101L, items = listOf(1L to 1))

        val paidChanged = service.changeStatus(order.id, OrderStatus.PAID, actorId = 1001L)
        val shippedChanged = service.changeStatus(order.id, OrderStatus.SHIPPED, actorId = 1002L)

        assertTrue(paidChanged)
        assertTrue(shippedChanged)
        assertEquals(OrderStatus.SHIPPED, service.findOrder(order.id).status)

        val histories = service.findStatusHistories(order.id)
        assertEquals(2, histories.size)
        assertEquals(OrderStatus.CREATED, histories[0].fromStatus)
        assertEquals(OrderStatus.PAID, histories[0].toStatus)
        assertEquals(1001L, histories[0].changedBy)
        assertEquals(OrderStatus.PAID, histories[1].fromStatus)
        assertEquals(OrderStatus.SHIPPED, histories[1].toStatus)
        assertEquals(1002L, histories[1].changedBy)
    }

    @Test
    fun `사례3 - 동일 상태 변경 요청은 no-op 처리된다`() {
        val catalog = ProductCatalog()
        catalog.save(Product(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))

        val service = OrderCase1Service(catalog)
        val order = service.createOrder(memberId = 101L, items = listOf(1L to 1))
        service.changeStatus(order.id, OrderStatus.PAID, actorId = 1001L)

        val changed = service.changeStatus(order.id, OrderStatus.PAID, actorId = 1002L)

        assertFalse(changed)
        assertEquals(OrderStatus.PAID, service.findOrder(order.id).status)
        assertEquals(1, service.findStatusHistories(order.id).size)
    }

    @Test
    fun `반환된 Order 수정은 내부 상태에 영향을 주지 않는다`() {
        val catalog = ProductCatalog()
        catalog.save(Product(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))

        val service = OrderCase1Service(catalog)
        val returnedOrder = service.createOrder(memberId = 101L, items = listOf(1L to 1))

        returnedOrder.status = OrderStatus.CANCELED

        val internalOrder = service.findOrder(returnedOrder.id)
        assertEquals(OrderStatus.CREATED, internalOrder.status)
        assertEquals(0, service.findStatusHistories(returnedOrder.id).size)
    }

    @Test
    fun `주문 수량은 1 이상이어야 한다`() {
        val catalog = ProductCatalog()
        catalog.save(Product(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))
        val service = OrderCase1Service(catalog)

        assertFailsWith<IllegalArgumentException> {
            service.createOrder(memberId = 101L, items = listOf(1L to 0))
        }
    }

    @Test
    fun `상태 이력은 동시 변경과 조회에서도 예외 없이 안전하게 조회된다`() {
        val catalog = ProductCatalog()
        catalog.save(Product(id = 1L, name = "텀블러", unitPrice = BigDecimal("10000")))
        val service = OrderCase1Service(catalog)
        val order = service.createOrder(memberId = 101L, items = listOf(1L to 1))

        val errorRef = AtomicReference<Throwable?>(null)
        val executor = Executors.newFixedThreadPool(8)
        val statuses = listOf(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELED)

        repeat(300) { index ->
            executor.submit {
                runCatching {
                    service.changeStatus(order.id, statuses[index % statuses.size], actorId = index.toLong())
                    service.findStatusHistories(order.id)
                }.onFailure { errorRef.compareAndSet(null, it) }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(5, TimeUnit.SECONDS)

        assertTrue(finished)
        assertNull(errorRef.get())
    }
}
