# crawler

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/happinesea/crawler/tree/main.svg?style=svg)](https://app.circleci.com/pipelines/github/happinesea/crawler)
[![Java 17](https://img.shields.io/badge/Java-17-437291.svg)](https://adoptium.net/temurin/releases/?version=17)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-6f42c1.svg)](LICENSE)
[![Coverage: JaCoCo artifact](https://img.shields.io/badge/Coverage-JaCoCo%20artifact-2f855a.svg)](https://app.circleci.com/pipelines/github/happinesea/crawler)

Canonical crawler implementation for the RAN system. This repository owns the
Java/Spring application, crawler business logic, application tests, and CI
quality checks. Infrastructure, production credentials, deployment, backup,
and scheduled runtime are owned by
[`happinesea/ran-deck`](https://github.com/happinesea/ran-deck).

## Build And Test

Requirements: Java 17 and the checked-in Gradle Wrapper.

```powershell
.\gradlew.bat clean test integrationTest bootJar
```

`integrationTest` runs only when `MARIADB_INTEGRATION=true` and must use a
disposable MariaDB instance. It must never receive production credentials or a
production endpoint. The CI job provides MariaDB 10.11 with disposable values.

The `test` task produces JaCoCo XML and HTML reports. CircleCI stores those
reports, JUnit results, and the bootJar as build evidence. No coverage vendor
or coverage credential is required.

## Architecture

The application is a Java 17 / Spring Boot crawler using Spring Batch, Jsoup,
Flyway, JPA, and WordPress REST posting. Detailed contracts and migration notes
are in [docs](docs/) and [the migration comparison](docs/migration/comparison.md).

CircleCI validates pull requests and `main` revisions. It does not schedule or
dispatch production runs. An approved crawler revision is consumed by
ran-deck's separately reviewed runtime and Production Backup contract.

The existing GitHub Actions workflow temporarily repeats build/test validation.
ran-deck T-013I tracks its retirement after the CircleCI path is accepted; this
PR does not remove or alter the production workflow boundary.

## Security And Contribution

- Do not commit database, WordPress, AI, SMTP, or other credentials.
- Do not put production endpoints or runtime secret values in test output,
  README files, or build artifacts.
- Use a pull request with the Java 17 test, disposable MariaDB integration,
  bootJar, JaCoCo, and wrapper-integrity checks. Verify added lines separately
  for credentials before merge.
- Report security issues privately through the repository's configured security
  contact rather than publishing credentials or exploit details.

Apache License 2.0 applies to this repository's source. Third-party
dependencies retain their own licenses; see their published metadata. No
production operation is implied by a green application-CI build. No
project-authored attribution notice currently requires a root `NOTICE` file;
add one before distribution if bundled code introduces that requirement.
