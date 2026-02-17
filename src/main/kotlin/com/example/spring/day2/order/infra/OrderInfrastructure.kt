package com.example.spring.day2.order.infra

import com.example.spring.day2.order.domain.CatalogProduct
import com.example.spring.day2.order.domain.Order
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
}

class InMemoryOrderRepository : OrderRepository {
    private val sequence = AtomicLong(0)
    private val orders = ConcurrentHashMap<Long, Order>()

    override fun nextId(): Long = sequence.incrementAndGet()

    override fun save(order: Order) {
        orders[order.id] = order.copy(items = order.items.toList())
    }

    override fun findById(orderId: Long): Order? {
        val order = orders[orderId] ?: return null
        return order.copy(items = order.items.toList())
    }
}
