# Decisions

Migrated decisions retain their original dates. Production statements are historical evidence, not fresh verification.

## 2026-07-23: web-crawlerの第1段階信頼性改善はカテゴリ単位リースで導入する

### 決定

`web-crawler` の信頼性改善第1段階では、現在の単一Spring Boot / Spring Batch構成を維持し、カテゴリ1件を復旧単位とする `chunk(1)`、`site_info_process_pool` の条件付き取得、`process_id` による所有権、`PROCESSING` タイムアウト回収、Job終了時の構造化サマリーを導入する。

Stepでは `ResourcelessTransactionManager` を使い続け、外部HTTP通信を長時間のDBトランザクションで囲まない。DB更新はプール取得、本文保存、投稿状態更新、プール完了の短い明示的トランザクションへ分離する。

### 理由

前回レビューで挙がった全面的なStep分離や投稿冪等化は効果が大きい一方、影響範囲も大きい。第1段階では、JVM停止後の `PROCESSING` 残留、同一カテゴリの二重取得、失敗があるのにJobが単純成功に見える問題を、既存構成のまま独立して改善する。

### 影響範囲

- `web-crawler` のSpring Batch設定、プール取得・完了更新、ジョブ集計、起動時設定検証。
- `site_info_process_pool.site_info_process_id` のDB採番化。
- `site_info_process_pool.process_id` の `varchar(64)` 化。
- `docs/02_batch.md` の状態遷移、リース、トランザクション境界、本番適用前確認手順。

### 制約

- 本番DBの実データを確認できないため、`site_category_id` の一意制約は今回追加しない。重複検出SQLで0件を確認した後、別PRで追加を検討する。
- `V1__init_schema.sql` と `V5__add_featured_image_url.sql` の `featured_image_url` 重複は記録に留め、既存DBのFlyway履歴と実カラム状態を確認できるまで既存マイグレーションは修正しない。
- MySQL固有のロック・一意制約検証はH2テストだけで完了扱いにせず、別PRまたは本番相当環境での統合テスト課題として残す。
- WordPress投稿の完全冪等化、クロールStepと投稿Stepの分離、`url_hash` 導入、AI状態保存DB変更は今回行わない。
## 2026-08-05: Webクローラーのpool状態とURL同一性をDB制約で確定する

`site_info_process_pool` は処理履歴ではなく再利用可能なlease枠とし、永続状態を `NONE(AVAILABLE) -> PROCESSING -> NONE` とする。処理結果は `last_result_status` へ分離し、claim、heartbeat、releaseはownerと単調増加attemptでfencingする。固定 `MAX_CONCURRENCY` は廃止し、claim候補pool数を並列上限とする。

記事URLはrequested、redirected、canonical、normalized、sourceを分離し、normalized URLのSHA-256 unique制約で同時insertを防ぐ。外部canonicalは既定拒否、same-siteは明示設定時だけ許可する。V7/V8は本番適用前にbackupと重複監査を必須とし、通常CIは本番DB・WordPress・AIへ接続しない。
