# AGENTS.md

## Repository scope
These instructions apply to the entire repository.

## Project overview
- Tech stack: Spring Boot (Kotlin), Gradle Kotlin DSL
- Main app entrypoint: `src/main/kotlin/com/example/spring/SpringApplication.kt`
- Tests: `src/test/kotlin/...`

## Common commands
- Run app: `./gradlew bootRun`
- Run tests: `./gradlew test`
- Build: `./gradlew build`

## Working conventions
- Prefer Kotlin idioms and immutable data classes where possible.
- Keep package structure aligned with feature folders (e.g. `day1/case1`).
- Add or update tests for behavior changes.
- Keep docs in `docs/` in sync when changing case-study behavior.

## Agent workflow notes
- Before finalizing, run targeted tests at minimum.
- Provide concise change summaries with file + line citations.
