# WordPress投稿API仕様

## 目的
`web-crawler` で取得した `site_contents` を WordPress 公式 REST API に投稿する。
独自APIは作成せず、WordPress標準の `wp-json/wp/v2` エンドポイントを利用する。

## 参照仕様
- WordPress REST API Handbook: https://developer.wordpress.org/rest-api/
- Posts API: https://developer.wordpress.org/rest-api/reference/posts/
- Categories API: https://developer.wordpress.org/rest-api/reference/categories/
- Authentication: https://developer.wordpress.org/rest-api/using-the-rest-api/authentication/
- Application Passwords: https://developer.wordpress.org/advanced-administration/security/application-passwords/

## API基本情報

| 項目 | 値 |
| --- | --- |
| Base URL | `https://${CMS_HOST}/wp-json` |
| 投稿エンドポイント | `POST /wp/v2/posts` |
| カテゴリ取得エンドポイント | `GET /wp/v2/categories` |
| Content-Type | `application/json; charset=UTF-8` |
| 認証方式 | Basic認証（WordPress Application Password） |
| 通信方式 | HTTPSのみ |

## 認証
WordPress公式の Application Password を利用する。

HTTPヘッダー:

```http
Authorization: Basic base64(username:applicationPassword)
Content-Type: application/json; charset=UTF-8
```

設定値はソースコードや設計書に直接記載しない。
GitHub Actions / 実行環境の Secrets または環境変数から参照する。

想定環境変数:

| 環境変数 | 用途 |
| --- | --- |
| `CMS_HOST` | WordPressホスト名 |
| `CMS_USERNAME` | WordPress投稿ユーザー名 |
| `CMS_APP_PASSWORD` | WordPress Application Password |
| `POST_LIMIT` | 1回の処理で投稿する最大件数 |

## 通常コンテンツ投稿

WordPress投稿はCrawlerの同一Job・同一処理フロー内で実行する。`WordPressPostService` はCrawler内部でWordPress REST API呼び出しを担当するJavaクラスであり、独立サービス、独立Job、独立サブシステムではない。現行フローは `クロール -> SiteContents保存 -> 必要ならAI加工 -> WordPress media API登録 -> WordPress posts API投稿 -> cms_content_id / status更新` とする。

WordPress media を削除する運用では、DB record を先に直接削除してから physical file を `rm` する方式は禁止する。原則は WordPress 標準 media 削除で original file、generated thumbnail、metadata、DB record を同一処理で整合させ、その後に DB と physical file を検証する。attachment record が既に消えた orphan recovery のみ、baidu.tokyo 専用 cleanup wrapper と manifest dry-run/apply 手順を例外として使う。

### リクエスト

```http
POST https://${CMS_HOST}/wp-json/wp/v2/posts
Authorization: Basic base64(username:applicationPassword)
Content-Type: application/json; charset=UTF-8
```

### Body

```json
{
  "title": "記事タイトル",
  "content": "記事本文HTML",
  "status": "publish",
  "categories": [2]
}
```

### 項目マッピング

| WordPress項目 | 設定元 | 必須 | 備考 |
| --- | --- | --- | --- |
| `title` | `site_contents.title` | 必須 | 空の場合は投稿しない |
| `content` | `site_contents.contents` または `description` | 必須 | 契約状態に応じて本文を切り替える |
| `status` | 固定値 `publish` | 必須 | 下書き運用にする場合は `draft` に変更 |
| `categories` | `site_category.target_category` | 任意 | WordPressカテゴリIDを設定する |
| `featured_media` | WordPress media APIが返したmedia ID | 任意 | 元画像URLや独自DBのIDは設定しない |

本文の設定ルール:

1. `site_info.contract_type = NONE` の場合、`site_contents.description` を本文に使用する。
2. `description` が空の場合、`site_contents.title` を本文に使用する。
3. `site_info.contract_type >= 1` の場合、`site_contents.contents` を本文に使用する。

### 正常レスポンス
HTTPステータスが `200` または `201` の場合、投稿成功とする。

レスポンスの `id` を `site_contents.cms_content_id` に保存する。

```json
{
  "id": 123,
  "link": "https://example.com/20260611/123.html",
  "status": "publish"
}
```

### 異常レスポンス
HTTPステータスが `2xx` 以外、通信例外、JSON解析失敗、必須項目不足は投稿失敗とする。

投稿失敗時の処理:

1. 対象 `site_contents` の `process_status` を `9:失敗` に更新する。
2. warnログを出力する。
3. 例外を上位へ再throwしない。
4. 同一バッチ内の次レコード処理を継続する。

ログに出力する情報:

| 項目 | 出力 |
| --- | --- |
| `site_contents_id` | 出力する |
| `url` | 出力する |
| HTTPステータス | 取得できる場合のみ出力する |
| WordPressエラーコード | 取得できる場合のみ出力する |
| 認証情報 | 出力しない |
| 投稿本文 | 原則出力しない |

ログ例:

```text
warn CMS post failed. siteContentsId=123 url=https://example.com/news/1 status=401 code=rest_cannot_create
```

## リトライ方針
投稿APIのリトライは行わない。
エラー発生時は対象レコードを失敗扱いにしてスキップする。

理由:
- ユーザー要件として、エラー時はスキップしてwarnログのみでよい。
- WordPress側の認証・権限・入力不備エラーは即時リトライしても成功しない可能性が高い。
- 再処理が必要な場合は、DB上の `process_status` を運用で戻して再実行する。

## タイムアウト

| 項目 | 初期値 |
| --- | --- |
| 接続タイムアウト | 10秒 |
| 読み取りタイムアウト | 30秒 |

タイムアウト発生時も投稿失敗として扱い、warnログを出力して次レコードへ進む。

## 投稿件数制限
1回のバッチ実行で投稿する件数は `web-crawler.post-contents-limit-count` で制御する。
環境変数では `POST_LIMIT` を利用する。

## カテゴリ取得
WordPressカテゴリIDの確認には公式カテゴリAPIを使用する。

```http
GET https://${CMS_HOST}/wp-json/wp/v2/categories
```

`site_category.target_category` には、投稿先の WordPress カテゴリIDを保存する。
カテゴリ名やslugではなく、投稿APIの `categories` に指定可能な数値IDを保存する。

## 実装上の注意
- Application Password はログ、例外メッセージ、テスト出力に含めない。
- Basic認証はHTTPS前提でのみ使用する。
- WordPress投稿クライアントは、Crawler内部の専用Javaクラス `WordPressPostService` として責務を分ける。独立サービス化はしない。
- 投稿API失敗はジョブ全体の失敗にしない。
- ステータス更新ロジックの既存制約を守り、投稿失敗時は対象レコード単位で `FAIL` にする。

## 画像・アイキャッチ画像

`ArticleImagePolicy` は元記事から画像URL候補を選択・除外するCrawler内部ロジックであり、WordPress REST APIを呼び出さない。`site_contents.featured_image_url` は選択された元記事の画像URLを保持する項目で、WordPress側media IDを保持する項目ではない。AI加工で投稿用copyを作る場合も、このURLをcopyへ引き継ぐ。

画像登録にはWordPress公式REST APIのみを使用する。

```http
POST https://${CMS_HOST}/wp-json/wp/v2/media
Authorization: Basic base64(username:applicationPassword)
Content-Type: image/*
```

WordPressから返されたレスポンスの `id` をmedia IDとし、続く `POST /wp-json/wp/v2/posts` の `featured_media` に設定する。WordPress側media IDを管理する独自API、独自メディア管理機構、独自DBテーブルは追加しない。

投稿本文の画像は、記事本文として保存されたHTML内の画像のみをWordPressメディアにアップロードする。テンプレート由来の出典ロゴや `site_info.logo_url` はアップロード対象外とする。
テンプレート由来の出典ロゴは投稿本文のHTMLにも出力しない。

投稿リクエストの `featured_media` は任意項目とし、次のいずれかの場合のみ設定する。

- `site_contents.featured_image_url` が存在する場合、その画像をアップロードしたmedia idを設定する。
- 取得元が通常HTMLの場合、除外条件に該当しない本文中の最初の画像をアップロードしたmedia idを設定する。

取得元がWordPress REST APIで `site_contents.featured_image_url` が空の場合、本文中の画像をアイキャッチ画像として代用しない。

通常HTMLの `site_contents.featured_image_url` は、明示的なimage metadata、`og:image` / `twitter:image`、記事型JSON-LD、本文中の最初の有効画像の順で決定する。相対URL、protocol-relative URL、lazy-load属性、`srcset` / `picture` は取得元URLを基準にHTTP(S)の絶対URLへ解決する。

除外対象画像は、`site_info.logo_url` と一致する画像、URL/ファイル名/HTML属性が `news_*.png`、`logo`、`icon`、`avatar`、`sprite`、`tracking`、`pixel`、`spacer`、`placeholder`、`banner`、`ad` 等に該当する画像、1px以下と判断できる画像、HTTP(S)以外、不正URL、data URIとする。

同じ取得元画像を `featured_media` と本文画像に使う場合、1回のmedia upload結果を両方に利用する。featured imageのdownloadまたはuploadが失敗しても投稿本文作成は継続し、使用可能な本文画像がなければ `featured_media` を送信しない。

## 既存postのfeatured image repair

`REPAIR_CMS_CONTENT_ID` 指定時は、該当する既存postを維持したままfeatured imageだけを補正する。`featured_image_url` が空の場合は保存済み元記事URLを再解析し、通常の画像選定規則で得た元画像URLを `site_contents.featured_image_url` に保存する。

repairで使用するWordPress APIは次の公式REST APIだけである。

```http
GET  /wp-json/wp/v2/posts/{cms_content_id}?_fields=id,content,featured_media
GET  /wp-json/wp/v2/media?parent={cms_content_id}&per_page=100&_fields=id,source_url
POST /wp-json/wp/v2/media?post={cms_content_id}
POST /wp-json/wp/v2/posts/{cms_content_id}
```

既存postの `featured_media=0` かつ選定済み画像URLが有効な場合、mediaを登録し、そのmedia IDだけを既存post更新payloadの `featured_media` に指定する。title、content、categoriesは送信せず、post ID、URL、カテゴリを維持する。media登録後にpost更新が失敗した再実行では、同じparentのmediaを取得して元画像とbytesが一致するmedia IDを再利用する。独自media ID管理DBは追加しない。
