package com.example.spring.day2.order.infra

import com.example.spring.day2.order.domain.CatalogProduct
import com.example.spring.day2.order.domain.Order
import com.example.spring.day2.order.domain.OrderStatusHistory
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface ProductCatalogRepository {
    fun findById(productId: Long): CatalogProduct
}

class InMemoryProductCatalogRepository : ProductCatalogRepository {
    private val products = ConcurrentHashMap<Long, CatalogProduct>()

    fun save(product: CatalogProduct) {
        products[product.id] = product
    }

    fun updatePrice(productId: Long, newPrice: BigDecimal) {
        val product = products[productId] ?: throw IllegalArgumentException("product not found: $productId")
        products[productId] = product.copy(unitPrice = newPrice)
    }

    override fun findById(productId: Long): CatalogProduct {
        return products[productId] ?: throw IllegalArgumentException("product not found: $productId")
    }
}

interface OrderRepository {
    fun nextId(): Long
    fun save(order: Order)
    fun findById(orderId: Long): Order?
    fun findByIdForUpdate(orderId: Long): Order?
}

class InMemoryOrderRepository : OrderRepository {
    private val sequence = AtomicLong(0)
    private val orders = ConcurrentHashMap<Long, Order>()
    private val orderLocks = ConcurrentHashMap<Long, ReentrantLock>()

    override fun nextId(): Long = sequence.incrementAndGet()

    override fun save(order: Order) {
        orders[order.id] = order.copy(items = order.items.toList())
    }

    override fun findById(orderId: Long): Order? {
        val order = orders[orderId] ?: return null
        return order.copy(items = order.items.toList())
    }

    override fun findByIdForUpdate(orderId: Long): Order? {
        val lock = orderLocks.computeIfAbsent(orderId) { ReentrantLock() }
        // DB의 FOR UPDATE와 유사하게 읽기~쓰기 구간을 보호하기 위해 잠금을 획득한다.
        lock.lock()
        val order = orders[orderId] ?: run {
            lock.unlock()
            return null
        }
        return order.copy(items = order.items.toList())
    }

    fun unlock(orderId: Long) {
        orderLocks[orderId]?.let { lock ->
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}

interface OrderStatusHistoryRepository {
    fun save(history: OrderStatusHistory)
    fun findByOrderId(orderId: Long): List<OrderStatusHistory>
}

class InMemoryOrderStatusHistoryRepository : OrderStatusHistoryRepository {
    private val histories = ConcurrentHashMap<Long, MutableList<OrderStatusHistory>>()
    private val lock = ReentrantLock()

    override fun save(history: OrderStatusHistory) {
        lock.withLock {
            val list = histories.computeIfAbsent(history.orderId) { mutableListOf() }
            // 상태 이력은 append-only로 유지하여 감사 추적 가능성을 보장한다.
            list.add(history)
        }
    }

    override fun findByOrderId(orderId: Long): List<OrderStatusHistory> {
        return lock.withLock {
            histories[orderId]?.toList().orEmpty()
        }
    }
}
