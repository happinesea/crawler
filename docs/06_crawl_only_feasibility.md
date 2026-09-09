# クロール処理のみ（WordPress送信なし）実装可否

## 結論
可能です。現在のソースコードと仕様書の範囲で、**クロールしてDB登録する処理のみ**を先行稼働できます。

## 根拠
- `CrawlerComponents.siteInfoProcessor()` でカテゴリ一覧取得→各記事詳細取得→`bulkInsertIfNotExists` による重複除外保存まで実装済み。
- `BatchConfig` で `crawlJob` / `crawlStep` が組まれており、`SiteInfoProcessPool` を単位に処理を回せる。
- 仕様書 `docs/02_batch.md` でも、バッチの主処理は「取得→保存」で、CMS投稿は別責務として整理されている。

## 今回の対応
- `web-crawler.skip-cms-post` を追加し、既定値を `true` に設定。
- `skip-cms-post=true` の場合は CMS 投稿をスキップして `SUCCESS`。
- `skip-cms-post=false` の場合は、投稿未実装のため `FAIL` とし、誤って成功扱いしないようにした。

## 運用条件（クロールのみ）
1. `SKIP_CMS_POST=true` を設定（未設定でも既定値 true）。
2. DB接続情報（`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`）を環境変数で設定。
3. バッチ実行後、`site_contents` と `site_info_process_pool` の更新を確認。

## 今後（API確定後）
- CMS投稿クライアント実装。
- 投稿結果に応じた `SiteContents.processStatus` 更新。
- 投稿失敗時のリトライ/再実行ポリシー追加。
