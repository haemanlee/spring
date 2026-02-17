# Day1 사례1+2 통합 구현 메모

## 구현 대상
- `docs/DAY1_ORDER_THEORY_CASES.md`의 **사례 A — 상품 가격 변경 이슈**
- `docs/DAY1_ORDER_THEORY_CASES.md`의 **사례 B — 상태 변경 분쟁 이슈**
- `docs/DAY1_ORDER_THEORY_CASES.md`의 **사례 C — 중복 상태 변경 요청**

## 구현 내용
- 주문 생성 시 상품 정보를 조회해 아래 스냅샷 필드를 `OrderItem`에 저장
  - `productNameSnapshot`
  - `unitPriceSnapshot`
- 주문 생성 이후 상품 카탈로그 가격이 변경되어도 주문 총액 계산은 스냅샷 가격을 기준으로 유지
- 주문 엔티티에 `orderNo`, `status(CREATED)` 포함
- 같은 서비스(`OrderCase1Service`)에서 상태 변경과 이력 누적을 함께 처리
  - 상태 변경 시 이력 INSERT-only 저장
  - 동일 상태 요청은 no-op(`false` 반환, 이력 미적재)

## 코드 위치
- `src/main/kotlin/com/example/spring/day1/case1/OrderCase1Models.kt`
- `src/test/kotlin/com/example/spring/day1/case1/OrderCase1ServiceTest.kt`

## 검증 포인트
- 초기 가격 10,000원으로 주문 생성
- 카탈로그 가격을 12,000원으로 변경
- 주문 스냅샷 가격은 10,000원을 유지
- 총액 계산은 20,000원(10,000 x 2) 유지
- `CREATED -> PAID -> SHIPPED` 변경 시 이력 2건 누적 확인
- 동일 상태 재요청(`PAID -> PAID`)은 no-op 처리 확인
