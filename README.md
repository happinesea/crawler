# crawler

Canonical crawler implementation: `happinesea/crawler`.

Java 17 / Spring Boot dependencies are defined in [build.gradle](build.gradle). Source and tests are under `src/`. Preserve ingestion, lease ownership, URL identity, Flyway and WordPress posting behavior.

```powershell
.\gradlew.bat test bootJar
```

MariaDB integration tests require an explicitly configured disposable test database; see [runtime notes](docs/migration/ran-runtime-notes.md). They are not production verification.

- [Batch](docs/02_batch.md), [posting contract](docs/03_api.md), [system acceptance tests](docs/08_system_test_spec_ai_off_yahoo_to_cms.md)
- [Migration and three-source comparison](docs/migration/comparison.md)
- [WordPress consumer owner](https://github.com/loveapple/wordpress)
- [Infrastructure and production owner](https://github.com/happinesea/ran-deck)

No production workflows are activated here by this migration. ran-deck currently consumes the old ran checkout and needs a separately reviewed source-contract update before ran cleanup merges.

The old LICENSE file is unchanged pending a separate distribution/license decision. It is not an input to implementation selection or current architecture. LICENSE.ran-Apache-2.0 records imported provenance, not a newly selected repository license. License selection remains REVIEW REQUIRED.
