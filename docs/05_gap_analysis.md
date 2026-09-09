# 設計ドキュメントと現行ソースの不足分析（2026-05-11時点）

## 結論
可能です。現時点の設計ドキュメント（`docs/00_overview.md`〜`docs/04_prompt.md`）と `web-crawler` ソースを突合すると、実装不足・未定義・運用不足を特定できます。

## 分析対象
- 設計: `docs/00_overview.md`, `docs/01_architecture.md`, `docs/02_batch.md`, `docs/03_api.md`, `docs/04_prompt.md`
- 実装: `src/main/java/...`, `src/main/resources/...`, `web-crawler/.github/workflows/deploy.yml`

## 不足一覧

### 1) CMS/WordPress投稿処理が未実装
- `SiteContentsService.saveAllProcessPools` に `// TODO CMSにコンテンツ登録` が残っており、実際のHTTP投稿処理が存在しない。
- 仕様上「web-crawler → WordPress連携」を前提としているため、要件と実装に差分あり。

### 2) API設計ドキュメント不足
- `docs/03_api.md` を追加し、WordPress公式REST APIを利用する投稿仕様、Application Password認証、エラー時のスキップ方針を定義済み。
- 残課題は、実装側のWordPress投稿クライアント作成とSecrets設定である。

### 3) バッチスケジュール定義不足
- 設計上は cron 実行前提だが、`deploy.yml` は push / pull_request トリガーのみで `schedule` が未設定。
- 「定期クローリング」の運用定義がコード化されていない。

### 4) 状態遷移詳細の不足
- 「ステータス更新ロジック変更禁止」という制約はあるが、遷移表（NONE/PROCESSING/SUCCESS/FAIL）と失敗復旧条件が文書化されていない。
- 現実装では例外時 FAIL 更新はあるが、再実行ポリシーや再試行上限が未定義。

### 5) 空/薄い設計ドキュメント
- `docs/01_architecture.md`、`docs/03_api.md` は内容が不足。
- `docs/00_overview.md` も見出し中心で、非機能要件や責務境界が未記述。

### 6) 運用監視要件の不足
- 監視項目（ジョブ成功率、処理件数、失敗URL、CMS投稿失敗率）やアラート基準の記述がない。
- ログ方針・保存期間・トレースID運用の設計がない。

### 7) セキュリティ/秘密情報運用の不足
- 環境変数利用方針はあるが、鍵ローテーション、最小権限、監査証跡、Secretスコープ管理が未記述。

## 優先度つき改善提案

### P0（直ちに必要）
1. WordPress投稿クライアントを実装する（`docs/03_api.md` 準拠）。
2. CMS投稿機能の実装仕様を `docs/02_batch.md` / `docs/03_api.md` に合わせて実装する。
3. GitHub Actions に `schedule` を追加し、定期実行を明文化。

### P1（次に必要）
4. 状態遷移表と再実行ポリシーを明文化（特に FAIL 時の扱い）。
5. 監視・アラート・運用Runbookを追加。

### P2（継続改善）
6. `docs/00_overview.md` と `docs/01_architecture.md` を充実化（責務分離、障害時設計、非機能要件）。

## AI向けプロンプト設計への反映ポイント
- 「実装済み」と「未実装（TODO）」を明確に分離してプロンプト化する。
- ステータス更新ロジック変更禁止をハード制約として固定する。
- API未定義箇所は仮定禁止にし、必ず設計タスクとして先出しする。
