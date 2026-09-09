# マルチスレッド実行の現状レビュー

## 結論
- 現状でも動作する可能性はありますが、**そのままでは負荷時に不安定化するポイント**がありました。
- 今回、最低限の修正として「スレッドセーフなReader利用」と「同時実行数の上限設定」を追加しました。

## 主な懸念点（修正前）
1. `BatchConfig` は `ListItemReader` を直接使っており、`taskExecutor` 併用時に読み取り競合のリスクがある。
2. スレッド数を `targetList.size()` にしていたため、対象件数が多いと過剰スレッドが生成される。
3. `queueCapacity=0` でスパイク時の余裕が少なく、実行環境次第で不安定になりうる。

## 今回の修正
- Reader を `CrawlerComponents.siteInfoProcessReader()`（`SynchronizedItemReader`）に統一。
- 同時実行数を `web-crawler.max-concurrency` で制御（既定4）。
- 実行キューを `queueCapacity=threadCount` に変更し、急激な詰まりを緩和。
- 対象サイトはDBの `site_info` / `site_category` マスタで管理する。

## 追加で確認したいこと
- 本番DBの同時更新時ロック待ち/タイムアウトは当面10秒（`jakarta.persistence.lock.timeout=10000`）。
- `ContentsParser` の外部サイト接続失敗時リトライは当面3回（`external-connect-retry-count=3`）。
- バッチ再実行時の `SiteInfoProcessPool` の FAIL 再試行ルールは一旦現状維持（追加実装なし）。
