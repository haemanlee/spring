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

## Kotlin coding style (Clean Code + Refactoring)
- Follow recommendations aligned with Clean Code and Refactoring principles (Robert C. Martin, Kent Beck, Martin Fowler).
- Write small, focused functions and classes with a single clear responsibility.
- Use intention-revealing names; avoid ambiguous abbreviations.
- Prefer immutable `val` over mutable `var`; minimize shared mutable state.
- Keep control flow simple: use early returns, avoid deep nesting, and reduce conditional complexity.
- Express domain behavior with clear abstractions and data classes, rather than ad-hoc procedural code.
- Remove duplication first through extraction and composition; favor readability over cleverness.
- Refactor incrementally with behavior-preserving changes and keep code always in a releasable state.
- Handle nullability explicitly and safely using Kotlin null-safety idioms.
- Keep comments minimal and meaningful; code should explain itself through structure and naming.

## Test style conventions
- Use `given-when-then` structure in test methods and test bodies.
- Prefer test names that describe behavior in business/domain language.
- Keep each test focused on one behavior; avoid multiple assertions for unrelated outcomes.
- Arrange test fixtures clearly, act once, and assert expected behavior deterministically.

## Agent workflow notes
- Before finalizing, run targeted tests at minimum.
- Provide concise change summaries with file + line citations.
