# crawler

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/happinesea/crawler/tree/main.svg?style=svg)](https://app.circleci.com/pipelines/github/happinesea/crawler)
[![Java 17](https://img.shields.io/badge/Java-17-437291.svg)](https://adoptium.net/temurin/releases/?version=17)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-6f42c1.svg)](LICENSE)
[![Coverage: JaCoCo artifact](https://img.shields.io/badge/Coverage-JaCoCo%20artifact-2f855a.svg)](https://app.circleci.com/pipelines/github/happinesea/crawler)

日本語 | [English](README.en.md) | [简体中文](README.zh-CN.md)

RAN systemのcanonical crawler実装です。このrepositoryはJava/Spring
application、crawler business logic、application test、CI quality checkを
管理します。infrastructure、production credential、deployment、backup、
scheduled runtimeは
[`happinesea/ran-deck`](https://github.com/happinesea/ran-deck)が管理します。

## 主な機能

- Spring BatchとJsoupによる収集・ingestion
- FlywayとJPAによるdatabase連携
- WordPress RESTへのposting
- unit test、disposable MariaDB integration test、bootJar packaging

詳細なcontractとmigration noteは[docs](docs/)および
[migration comparison](docs/migration/comparison.md)を参照してください。

## 要件

- Java 17
- repositoryに含まれるGradle Wrapper
- integration testを実行する場合は、使い捨てのMariaDB instance

## Buildとtest

```powershell
.\gradlew.bat clean test integrationTest bootJar
```

`integrationTest`は`MARIADB_INTEGRATION=true`の場合だけ実行されます。
使い捨てのMariaDBを使用し、production credentialやproduction endpointを
渡してはなりません。CIではMariaDB 10.11と使い捨ての値を使用します。

## JaCoCo

`test` taskはJaCoCo XML/HTML reportを生成します。CircleCIはreport、
JUnit result、bootJarをbuild evidenceとして保存します。外部coverage
serviceやcoverage credentialは必要ありません。

## CircleCI

CircleCIはpull requestと`main` revisionを検証し、production runをschedule
またはdispatchしません。承認済みcrawler revisionは、別途reviewされる
ran-deckのruntimeおよびProduction Backup contractから利用されます。

既存GitHub Actions workflowは一時的にbuild/testを重複検証しています。
ran-deck T-013IでCircleCI path受入後の廃止を管理します。

## Contributing

- Java 17 test、使い捨てMariaDB integration、bootJar、JaCoCo、
  Gradle Wrapper integrity checkを通したpull requestを使用してください。
- database、WordPress、AI、SMTPなどのcredentialをcommitしないでください。
- production endpointやruntime secret valueをtest output、README、
  build artifactに記録しないでください。
- security issueはcredentialやexploit detailを公開せず、repositoryで設定された
  security contactへ非公開で報告してください。

## License

このrepositoryのsourceには[Apache License 2.0](LICENSE)が適用されます。
third-party dependencyには各配布元のlicenseが適用されます。application CIの
成功はproduction operationを意味しません。現在、root `NOTICE` fileを必要とする
project-authored attribution noticeはありません。bundled codeが要求する場合は
配布前に追加します。
