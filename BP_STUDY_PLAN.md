# 실무 BP 중심 학습 계획 (주문 / 정산 / 게시판)

> 전제: **Spring Boot + Kotlin + Spring Data JPA + MySQL/PostgreSQL**

## 전체 학습 로드맵 (3~4주)

### Week 0 (0.5~1일): 공통 세팅
- 멀티모듈(권장) 또는 단일모듈 구성
- 공통 엔티티: `BaseEntity(createdAt, updatedAt, createdBy, updatedBy)` + Auditing
- 공통 정책: 에러/응답 표준, 트랜잭션 전략(`@Transactional` readOnly/쓰기 분리)

---

## Week 1 — 주문(Order) 설계 BP 집중

### Day 1: 이론
- 도메인 경계 분리: `Order / OrderItem / Payment / Shipment`
- 상태 모델링: `Enum` vs 코드 테이블 (변경 빈도/메타데이터 필요성 기준)
- 스냅샷(반정규화): 주문 시점 상품명/가격/옵션을 `OrderItem`에 저장
- 상태 이력: `OrderStatusHistory`로 상태 변경 추적

### Day 2~3: BP 구현
- 주문 생성: `Order + OrderItems` 저장 (상품 스냅샷 포함)
- 상태 변경: **현재 상태 업데이트 + 이력 INSERT-only**
- 조회 성능: `OrderSummary` DTO 전용 조회(`fetch join`/전용 쿼리)

### Day 4: 실무 체크리스트
- Soft delete 금지(주문 데이터는 보존), 취소/환불은 상태로 관리
- idempotency key 도입 여부 판단(재시도 요청 대비)
- 인덱스: `(member_id, created_at)`, `order_no unique`, `status`, `history.order_id`

### 샘플 코드 (Kotlin + JPA)

#### 1) 상태 Enum (고정 값)
```kotlin
enum class OrderStatus {
    CREATED, PAID, SHIPPED, DELIVERED, CANCELED
}
```

#### 2) 주문/아이템 + 상품 스냅샷
```kotlin
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "orders")
class Order(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    val orderNo: String,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.CREATED,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf(),
) {
    fun addItem(item: OrderItem) {
        items += item
        item.order = this
    }

    fun changeStatus(newStatus: OrderStatus) {
        this.status = newStatus
    }
}

@Entity
@Table(name = "order_items")
class OrderItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    lateinit var order: Order,

    // 상품 스냅샷 (중요 BP)
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "product_name", nullable = false, length = 200)
    val productNameSnapshot: String,

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    val unitPriceSnapshot: BigDecimal,

    @Column(nullable = false)
    val quantity: Int,
)
```

#### 3) 상태 이력 (INSERT-only)
```kotlin
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "order_status_histories",
    indexes = [
        Index(name = "idx_order_hist_order_id_created_at", columnList = "order_id, created_at")
    ]
)
class OrderStatusHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    val fromStatus: OrderStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    val toStatus: OrderStatus,

    @Column(name = "changed_by", nullable = true)
    val changedBy: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
```

#### 4) 상태 변경 서비스 + 동시성 잠금
```kotlin
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface OrderRepository : Repository<Order, Long> {
    fun save(order: Order): Order

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Order?

    fun findById(id: Long): java.util.Optional<Order>
}

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val historyRepository: OrderStatusHistoryRepository,
) {
    @Transactional
    fun changeStatus(orderId: Long, newStatus: OrderStatus, actorId: Long?) {
        val order = orderRepository.findByIdForUpdate(orderId)
            ?: throw IllegalArgumentException("order not found")

        val old = order.status
        if (old == newStatus) return

        order.changeStatus(newStatus)
        historyRepository.save(
            OrderStatusHistory(
                orderId = order.id,
                fromStatus = old,
                toStatus = newStatus,
                changedBy = actorId,
            )
        )
    }
}
```

---

## Week 2 — 정산(Settlement) BP 집중

### Day 1: 이론
- 원천(거래/결제 원장) vs 집계(정산 결과) 분리
- 멱등성(idempotency): 배치 재실행/중복 이벤트 대응
- 결정적 계산(deterministic): 수수료율/룰 버전 관리
- 마감(Closing): 확정 후 수정 불가, 조정은 `Adjustment`로 분리

### Day 2~3: BP 구현
- 원천 테이블: `LedgerTransaction` (INSERT-only)
- 정산 결과: `MerchantSettlement(month, merchantId, amount...) + UNIQUE`
- 처리 로그: `SettlementJobRun(runId, status, processedCount, from/to)`
- 조정 테이블: `SettlementAdjustment(+/- amount, reason)`

### Day 4: 실무 체크리스트
- 실패/재시도 전략: runId로 실행 이력 분리
- UNIQUE 키: `(merchant_id, settlement_month, settlement_version)` 권장
- 대용량 대응: 월 파티셔닝 고려

### 샘플 코드

#### 1) 원장 거래 (INSERT-only)
```kotlin
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "ledger_transactions",
    indexes = [
        Index(name = "idx_ledger_merchant_paid_at", columnList = "merchant_id, paid_at"),
        Index(name = "idx_ledger_idem_key", columnList = "idempotency_key", unique = true),
    ]
)
class LedgerTransaction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: Long,

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "fee_rate_bp", nullable = false)
    val feeRateBp: Int, // 100bp = 1%

    @Column(name = "paid_at", nullable = false)
    val paidAt: Instant,

    @Column(name = "idempotency_key", nullable = false, length = 80, unique = true)
    val idempotencyKey: String,
)
```

#### 2) 월 정산 결과 (UNIQUE로 멱등성 확보)
```kotlin
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "merchant_settlements",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_merchant_month_version",
            columnNames = ["merchant_id", "settlement_month", "settlement_version"],
        )
    ]
)
class MerchantSettlement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: Long,

    @Column(name = "settlement_month", nullable = false, length = 6)
    val settlementMonth: String, // e.g. "202602"

    @Column(name = "settlement_version", nullable = false)
    val settlementVersion: Int = 1,

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 2)
    val grossAmount: BigDecimal,

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    val feeAmount: BigDecimal,

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 2)
    val netAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: SettlementStatus = SettlementStatus.DRAFT,
)

enum class SettlementStatus { DRAFT, CONFIRMED }
```

#### 3) 정산 배치 서비스 (멱등)
```kotlin
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class SettlementService(
    private val ledgerRepo: LedgerRepository,
    private val settlementRepo: MerchantSettlementRepository,
) {
    @Transactional
    fun settleMonth(merchantId: Long, month: String, version: Int = 1) {
        if (settlementRepo.existsByMerchantIdAndSettlementMonthAndSettlementVersion(merchantId, month, version)) {
            return
        }

        val (from, to) = monthToRange(month)
        val txs = ledgerRepo.findAllByMerchantIdAndPaidAtBetween(merchantId, from, to)

        val gross = txs.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
        val fee = txs.fold(BigDecimal.ZERO) { acc, t ->
            acc + t.amount.multiply(BigDecimal(t.feeRateBp)).divide(BigDecimal(10000))
        }
        val net = gross - fee

        settlementRepo.save(
            MerchantSettlement(
                merchantId = merchantId,
                settlementMonth = month,
                settlementVersion = version,
                grossAmount = gross,
                feeAmount = fee,
                netAmount = net,
            )
        )
    }

    private fun monthToRange(month: String): Pair<Instant, Instant> {
        val year = month.substring(0, 4).toInt()
        val m = month.substring(4, 6).toInt()
        val start = LocalDate.of(year, m, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = LocalDate.of(year, m, 1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return start to end
    }
}
```

---

## Week 3 — 게시판/댓글(Board) BP 집중

### Day 1: 이론
- 댓글 구조: `Adjacency List(parentId)` (대댓글 1~2단계에 적합)
- Soft Delete: `status` 기반 블라인드/삭제 처리
- 조회수/좋아요: 핫스팟 고려(초기 단순 업데이트, 이후 캐시/배치)

### Day 2~3: BP 구현
- `Post/Comment` CRUD
- 삭제 정책
  - 글 삭제: `status=DELETED` + 본문/제목 마스킹
  - 댓글 삭제: UI에 “삭제된 댓글입니다” 노출
- 조회 최적화: `post_id + created_at` 인덱스

### 샘플 코드 (Soft Delete + 댓글 구조)
```kotlin
import jakarta.persistence.*

enum class PostStatus { ACTIVE, DELETED, BLINDED }
enum class CommentStatus { ACTIVE, DELETED, BLINDED }

@Entity
@Table(name = "posts", indexes = [Index(name = "idx_posts_created_at", columnList = "created_at")])
class Post(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 200)
    var title: String,

    @Lob
    @Column(nullable = false)
    var content: String,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PostStatus = PostStatus.ACTIVE,
) {
    fun softDelete() {
        status = PostStatus.DELETED
        title = "[삭제된 글]"
        content = ""
    }
}

@Entity
@Table(
    name = "comments",
    indexes = [
        Index(name = "idx_comments_post_created_at", columnList = "post_id, created_at"),
        Index(name = "idx_comments_parent_id", columnList = "parent_comment_id"),
    ]
)
class Comment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "parent_comment_id")
    val parentCommentId: Long? = null,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(nullable = false, length = 2000)
    var content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CommentStatus = CommentStatus.ACTIVE,
) {
    fun softDelete() {
        status = CommentStatus.DELETED
        content = "삭제된 댓글입니다."
    }
}
```

---

## 공부 방식(실무형 루틴)

각 케이스(주문/정산/게시판)마다 아래 4단계를 동일하게 반복합니다.

1. 요구사항 10개 작성
   - 예: 주문 가격 스냅샷 보존, 상태 이력, 웹훅 중복 안전성
2. ERD + 키/인덱스 설계
   - PK/FK, 유니크키(멱등), 조회패턴 기반 인덱스
3. 트랜잭션/동시성 시나리오 3개 테스트
   - 상태 변경 경합, 정산 재실행, 댓글 삭제/조회 경합
4. 운영 포인트 문서화
   - 삭제 정책, 이력 정책, 정산 마감, 재처리 기준

---

## 바로 실행 가능한 TODO

원하면 아래까지 포함한 **스터디 템플릿**으로 확장할 수 있습니다.
- `order / settlement / board` bounded context 폴더 분리
- `repository / query` 레이어 분리
- 테스트 코드(멱등성/동시성 중심)
- 마이그레이션 DDL(Flyway/Liquibase)

### DB 선택 가이드
- **MySQL**: 운영 레퍼런스 많고 학습 진입이 쉬움
- **PostgreSQL**: CTE/partial index 등 고급 기능 활용에 유리

> 추천: 학습 초반은 MySQL, BP 심화/최적화 학습은 PostgreSQL도 병행
