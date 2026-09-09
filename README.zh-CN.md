# crawler

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/happinesea/crawler/tree/main.svg?style=svg)](https://app.circleci.com/pipelines/github/happinesea/crawler)
[![Java 17](https://img.shields.io/badge/Java-17-437291.svg)](https://adoptium.net/temurin/releases/?version=17)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-6f42c1.svg)](LICENSE)
[![Coverage: JaCoCo artifact](https://img.shields.io/badge/Coverage-JaCoCo%20artifact-2f855a.svg)](https://app.circleci.com/pipelines/github/happinesea/crawler)

[日本語](README.md) | [English](README.en.md) | 简体中文

这是RAN系统的标准crawler实现。本repository负责Java/Spring application、
crawler业务逻辑、application test和CI质量检查。infrastructure、
production credential、deployment、backup及scheduled runtime由
[`happinesea/ran-deck`](https://github.com/happinesea/ran-deck)负责。

## 主要功能

- 使用Spring Batch和Jsoup进行采集与ingestion
- 使用Flyway和JPA连接database
- 通过WordPress REST API发布内容
- unit test、一次性MariaDB integration test和bootJar packaging

详细contract和migration说明请参阅[docs](docs/)及
[migration comparison](docs/migration/comparison.md)。

## 环境要求

- Java 17
- repository中包含的Gradle Wrapper
- 运行integration test时使用的一次性MariaDB instance

## Build与test

```powershell
.\gradlew.bat clean test integrationTest bootJar
```

`integrationTest`仅在`MARIADB_INTEGRATION=true`时运行。必须使用一次性
MariaDB，且不得传入production credential或production endpoint。CI使用
MariaDB 10.11和一次性配置值。

## JaCoCo

`test` task生成JaCoCo XML和HTML report。CircleCI将report、JUnit result
及bootJar保存为build evidence，无需外部coverage service或coverage
credential。

## CircleCI

CircleCI验证pull request和`main` revision，不负责schedule或dispatch
production run。经批准的crawler revision由另行review的ran-deck runtime和
Production Backup contract使用。

现有GitHub Actions workflow暂时重复执行build/test验证。ran-deck T-013I
负责在CircleCI path验收后将其停用。

## Contributing

- 使用通过Java 17 test、一次性MariaDB integration、bootJar、JaCoCo和
  Gradle Wrapper integrity check的pull request。
- 不得commit database、WordPress、AI、SMTP或其他credential。
- 不得在test output、README或build artifact中记录production endpoint及
  runtime secret value。
- security issue应通过repository配置的security contact私下报告，不得公开
  credential或exploit detail。

## License

本repository的source采用[Apache License 2.0](LICENSE)。third-party
dependency保留其各自的license。application CI成功并不代表执行了production
operation。目前没有project-authored attribution notice要求root `NOTICE`
file；如bundled code将来提出该要求，应在发布前补充。
