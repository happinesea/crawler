---
name: ran-spring-batch-development
description: Use this ran skill when inspecting, implementing, debugging, testing, or reviewing code under web-crawler, including Java, Spring Boot, Spring Batch, JPA, Flyway, H2/MySQL, Jsoup crawling, AI analysis, and WordPress REST posting. Use for source changes and read-only reviews; do not use it to deploy, change production databases, edit WordPress themes, or operate production services.
---

# Ran Spring Batch Development

Implement and review the `web-crawler/` subsystem while preserving its batch, persistence, and external-posting contracts.

## Required Reading

Always read:

- [shared development policy](https://github.com/loveapple/ran/blob/main/ai/skills/common/development-workflow-policy.md)
- `../../../docs/01_architecture.md`
- `../../../docs/02_batch.md`
- `../../../docs/03_api.md`
- `../../../build.gradle`

Read the affected source, tests, `application*.yml`, and Flyway migrations before changing their contracts. Read `../../../docs/08_system_test_spec_ai_off_yahoo_to_cms.md` for crawl-to-CMS behavior.

## Scope

Do:

- Implement or review Java, Spring Boot, Spring Batch, JPA, Flyway, Jsoup, AI-client, and WordPress REST behavior.
- Preserve documented process states and failure transitions.
- Maintain idempotency, bounded concurrency, duplicate prevention, chunk semantics, and restart safety.
- Keep H2 tests aligned with MySQL/Flyway behavior.
- Isolate network, time, static Jsoup calls, and external APIs in tests.
- Update design, migrations, configuration, and tests when their contract changes.

Do not:

- Deploy, modify a production database, post test data to production, or expose credentials.
- Treat a review request as authority to edit files.
- Change schema, public API, or state transitions without documenting compatibility and migration impact.
- Mark unexecuted integration or production checks as passed.

## Workflow

1. Declare `レビューモード` or `実装モード`.
2. Locate the affected job, step, reader, processor, writer, service, repository, entity, configuration, and tests.
3. Map normal, empty, duplicate, partial-failure, retry, restart, and concurrent execution paths.
4. Identify transaction and status-update boundaries before editing.
5. Implement the smallest coherent change.
6. Add focused unit or repository tests; add batch/integration tests when wiring or transaction behavior changes.
7. For schema changes, add a forward Flyway migration and record rollback or compatibility limits.
8. Run focused tests, then `./gradlew test` when available.
9. Report H2-only coverage separately from MySQL, network, CMS, or production verification.

## Explaining Scope Expansion

Keep `Implement the smallest coherent change.` as the default. If a change was not directly requested, or extends the design or behavior beyond the direct cause, explain it before implementation when practical and always in the completion report. Cover:

- The original request or direct cause.
- The additional change and why the direct fix alone is insufficient.
- The concrete risk of omitting the additional change.
- Existing duplication or responsibility problems that make the change necessary.
- The smallest alternative fix and why the broader change belongs in the same work.
- Whether the additional change can be split into another PR.
- Behavior, compatibility, schema, API, state-transition, and operational impact.

For a defect, decide in this order: direct cause, smallest fix, concrete risk left by that fix, then whether an additional design is required. Do not mix unrequested generalization into a fix merely because it appears architecturally cleaner.

When adding a class, abstraction, policy, adapter, framework, database structure, or general-purpose mechanism, explain why existing processing is insufficient, not only what the new mechanism achieves. For example, justify an image policy by the specific lazy-load, logo/tracking image, relative-URL, or duplicate-media-upload risks it centralizes. This rule applies to all ran Spring Batch and web-crawler work, not only image processing.

## Completion Report

Include affected job flow and contracts, files changed, tests run, database/API impact, concurrency or restart impact, unverified environments, decisions required, and confirmation that no production operation occurred.
