# crawler

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/happinesea/crawler/tree/main.svg?style=svg)](https://app.circleci.com/pipelines/github/happinesea/crawler)
[![Java 17](https://img.shields.io/badge/Java-17-437291.svg)](https://adoptium.net/temurin/releases/?version=17)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-6f42c1.svg)](LICENSE)
[![Coverage: JaCoCo artifact](https://img.shields.io/badge/Coverage-JaCoCo%20artifact-2f855a.svg)](https://app.circleci.com/pipelines/github/happinesea/crawler)

[日本語](README.md) | English | [简体中文](README.zh-CN.md)

The canonical crawler implementation for the RAN system. This repository owns
the Java/Spring application, crawler business logic, application tests, and CI
quality checks. Infrastructure, production credentials, deployment, backup,
and scheduled runtime are owned by
[`happinesea/ran-deck`](https://github.com/happinesea/ran-deck).

## Features

- Collection and ingestion with Spring Batch and Jsoup
- Database integration with Flyway and JPA
- Posting through the WordPress REST API
- Unit tests, disposable MariaDB integration tests, and bootJar packaging

See [docs](docs/) and the
[migration comparison](docs/migration/comparison.md) for detailed contracts
and migration notes.

## Requirements

- Java 17
- The checked-in Gradle Wrapper
- A disposable MariaDB instance when running integration tests

## Build and Test

```powershell
.\gradlew.bat clean test integrationTest bootJar
```

`integrationTest` runs only when `MARIADB_INTEGRATION=true`. It must use a
disposable MariaDB instance and must never receive production credentials or a
production endpoint. CI uses MariaDB 10.11 with disposable values.

## JaCoCo

The `test` task generates JaCoCo XML and HTML reports. CircleCI stores the
reports, JUnit results, and bootJar as build evidence. No external coverage
service or coverage credential is required.

## CircleCI

CircleCI validates pull requests and `main` revisions. It does not schedule or
dispatch production runs. An approved crawler revision is consumed by
ran-deck's separately reviewed runtime and Production Backup contract.

The existing GitHub Actions workflow temporarily duplicates build and test
validation. ran-deck T-013I tracks its retirement after the CircleCI path is
accepted.

## Contributing

- Use a pull request that passes the Java 17 tests, disposable MariaDB
  integration, bootJar, JaCoCo, and Gradle Wrapper integrity checks.
- Do not commit database, WordPress, AI, SMTP, or other credentials.
- Do not include production endpoints or runtime secret values in test output,
  README files, or build artifacts.
- Report security issues privately through the configured repository security
  contact. Do not publish credentials or exploit details.

## License

This repository's source is licensed under the
[Apache License 2.0](LICENSE). Third-party dependencies retain their own
licenses. A successful application-CI build does not imply a production
operation. No project-authored attribution notice currently requires a root
`NOTICE` file; add one before distribution if bundled code introduces that
requirement.
