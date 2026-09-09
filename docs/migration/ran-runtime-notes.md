# Historical runtime notes

Historical. Not current source of truth for production; captured from ran 2ea27f1170c2351f6172b3f87db1b82718100000.

Legacy CircleCI and Coveralls badges were removed because they do not describe
the canonical repository's current CI or coverage evidence.

[![X (formerly Twitter) Follow](https://img.shields.io/twitter/follow/ThumbJava)](https://x.com/ThumbJava)

## 環境設定

このプロジェクトは、データベースやCMS接続などの機密設定に環境変数を使用します。これらの値をGitにコミットしないでください。

### 開発環境 (Codespaces)

環境変数は `.devcontainer/devcontainer.json` で設定されます。MySQLが自動的にセットアップされます。

### 本番環境 (GitHub Actions)

GitHubリポジトリの設定で以下のSecretsを設定してください：

- `DB_URL`: データベース接続URL
- `DB_USERNAME`: データベースユーザー名
- `DB_PASSWORD`: データベースパスワード
- `CMS_HOST`: CMSホストURL
- `POST_LIMIT`: コンテンツ投稿制限数 (オプション、デフォルト: 10)

### ローカル開発

プロジェクトルートに `.env` ファイルを作成してください (Gitで無視されます)：

```
DB_URL=jdbc:mysql://localhost:3306/webcrawler
DB_USERNAME=root
DB_PASSWORD=password
CMS_HOST=your-cms-host
POST_LIMIT=10
```

## Process pool / canonical URL migration runbook

V7はpool leaseのowner instance、attempt、claimed/heartbeat時刻、直前結果とカテゴリ一意制約を追加する。V8はURL由来カラム、`site_category.source_category_id`、`normalized_url_hash` unique制約を追加する。適用前にDB backupを取得し、poolのカテゴリ重複、`site_contents.url` の重複、V8 backfill後hash重複が0件であることを確認する。Flyway失敗時はbatchを起動せず、backupから復元して原因を解消する。

V5はV1との重複を回避するidempotent migrationへ修正している。既存環境に `flyway_schema_history` のV5成功行がある場合は、適用前に `flyway validate` でchecksum差を確認し、DB定義とV5内容が一致することを人間が確認した場合だけbackup取得後に `flyway repair` を行う。履歴がない環境やprod互換SQL運用ではrepairしない。

`HEARTBEAT_INTERVAL_MS` は正数かつ `PROCESSING_TIMEOUT_MINUTES` の3分の1未満にする。`MAX_CONCURRENCY` は廃止され、`CRAWL_PROCESS_LIMIT` はrun開始時に固定した対象カテゴリを処理するlogical worker数の上限として使う。各workerは固定割当されたカテゴリを順次処理し、対象カテゴリ自体はこの値で切り捨てない。`TARGET_SITE_INFO_IDS`をカンマ区切りで指定するとmanual runの対象SiteInfoを限定できる。

WordPress取得は `WORDPRESS_SOURCE_MAX_PAGES` を上限とする。masterの `source_category_id` が設定済みならその値を使い、未設定かつカテゴリURLが`/category/{slug}`形式ならREST APIでslugをカテゴリIDへ解決する。slug解決に失敗したカテゴリは無条件取得へfallbackしない。

本番はGitHub Actionsで`bootJar`を生成し、artifact SHA-256を記録して同一run内で`java -jar`実行する。確認済み本番DBにはFlyway履歴がないため、prod profileはSQL初期化を無効にしている。本番schemaへDDLを適用する場合は、起動とは分離してbackupと差分監査後に実行する。

非本番MariaDB検証は次で実行する。接続先はcleanされるため、本番URLを指定してはならない。

```powershell
$env:MARIADB_INTEGRATION='true'
$env:MARIADB_URL='jdbc:mysql://127.0.0.1:3306/webcrawler_test'
$env:MARIADB_USERNAME='crawler'
$env:MARIADB_PASSWORD='crawler'
.\gradlew.bat integrationTest
```

ローカル実行: `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun`

本番manual runは`hourly-crawl.yml`の入力でSiteInfo、CMS投稿有無、process/article上限を指定する。crawlの終了コードが0でない場合、既存DB行の受入確認が成功してもworkflow全体を失敗にする。

## ライセンス

番创知库(loveapple.cn)
