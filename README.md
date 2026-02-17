# spring

기존 저장소는 스프링 개념 정리 문서만 있었기 때문에, **Java 25 + Kotlin 기반의 최신 Spring Boot 프로젝트 구조**로 업그레이드했습니다.

## 적용된 업그레이드

- Java Toolchain: **25**
- Kotlin: **2.1.10**
- Spring Boot: **3.5.0**
- Gradle: Kotlin DSL(`build.gradle.kts`) 기반 구성

## 프로젝트 구성

- `settings.gradle.kts`: 프로젝트 이름 및 Java Toolchain 자동 해석 플러그인 설정
- `build.gradle.kts`: Kotlin + Spring Boot + Java 25 설정
- `src/main/kotlin/com/example/spring/SpringApplication.kt`: 애플리케이션 진입점
- `src/test/kotlin/com/example/spring/SpringApplicationTests.kt`: 기본 컨텍스트 로드 테스트

## 실행

```bash
gradle bootRun
```

## 테스트

```bash
gradle test
```
