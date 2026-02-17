# Day 2 — 주문(Order) BP 구현 학습 노트

> 목표: Day 1 이론(스냅샷, 상태 이력, no-op)을 실제 구현 관점으로 연결해, 서비스/리포지토리/조회 최적화의 기본 틀을 잡는다.

## 0) Day1과 Day2의 차이 (핵심)

> 한 줄 정리: **Day1은 "왜 이렇게 설계해야 하는가"**, **Day2는 "그 설계를 코드/테스트로 어떻게 실행할 것인가"**에 집중한다.

| 구분 | Day1 | Day2 |
|---|---|---|
| 초점 | 이론/설계 판단 기준 | 구현 순서/서비스 책임/쿼리 전략 |
| 질문 | "왜 스냅샷이 필요한가?" "왜 이력이 필요한가?" | "어떤 트랜잭션 경계로 저장할까?" "락은 어디에 걸까?" |
| 산출물 | 개념 정리, 사례 이해 | 체크리스트, 구현 흐름, 테스트 항목 |
| 성공 기준 | 설계를 설명할 수 있음 | 실제 코드/테스트로 재현 가능 |

즉, Day2 문서는 Day1 내용을 반복하려는 목적이 아니라,
Day1에서 합의한 원칙을 **서비스 메서드 단위로 내려서 실행 계획으로 바꾸는 단계**다.

---

## 1) 오늘의 학습 목표
- 주문 생성 시 `Order + OrderItem`을 **한 트랜잭션**으로 저장한다.
- 상품 정보는 주문 시점 스냅샷(`productNameSnapshot`, `unitPriceSnapshot`)으로 고정한다.
- 상태 변경은 `orders.status` 갱신 + `order_status_histories` INSERT-only를 함께 처리한다.
- 동일 상태 요청은 no-op으로 처리하고, 동시성 충돌 가능 구간에 락 전략을 적용한다.
- 목록/상세 조회는 엔티티 직접 노출 대신 DTO 조회를 우선 고려한다.

---

## 2) 구현 흐름 (단계별 진행)

> 요청하신 대로 "한 번에 다 구현"이 아니라, **2-1 → 2-2 → 2-3 → 2-4** 순서로 완료 기준을 두고 진행한다.

### 2-1. 1단계: 주문 생성 골격 먼저 완성
1. `CreateOrderService` 입력/출력 DTO 정의
2. 상품 카탈로그 조회 후 `OrderItem` 스냅샷 복사
3. `Order + OrderItems` 저장 (`REQUIRED`)
4. 총액 계산은 스냅샷 가격 기준으로 고정

완료 기준(DoD):
- 주문 생성 단위 테스트 통과
- 카탈로그 가격 변경 후에도 주문 총액이 변하지 않음

### 2-2. 2단계: 상태 변경 플로우 분리 구현
1. `ChangeOrderStatusService` 생성
2. `findByIdForUpdate`(또는 동등한 락 전략) 적용
3. 동일 상태 요청 no-op 처리
4. 상태 변경 + `OrderStatusHistory` INSERT-only 처리

완료 기준(DoD):
- `CREATED -> PAID -> SHIPPED` 이력 누적 검증 통과
- `PAID -> PAID` 재요청 시 이력 미생성 검증 통과

### 2-3. 3단계: 부수효과 트랜잭션 분리 (`REQUIRES_NEW`)
1. 감사 로그/알림 이력 같은 부수 작업 서비스 분리
2. `@Transactional(propagation = REQUIRES_NEW)` 적용
3. 메인 트랜잭션 롤백 시에도 부수 이력 커밋 여부 테스트

완료 기준(DoD):
- 메인 실패 상황에서도 감사 로그가 남는 테스트 확보
- 부수 작업 실패가 메인 주문 성공을 깨지 않도록 경계 확인

### 2-4. 4단계: 조회 최적화 적용
1. 목록용 `OrderSummary` DTO 쿼리 작성
2. 상세 조회는 헤더/아이템 전략 분리
3. 페이징 구간에서 컬렉션 fetch join 남용 제거

완료 기준(DoD):
- 목록/상세 응답이 엔티티 직접 노출 없이 DTO로 제공
- 기본 인덱스 조건에서 조회 성능 저하 포인트(정렬/필터) 확인

---

## 3) 추천 패키지 구조(초안)

- `day2/order/domain`
  - `Order`, `OrderItem`, `OrderStatus`, `OrderStatusHistory`
- `day2/order/application`
  - `CreateOrderService`, `ChangeOrderStatusService`, `GetOrderQueryService`
- `day2/order/infra`
  - `OrderRepository`, `OrderStatusHistoryRepository`, 쿼리 리포지토리
- `day2/order/api`
  - Request/Response DTO, Controller

---

## 4) 실무 체크포인트

### 트랜잭션
- 기본 쓰기 유스케이스: `@Transactional` (`REQUIRED`)
- 조회 유스케이스: `@Transactional(readOnly = true)`
- 독립 커밋이 필요한 부수 작업: `@Transactional(propagation = REQUIRES_NEW)`
- 외부 연동 직후 실패 가능 구간은 트랜잭션 경계를 분리해 롤백 전파 범위를 의도적으로 제어

**전파 속성별 실무 케이스**
- `REQUIRED` (기본): 주문 생성/상태 변경처럼 "성공/실패를 하나로 묶어야 하는" 핵심 플로우
- `REQUIRES_NEW`: 감사 로그, 알림 발송 이력, 실패 이벤트 적재처럼 "메인 트랜잭션 실패와 분리"가 필요한 플로우
- `NESTED`(DB/설정 제약 확인 필요): 부분 실패 복구가 중요한 배치성 처리에서 제한적으로 검토

### 동시성
- 상태 변경처럼 경합이 높은 구간은 비관적 락 또는 버전 기반 낙관적 락 검토
- 재시도 정책(클라이언트/서버)과 함께 설계

### 멱등성
- 동일 요청 재시도 가능성이 높으면 idempotency key 고려
- 최소한 동일 상태 변경 no-op은 필수

### 인덱스
- `orders(order_no unique)`
- `orders(member_id, created_at)`
- `orders(status)`
- `order_status_histories(order_id, created_at)`

---

## 4-1) 트랜잭션 유즈케이스 예시 (REQUIRES_NEW 포함)

### 유즈케이스 A: 주문 상태 변경 + 이력 저장 (`REQUIRED`)
- 목적: 주문 상태와 상태 이력을 **같은 원자 단위**로 처리
- 결과: 둘 중 하나라도 실패하면 전체 롤백

### 유즈케이스 B: 상태 변경 실패 감사 로그 적재 (`REQUIRES_NEW`)
- 상황: 상태 변경 중 예외 발생
- 처리: catch 구간에서 `OrderAuditService.saveFailureLog(...)`를 `REQUIRES_NEW`로 호출
- 결과: 메인 트랜잭션이 롤백되어도 실패 감사 로그는 커밋되어 장애 분석 가능

### 유즈케이스 C: 주문 생성 후 알림 발송 이력 저장 (`REQUIRES_NEW`)
- 상황: 주문 생성은 성공했지만 알림 시스템 지연/실패 가능
- 처리: 알림 요청 자체는 비동기/재시도 큐로 보내고, 발송 시도 이력은 `REQUIRES_NEW`로 저장
- 결과: 주문 생성 성공과 알림 실패를 분리해 사용자 정합성(주문)과 운영 추적성(알림)을 동시에 확보

---

## 5) Day 2 체크리스트

- [ ] 주문 생성 서비스: 상품 스냅샷 저장 구현
- [ ] 상태 변경 서비스: 잠금 조회 + 이력 INSERT-only 구현
- [ ] `REQUIRES_NEW` 적용 유즈케이스(감사 로그/알림 이력) 1개 이상 추가
- [ ] 동일 상태 변경 no-op 테스트 작성
- [ ] 목록 조회 DTO 쿼리 작성 (`OrderSummary`)
- [ ] 인덱스/유니크 키 DDL 점검

---

## 6) 실습 순서 제안
1. 단위 테스트 먼저 작성
   - 주문 생성 후 카탈로그 가격 변경 시에도 주문 총액 유지
   - 동일 상태 재요청 시 이력 미생성 검증
2. 서비스 구현
3. 리포지토리 쿼리 튜닝
4. API 연결 및 응답 스펙 고정

---

## 7) Day 3로 이어지는 포인트
- 주문 전이 규칙 테이블화 여부 판단
- 조회 성능 고도화(페이징 + 카운트 최적화)
- 운영 관점 로깅(누가/언제/왜 상태 변경했는지) 강화
