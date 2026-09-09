# システムテスト仕様書: AI OFF Yahooニュース クロール・CMS公開

## 1. 目的

本仕様書は、AI機能を OFF にした状態で、Yahooニュースから記事をクロールし、記事属性を DB に登録し、CMS に投稿し、公開サイト `baidu.tokyo` から閲覧できることを確認するためのシステムテスト仕様である。

確認目的は以下とする。

1. 目視で、システムが「ニュース取得、DB保存、CMS投稿、公開サイト表示」を実現していることを確認できる。
2. 実装が要件通りに動作するかを AI または自動テストが判定できるよう、入力、操作、確認対象、合否基準を明確にする。

## 2. テスト対象範囲

対象機能:

- `web-crawler` のバッチ処理
- Yahooニュースカテゴリページからの記事一覧取得
- Yahooニュース記事ページからのタイトル、description、本文、URL等の取得
- `site_contents` を中心とする DB 登録
- WordPress REST API を利用した CMS 投稿
- `baidu.tokyo` での投稿記事表示
- `AI_MODE=off` 時に AI 変換を行わず、取得内容を投稿に使用する動作

対象外:

- AI によるタイトル・本文の生成、要約、分類
- AI embedding、イベント統合、関連記事統合
- Yahooニュース側のサイト仕様変更そのものの品質保証
- WordPress 管理画面自体の詳細機能

## 3. 前提条件

### 3.1 環境

| 項目 | 条件 |
| --- | --- |
| 実行アプリ | `web-crawler` |
| 実行プロファイル | `dev` または本番相当プロファイル |
| DB | `application.yml` の `DB_URL` で接続可能 |
| CMS | WordPress REST API が有効 |
| 公開サイト | `https://baidu.tokyo` から CMS 投稿が閲覧可能 |
| 外部接続 | `https://news.yahoo.co.jp` と `https://baidu.tokyo` に接続可能 |

### 3.2 環境変数

| 環境変数 | 設定値 | 判定基準 |
| --- | --- | --- |
| `AI_MODE` | `off` | AI クライアント呼び出しが発生しない |
| `SKIP_CMS_POST` | `false` | CMS 投稿処理が実行される |
| `WP_BASE_URL` または `CMS_HOST` | `https://baidu.tokyo` または `baidu.tokyo` | 投稿先が `baidu.tokyo` である |
| `WP_USERNAME` | WordPress 投稿可能ユーザー | 空でない |
| `WP_APPLICATION_PASSWORD` | WordPress Application Password | 空でない。ログに出力されない |
| `POST_LIMIT` | `1` 以上 | テスト実行時の投稿件数上限 |
| `CRAWL_CONTENT_LIMIT` | `1` 以上 | テスト実行時のクロール件数上限 |
| `TARGET_SITE_INFO_IDS` | 対象SiteInfo ID、任意 | 指定時は対象SiteInfoのpoolだけを選択 |
| `TARGET_SITE_CATEGORY_IDS` | 対象SiteCategory ID、任意 | 指定時はSiteInfo条件とのANDで、limit・claim前に対象categoryだけを選択 |
| `POST_FEATURED_IMAGE_ONLY` | `true` または `false` | `true` の場合、`featured_image_url` が非空のCMS未投稿候補だけへ `POST_LIMIT` を適用。通常は `false` |

### 3.3 初期データ

DB に以下の有効データが登録されていること。

#### `site_info`

| カラム | 値 |
| --- | --- |
| `site_name` | `Yahoo News` など、Yahooニュースと判別できる名称 |
| `site_url` | `https://news.yahoo.co.jp` |
| `delete_flg` | `0` |
| `contents_type` | `1` |

#### `site_category`

| カラム | 値 |
| --- | --- |
| `site_info_id` | 上記 `site_info.site_info_id` |
| `category_name` | `国内` など |
| `category_url` | `https://news.yahoo.co.jp/topics/domestic` など |
| `category_list_url` | `https://news.yahoo.co.jp/topics/domestic` など |
| `delete_flg` | `0` |
| `cms_category_id` | WordPress 投稿先の主カテゴリ ID。設定されている場合は先頭カテゴリとして使用する |
| `cms_target_category` / `target_category` | CSV形式の追加カテゴリ ID。`cms_target_category` を優先し、空の場合のみ互換用の `target_category` を使用する |
| `list_record_select_id` | Yahooニュース一覧のレコードを取得できる CSS セレクタ |
| `title_record_select_id` | 一覧内タイトルを取得できる CSS セレクタ |
| `contents_url_selectId` | 一覧内リンクを取得できる CSS セレクタ |
| `body_select_id` | 記事本文を取得できる CSS セレクタ |
| `more_body_select_id` | 続き本文を取得できる CSS セレクタ |
| `more_body_select_txt` | 続き本文リンクの文言。複数指定する場合は CSV 形式 |

#### `site_info_process_pool`

| カラム | 値 |
| --- | --- |
| `site_info_process_id` | 任意の一意な ID |
| `site_category_id` | 上記 `site_category.site_category_id` |
| `process_status` | `1` または処理対象として読み込まれる値 |
| `process_id` | `NULL` 可 |
| `process_time` | `NULL` 可 |

## 4. 共通合否基準

本仕様書では、次の条件をすべて満たす場合のみ総合合格とする。

| 観点 | 合格条件 |
| --- | --- |
| AI OFF | `AI_MODE=off` で AI API 呼び出し、AI 生成タイトル、AI 生成本文が発生しない |
| クロール | Yahooニュースから 1 件以上の記事 URL を取得できる |
| DB登録 | 記事ごとに URL、タイトル、description、本文、カテゴリ、処理ステータスが DB に保存される |
| 重複制御 | 同一 URL が重複登録されない |
| CMS投稿 | DB登録済み記事が WordPress REST API に投稿される |
| CMSカテゴリ | DBの `cms_category_id`、`cms_target_category`、`target_category` から解決したカテゴリ ID が WordPress 投稿の `categories` に設定される。複数カテゴリ設定時は全カテゴリが含まれる |
| CMS結果保存 | CMS 投稿 ID または公開 URL を DB またはログから追跡できる |
| 公開確認 | `https://baidu.tokyo` から投稿タイトルと本文が閲覧できる |
| 秘密情報 | WordPress Application Password、Basic 認証値、AI API キーがログに出力されない |

注意: 現行実装または DB スキーマに `description`、`cms_content_id`、公開 URL 保存先が存在しない場合、該当観点は要件未達として NG と判定する。

## 5. テストケース一覧

| ID | テスト名 | 優先度 | 自動化 | 目視 |
| --- | --- | --- | --- | --- |
| ST-AIOFF-YAHOO-001 | AI OFF 設定でバッチを起動できる | P0 | 可 | 不要 |
| ST-AIOFF-YAHOO-002 | Yahooニュース一覧から記事 URL とタイトルを取得できる | P0 | 可 | 任意 |
| ST-AIOFF-YAHOO-003 | Yahooニュース記事詳細から本文と description を取得できる | P0 | 可 | 任意 |
| ST-AIOFF-YAHOO-004 | 取得記事を DB に登録できる | P0 | 可 | 不要 |
| ST-AIOFF-YAHOO-005 | 同一 URL を重複登録しない | P1 | 可 | 不要 |
| ST-AIOFF-YAHOO-006 | AI OFF では AI 加工せず投稿対象を作成する | P0 | 可 | 不要 |
| ST-AIOFF-YAHOO-007 | DB登録済み記事を CMS に投稿できる | P0 | 可 | 任意 |
| ST-AIOFF-YAHOO-008 | baidu.tokyo から公開記事にアクセスできる | P0 | 可 | 必須 |
| ST-AIOFF-YAHOO-009 | CMS投稿失敗時に対象記事と処理プールが失敗扱いになる | P1 | 可 | 不要 |
| ST-AIOFF-YAHOO-010 | ログに秘密情報が出力されない | P0 | 可 | 不要 |
| ST-AIOFF-YAHOO-011 | Yahooニュースベースの各カテゴリ(統合、国内、国際、経済、エンタメ、スポーツ、IT、サイエンス、地域)登録のコンテンツは登録可能(複数カテゴリ登録あり) | P0 | 可 | 必須 |

## 6. テストケース詳細

### ST-AIOFF-YAHOO-001 AI OFF 設定でバッチを起動できる

| 項目 | 内容 |
| --- | --- |
| 目的 | AI を無効化した状態でバッチが起動することを確認する |
| 前提 | 3章の環境変数が設定済み |
| 操作 | `web-crawler` でバッチを実行する |
| 期待結果 | アプリケーションが異常終了しない |
| AI判定基準 | ログに `AI mode` の有効化、AI API リクエスト、AI レスポンスを示す出力がない |
| NG例 | `AI_MODE=deepseek`、`AI_MODE=openai`、AI API の認証エラーがログに出る |

実行例:

```powershell
cd web-crawler
$env:AI_MODE="off"
$env:SKIP_CMS_POST="false"
$env:WP_BASE_URL="https://baidu.tokyo"
$env:POST_LIMIT="1"
$env:CRAWL_CONTENT_LIMIT="1"
.\gradlew.bat bootRun
```

### ST-AIOFF-YAHOO-002 Yahooニュース一覧から記事 URL とタイトルを取得できる

| 項目 | 内容 |
| --- | --- |
| 目的 | カテゴリページから記事候補を取得できることを確認する |
| 入力 | `site_category.category_list_url` または `category_url` |
| 期待結果 | 1 件以上の候補記事が取得される |
| DB確認 | 後続ケースで `site_contents.url` と `site_contents.title` に反映される |
| AI判定基準 | `url` が `https://news.yahoo.co.jp/` で始まり、`title` が空でなく、200文字以下である |
| NG例 | URL が相対パスのまま、タイトルが空、Yahoo以外のURLが登録される |

### ST-AIOFF-YAHOO-003 Yahooニュース記事詳細から本文と description を取得できる

| 項目 | 内容 |
| --- | --- |
| 目的 | 記事詳細ページから本文と description を取得できることを確認する |
| 入力 | ST-AIOFF-YAHOO-002 で取得した記事 URL |
| 期待結果 | 本文が取得され、description も取得可能な場合は保存される |
| AI判定基準 | `contents` が空でなく、HTMLタグだけではなく日本語本文を含む。`description` 要件がある場合は DB に保存されている |
| NG例 | 本文が空、一覧タイトルだけが本文に入る、description 保存先がない |

補足: 現行の `site_contents` エンティティと初期スキーマに `description` カラムがない場合、このケースは要件未達として NG とする。

### ST-AIOFF-YAHOO-004 取得記事を DB に登録できる

| 項目 | 内容 |
| --- | --- |
| 目的 | クロール結果が DB に永続化されることを確認する |
| 操作 | バッチ実行後に DB を確認する |
| 期待結果 | `site_contents` に 1 件以上の新規レコードが登録される |
| AI判定基準 | `url`, `title`, `contents`, `site_categoy_id`, `process_status` が要件通りである |

確認 SQL 例:

```sql
SELECT
  site_contents_id,
  url,
  title,
  contents,
  site_categoy_id,
  process_status
FROM site_contents
WHERE url LIKE 'https://news.yahoo.co.jp/%'
ORDER BY site_contents_id DESC
LIMIT 5;
```

合格条件:

- `url` は `https://news.yahoo.co.jp/` で始まる。
- `title` は `NULL` または空文字ではない。
- `title` は 200 文字以下であり、複数記事のタイトルや日時が連結されていない。
- `contents` は `NULL` または空文字ではない。
- `site_categoy_id` はテスト対象カテゴリを指す。
- CMS 投稿前の初期状態では `process_status='1'`、投稿成功後は `process_status='0'` など、実装定義と一致する。

### ST-AIOFF-YAHOO-005 同一 URL を重複登録しない

| 項目 | 内容 |
| --- | --- |
| 目的 | 同じ Yahoo 記事を複数回クロールしても重複登録されないことを確認する |
| 操作 | 同じ条件でバッチを 2 回実行する |
| 期待結果 | 同一 `url` の `site_contents` レコードは 1 件のみ |
| AI判定基準 | 次の SQL の結果が 0 件 |

確認 SQL:

```sql
SELECT url, COUNT(*) AS cnt
FROM site_contents
WHERE url LIKE 'https://news.yahoo.co.jp/%'
GROUP BY url
HAVING COUNT(*) > 1;
```

### ST-AIOFF-YAHOO-006 AI OFF では AI 加工せず投稿対象を作成する

| 項目 | 内容 |
| --- | --- |
| 目的 | `AI_MODE=off` の場合、取得したタイトルと本文が AI で変更されないことを確認する |
| 操作 | バッチ実行後、CMS 投稿されたタイトル・本文を DB 登録値と比較する |
| 期待結果 | CMS 投稿タイトルは DB の `site_contents.title` と一致または CMS 表示上のエスケープ差分のみ |
| AI判定基準 | AI由来の言い換え、要約、追記がない。AI API 呼び出しログがない |
| NG例 | DBタイトルと投稿タイトルが意味的に別物、本文が要約されている、AIレスポンスログがある |

### ST-AIOFF-YAHOO-007 DB登録済み記事を CMS に投稿できる

| 項目 | 内容 |
| --- | --- |
| 目的 | DB 登録済み記事を WordPress REST API に投稿できることを確認する |
| 前提 | `SKIP_CMS_POST=false`、WordPress 認証情報設定済み |
| 操作 | バッチ writer または通常バッチ実行により CMS 投稿を行う |
| 期待結果 | WordPress REST API が 2xx を返し、投稿が作成される |
| AI判定基準 | ログに投稿成功が出力され、対象 `site_contents.process_status` が成功値になる |
| NG例 | `SKIP_CMS_POST=true` のまま、401/403、投稿先が `baidu.tokyo` 以外、本文が空 |

確認 SQL 例:

```sql
SELECT site_contents_id, url, title, process_status
FROM site_contents
WHERE url LIKE 'https://news.yahoo.co.jp/%'
ORDER BY site_contents_id DESC
LIMIT 5;
```

補足: 要件上、CMS 投稿 ID を DB で追跡する必要がある。現行スキーマに `cms_content_id` が存在しない場合、CMS結果保存の観点は NG とする。

### ST-AIOFF-YAHOO-008 baidu.tokyo から公開記事にアクセスできる

| 項目 | 内容 |
| --- | --- |
| 目的 | CMS に登録された記事が公開サイトから閲覧できることを目視・自動確認する |
| 操作 | CMS 投稿結果の URL または `baidu.tokyo` の記事一覧・検索から対象記事を開く |
| 期待結果 | 記事ページが HTTP 200 で表示され、タイトルと本文が確認できる |
| AI判定基準 | レスポンス本文またはブラウザ表示に DB の `title` が含まれ、本文の主要文が含まれる |
| 目視基準 | ブラウザ上で記事タイトル、本文、公開状態が確認できる |
| NG例 | 404、下書き表示、ログイン必須、タイトル不一致、本文なし |

自動確認例:

```powershell
$url = "https://baidu.tokyo/<投稿URL>"
$response = Invoke-WebRequest -Uri $url -UseBasicParsing
$response.StatusCode
$response.Content.Contains("<DBに登録されたタイトル>")
```

合格条件:

- HTTP ステータスが `200`。
- ページタイトルに、DB の `site_contents.title` と同じ。
- ページ本文に、DB の `site_contents.contents` の主要文が含まれる。
- ページ本文に、引用元のサイト名、ページURLが含まれる
- 画像がある記事の場合、１枚の画像が記事のキャプチャとして登録して、各画像がCMSに登録済みであり、記事内の画像パスが登録された画像のパスで置換される
- CMSのページ本文が、引用元のHTMLタグがエスケープすることなく、そのままHTMLとしてパースされて表示すること。
- 投稿が公開状態であり、未ログインブラウザから閲覧できる。

### ST-AIOFF-YAHOO-009 CMS投稿失敗時に対象記事と処理プールが失敗扱いになる

| 項目 | 内容 |
| --- | --- |
| 目的 | CMS 投稿失敗時に失敗を検知し、処理状態を正しく更新する |
| 操作 | `WP_APPLICATION_PASSWORD` を意図的に不正値にして 1 件投稿を実行する |
| 期待結果 | 投稿対象記事が失敗ステータスになり、処理プールも失敗扱いになる |
| AI判定基準 | 401/403 等の失敗ログがあり、秘密情報は出力されず、後続処理は継続可能 |
| NG例 | 成功扱いになる、例外でバッチ全体が停止する、パスワードがログに出る |

### ST-AIOFF-YAHOO-010 ログに秘密情報が出力されない

| 項目 | 内容 |
| --- | --- |
| 目的 | 認証情報や API キーがログに出ないことを確認する |
| 操作 | バッチ実行ログを検索する |
| 期待結果 | `WP_APPLICATION_PASSWORD`、Basic 認証トークン、`AI_API_KEY` がログに含まれない |
| AI判定基準 | 秘密値そのもの、`Authorization: Basic ...`、APIキーらしき値が 0 件 |

確認例:

```powershell
Select-String -Path .\logs\*.log -Pattern "Authorization|Basic|WP_APPLICATION_PASSWORD|AI_API_KEY|application-password"
```

## 7. 目視確認観点

目視確認では、以下をスクリーンショットまたは確認メモとして残す。

| 画面 | 確認内容 |
| --- | --- |
| DB クライアント | `site_contents` に Yahoo記事の URL、タイトル、本文、description が登録されている |
| WordPress 管理画面 | 投稿一覧に対象記事が存在し、公開状態である |
| `https://baidu.tokyo` 記事ページ | 未ログイン状態でタイトルと本文が閲覧できる |
| バッチログ | クロール、DB保存、CMS投稿成功が追跡できる |

description については、DB クライアントで保存値を確認する。DB に保存先がない場合は、目視確認以前に要件未達とする。

## 8. 自動判定用チェックリスト

AI または自動テストは、以下を順に判定する。

1. 実行時環境変数で `AI_MODE=off` である。
2. 実行時環境変数で `SKIP_CMS_POST=false` である。
3. `site_info.site_url` または処理対象 URL が `https://news.yahoo.co.jp` である。
4. バッチ実行後、`site_contents` に `https://news.yahoo.co.jp/%` の URL が 1 件以上存在する。
5. 対象レコードの `title` が空でない。
6. 対象レコードの `contents` が空でない。
7. 対象レコードの `description` が要件通り保存されている。保存先がない場合は NG。
8. 同一 URL の重複がない。
9. CMS 投稿が実行され、2xx 応答または投稿成功ログがある。
10. CMS 投稿 ID または公開 URL が追跡できる。保存先がない場合は NG。
11. `https://baidu.tokyo` の公開ページが HTTP 200 を返す。
12. 公開ページに DB の `title` と本文主要文が表示される。
13. AI API 呼び出しログがない。
14. 秘密情報がログに出力されていない。
15. WordPress投稿の `categories` に、DBのカテゴリ設定から解決したカテゴリIDがすべて含まれる。複数カテゴリ設定がある場合は、複数IDが登録されている。

## 9. テスト証跡

テスト実行時は以下を保存する。

| 証跡 | 内容 |
| --- | --- |
| 実行ログ | バッチ開始、対象カテゴリ、取得件数、DB保存件数、CMS投稿結果 |
| DB抽出結果 | `site_contents`、必要に応じて `site_info_process_pool` |
| CMS API 結果 | 投稿成功時の HTTP ステータス、投稿 ID、公開 URL |
| 公開サイト確認 | `baidu.tokyo` 記事ページの URL、HTTP 200、スクリーンショット |
| 失敗時ログ | エラー種別、対象 URL、対象 ID。秘密情報は除外 |

## 10. 既知の要件差分として判定すべき事項

以下が実装に存在しない場合、システムテストは合格にしない。

| 要件 | 未対応時の判定 |
| --- | --- |
| `description` を DB に登録する | NG |
| CMS 投稿 ID または公開 URL を追跡できる | NG |
| `baidu.tokyo` から未ログインで閲覧できる | NG |
| `AI_MODE=off` で AI 呼び出しを行わない | NG |
| `SKIP_CMS_POST=false` で CMS 投稿を実行する | NG |

現行実装を確認する場合、少なくとも `site_contents` エンティティ、DB マイグレーション、WordPress 投稿レスポンス保存処理に上記要件が反映されているかを確認すること。
# WordPressカテゴリ合格条件（2026-06-29更新）

WordPress投稿の `categories` は、DBの `site_category.cms_category_id` を主カテゴリとして先頭に含むこと。
`site_category.cms_target_category` にCSV形式の追加カテゴリIDがある場合は、重複を除いて追加カテゴリとして含むこと。
`cms_target_category` が空または空白のみの場合だけ、互換用の `site_category.target_category` を追加カテゴリとして利用すること。
主カテゴリと追加カテゴリが重複する場合、WordPressへ送信するカテゴリIDは一度だけ含めること。

# WordPress画像・アイキャッチ合格条件（2026-08-13更新）

画像候補判定はCrawler内部の `ArticleImagePolicy` が行い、`site_contents.featured_image_url` には元記事の画像URLを保存する。WordPress登録は同一Crawlerフロー内の `WordPressPostService` が公式 `POST /wp-json/wp/v2/media` を使用し、そのレスポンスのmedia IDを続く `POST /wp-json/wp/v2/posts` の `featured_media` に設定すること。独自media API、media ID管理DB、独立した投稿Job・サービスは使用しないこと。AI OFF以外で投稿用copyを生成する場合も元画像URLが引き継がれること。

WordPress投稿時、テンプレート上の出典ロゴ、`site_info.logo_url`、Yahooニュースロゴ `news_*.png`、広告・トラッキング・1px画像はWordPressメディアに登録されず、`featured_media` にも設定されないこと。
テンプレート上の出典ロゴは投稿本文にも画像として含まれないこと。

取得元がWordPress REST APIの場合、取得元記事の `_embedded.wp:featuredmedia[0].source_url` または `featured_media` IDから取得した `source_url` が `site_contents.featured_image_url` に保存されること。`featured_image_url` が存在する場合は投稿先WordPressの `featured_media` として最優先で利用されること。

取得元がWordPress REST APIで `featured_image_url` が空の場合、本文中画像は本文画像としてアップロード・URL置換されるが、`featured_media` は設定されないこと。

取得元が通常HTMLの場合、明示的なimage metadata、`og:image` / `twitter:image`、記事型JSON-LD、本文中の最初の有効画像の優先順位で `site_contents.featured_image_url` が保存され、投稿先の `featured_media` に設定されること。相対URL、protocol-relative URL、lazy-load属性、`srcset` / `picture` が解決され、ロゴ、広告、tracking、極小、UI、data URI、不正URLは除外されること。

featured imageと本文画像が同じ取得元URLの場合はmediaを二重登録せず、本文画像も消さずに投稿先media URLへ置換されること。featured imageのdownloadまたはuploadだけが失敗した場合は記事投稿を継続し、有効画像がない場合は投稿リクエストに `featured_media` が含まれないこと。
