# web-crawler functional and master data specification

## 1. 位置づけ

この文書は、2026-08-21時点の `web-crawler` 実装と、2026-08-04に取得したConoHa MySQL実DBマスタ・外部ページ証跡を分けて扱う。特に `SiteInfoProcessPool`、並列制御、URL正規化はこの文書を正本とし、古い実DBsnapshotと現在の実装を混同しない。

優先順位は次の通り。

1. 現在の実DB
2. 現在の実装コード
3. 現在のテスト
4. 最新の設計資料
5. 古い設計資料・gap analysis

PR #34レビューで確定した本来の設計意図は、現行実装との差分を判断する際の正本とする。実DBや実装に意図と異なる状態があっても、現状を隠さず「現行」「差分」「次期要件」に分けて記録する。

古い資料は削除せず、次の分類で扱う。

| 資料 | 分類 | 理由 |
| --- | --- | --- |
| `docs/design/web-crawler-system-understanding.md` | 現在も有効 | 今回の調査結果で更新済み |
| `docs/02_batch.md` の状態遷移・process pool章 | 現在も有効 | 最新実装の所有権・timeout仕様と一致 |
| `docs/02_batch.md` のAIイベント集約章 | 一部古い/将来構想 | 実DB・Entityに該当テーブルなし |
| `docs/05_gap_analysis.md` | 履歴資料 | WordPress投稿未実装など古い前提を含む |
| `docs/08_system_test_spec_ai_off_yahoo_to_cms.md` | 一部有効 | システムテスト観点は有効だが現行DB/コード差分を要補正 |

## 2. 機能仕様

### 2.1 入力

| 入力 | 取得元 | 現行キー/カラム |
| --- | --- | --- |
| 対象サイト | DB | `site_info.delete_flg='0'`, `contents_type` |
| 対象カテゴリ | DB | `site_category.delete_flg='0'` |
| 一覧URL | DB | `category_list_url` 優先、空なら `category_url` |
| HTML selector | DB | `list_record_select_id`, `title_record_select_id`, `contents_url_selectId`, `body_select_id`, `more_body_select_id`, `more_body_select_txt` |
| 件数制限 | config/env | `CRAWL_PROCESS_LIMIT`, `CRAWL_CONTENT_LIMIT`, `POST_LIMIT` |
| CMS投稿制御 | config/env | `SKIP_CMS_POST`, `WP_BASE_URL`, `WP_USERNAME`, `WP_APPLICATION_PASSWORD`, `WP_POST_STATUS` |
| AI制御 | config/env | `AI_MODE`, `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` |

### 2.2 出力

| 出力 | 保存先 |
| --- | --- |
| 新規記事URL、title、description、contents、featured image候補 | `site_contents` |
| 記事処理状態 | `site_contents.process_status` |
| WordPress投稿ID | `site_contents.cms_content_id` |
| カテゴリ処理状態・owner・処理時刻 | `site_info_process_pool` |
| Job summary | ログ、Spring Batch ExitStatus |

### 2.3 Job / Step

現行実装（2026-08-21 category rotation反映後）:

- Job名: `crawlJob`
- Step名: `crawlStep`。対象poolがない場合は `emptyStep`。
- snapshot単位: run開始時にfilter済みの全対象poolを `site_category_id ASC` で固定する。
- 実行item: logical workerごとのimmutable category assignment。assignment内ではカテゴリを昇順に逐次処理する。
- chunk: `1`
- reader: `SynchronizedItemReader<ListItemReader<CategoryAssignment>>`
- processor: `CrawlerComponents.categoryAssignmentProcessor`。カテゴリごとに既存のclaim・parse・save・post・release処理を呼ぶ。
- writer: category処理はprocessor lifecycle内で完了するためno-op。
- logical worker上限: `CRAWL_PROCESS_LIMIT`。physical thread数は非空assignment数で、`min(対象カテゴリ数, CRAWL_PROCESS_LIMIT)`以下。
- Step transaction manager: `ResourcelessTransactionManager`
- DB更新: `SiteContentsService` の短い `@Transactional` メソッドで実行。

partitionは `base=floor(C/T)` とし、worker 1から`T-1`へ各`base`件、最後へ残り全部を割り当てる。assignment間のintersectionは空、unionはsnapshot全体である。dynamic work stealingは行わない。各カテゴリは処理直前に原子的にclaimし、owner/attempt一致でreleaseするため、同じsnapshotを見た別JVMがあっても同一カテゴリの同時所有はできない。

### 2.4 HTTP

HTML/REST取得は `ContentsParser` がJsoupで行う。

| 設定 | 既定値 |
| --- | --- |
| User-Agent | Chrome 125相当 |
| referrer | `https://news.yahoo.co.jp/` |
| timeout | `EXTERNAL_CONNECT_TIMEOUT_MS`、既定10000ms |
| retry | `EXTERNAL_CONNECT_RETRY_COUNT`、既定3 |
| REST JSON | Jsoup `ignoreContentType(true)` |

WordPress投稿は `WordPressPostService` が `RestTemplate` で行う。
これはCrawler内部のJavaクラスとしての責務分離であり、WordPress投稿を独立Job、独立サービス、独立サブシステムにするものではない。現行フローは `クロール -> SiteContents保存 -> 必要ならAI加工 -> WordPress media API登録 -> WordPress posts API投稿 -> cms_content_id / status更新` を維持する。

| API | 用途 |
| --- | --- |
| `POST /wp-json/wp/v2/posts` | 新規投稿 |
| `POST /wp-json/wp/v2/posts/{id}` | カテゴリ更新、画像ポリシー補修 |
| `POST /wp-json/wp/v2/media` | 画像アップロード |
| `GET /wp-json/wp/v2/posts/{id}` | 画像ポリシー補修確認 |
| `GET /wp-json/wp/v2/media/{id}` | アイキャッチ画像確認 |

### 2.5 URL正規化

現行実装で行うこと:

- Jsoupの `abs:href` / `absUrl` により相対URLを絶対URLへ変換する。
- 画像URLも `absUrl` を優先する。
- WordPress投稿先base URLはschemeがなければ `https://` を補い、末尾slashを除去する。

現行実装で行わないこと:

- canonical URLへの置換。
- tracking parameter除去。
- `http` から `https` への汎用変換。
- URL fragment除去。
- 配信元ページへのURL置換。

次期実装では、詳細レスポンスごとに次の値を個別に取得し、失わずに判定する。

| 値 | 意味 |
| --- | --- |
| `requested_url` | 一覧またはRESTレスポンスから取得し、HTTP要求に使用したURL |
| `redirected_url` | HTTPリダイレクトを追跡した最終URL。リダイレクトがなければnull |
| `canonical_url` | HTMLの `link[rel=canonical]` を絶対URL化し、信頼境界を通過した値 |
| `source_url` | 一覧またはAPIが示した取得元URL。Yahoo中間ページと配信元を区別するため保持 |
| `normalized_url` | 重複判定と `site_contents.url` 保存に使う正規化済み識別URL |

正規URLの候補順位は次の通りとする。実際にリダイレクトが発生した場合は最終リダイレクトURLを優先し、発生しなかった場合は有効なcanonical、`og:url`、requested URLの順に選ぶ。採用しなかった候補も監査・再判定用に保持する。

1. HTTPレスポンスの最終リダイレクトURL
2. HTMLの `<link rel="canonical" href="...">`
3. `meta[property="og:url"]`
4. 取得元のrequested URL

候補ごとにURIとしてparseし、HTTP(S)以外、hostなし、userinfoあり、不正なpercent encoding、空文字は拒否する。相対canonicalは最終レスポンスURLをbaseとして絶対URL化する。複数canonicalがある場合は先頭の有効候補だけを評価し、複数検出をwarnログへ残す。

正規化処理は次の順序で行う。

1. schemeとhostを小文字化する。
2. `http:80` と `https:443` のdefault portを除去する。
3. fragmentを除去する。
4. path内の連続slashを1つへ畳み、空pathを `/` とする。
5. percent encodingはUTF-8を前提に、unreserved文字をdecodeし、残る16進表記を大文字へ統一する。
6. `utm_*`, `gclid`, `fbclid`, `yclid` とサイトadapterで承認したtracking parameterを除去する。記事識別に必要なquery parameterは保持する。
7. root以外の末尾slashを除去する。ただしサイトadapterが末尾slashを識別子として扱う場合は保持する。
8. `http` から `https` への変更は、redirect、canonical、HSTS相当のサイト別承認でHTTPSが確認できた場合だけ行う。

AMP、pagination、Yahoo tracking URLはサイト別規則で扱う。AMPまたはpaginationページが同一記事の有効なcanonicalを示す場合はcanonical側へ寄せる。Yahoo tracking URLはリンク先を安全に展開できた場合だけ候補にし、任意のquery値を外部URLとして信用しない。

canonicalの信頼境界:

| 分類 | 判定 | 次期動作 |
| --- | --- | --- |
| same-origin | scheme正規化後のhostとportが一致 | 自動承認 |
| same-site | Public Suffix List基準のregistrable domainが一致 | サイト設定で許可された場合に承認 |
| approved external | サイト別allowlistに一致 | 承認し、理由をdebug/infoログへ残す |
| unapproved external | 上記以外の外部host | 拒否し、次順位へfallbackしてwarnログ |

CDN、広告、tracking host、明らかなcanonical設定ミスは外部canonicalとして自動承認しない。Yahoo!ニュースから配信元メディアへのexternal canonicalは人間判断が完了するまで既定拒否とし、Yahoo URLを識別URLとして維持する。同一企業の別サブドメインもsame-site設定がなければ自動承認しない。

重複判定は二段階で行う。一覧段階ではrequested URLの暫定normalized URLで同一バッチ内重複を除外し、詳細取得後は確定した `normalized_url_hash` で同一バッチと既存DBを再照合する。V8はURL由来値を分離して追加し、SHA-256 unique制約を最終防衛線とする。競合時は候補ごとの独立transactionだけをrollbackし、同一batchの他記事を継続する。

### 2.6 retry / skip / fallback

| 対象 | 現行動作 |
| --- | --- |
| 一覧HTTP失敗 | retry後にカテゴリ処理失敗、pool FAIL |
| 一覧0件 | pool FAIL |
| 詳細HTTP失敗 | 記事単位skip。全記事失敗ならpool FAIL |
| 本文selector不一致 | fallback selectorを試す。全て0件なら記事skip |
| AI設定不足 | 原文fallback、summaryにAI fallback加算 |
| AI API失敗 | 原文fallback、summaryにAI failure/fallback加算 |
| WordPress投稿失敗 | 対象contents FAIL、pool FAIL、後続は継続 |
| WordPressカテゴリ同期失敗 | pool FAIL、後続は継続 |
| owner不一致finish | pool状態を上書きしない |

## 3. データ仕様

### 3.1 Entityとテーブル

| Entity | Table | 主なカラム | 備考 |
| --- | --- | --- | --- |
| `SiteInfo` | `site_info` | `site_info_id`, `site_name`, `site_url`, `logo_url`, `contract_type`, `delete_flg`, `contents_type` | サイト単位の取得方式と権利区分 |
| `SiteCategory` | `site_category` | `site_category_id`, `site_info_id`, `category_url`, `category_list_url`, `cms_category_id`, `cms_target_category`, selector群 | カテゴリ単位の取得設定 |
| `SiteInfoProcessPool` | `site_info_process_pool` | `site_info_process_id`, `site_category_id`, `process_status`, `process_id`, `process_time` | カテゴリ処理リース |
| `SiteContents` | `site_contents` | `site_contents_id`, `url`, `title`, `description`, `contents`, `featured_image_url`, `site_categoy_id`, `process_status`, `cms_content_id` | `site_categoy_id` のtypoは現行互換 |

実DBに外部キー制約はない。`site_contents.url` はunique。

### 3.2 enum

| enum | DB値 | 意味 |
| --- | --- | --- |
| `ProcessStatus.NONE` | `1` | 未処理 |
| `ProcessStatus.PROCESSING` | `2` | 処理中 |
| `ProcessStatus.SUCCESS` | `0` | 成功 |
| `ProcessStatus.FAIL` | `9` | 失敗 |
| `DeleteFlg.OFF` | `0` | 有効 |
| `DeleteFlg.ON` | `1` | 削除 |
| `ContentsType.HTML` | `1` | HTMLクロール |
| `ContentsType.Wordpress` | `2` | WordPress REST |
| `ContentsType.X` | `3` | Enumのみ |
| `ContentsType.TIKTOK` | `4` | Enumのみ |
| `ContentsType.YOUTUBE_SHORT` | `5` | Enumのみ |

### 3.3 WordPress投稿内容

`ArticleImagePolicy` は元記事からアイキャッチ候補URLを判定し、`site_contents.featured_image_url` には元記事の画像URLを保存する。WordPress画像登録は `WordPressPostService` が公式 `POST /wp-json/wp/v2/media` で行い、返却されたmedia IDを `POST /wp-json/wp/v2/posts` の `featured_media` に設定する。AI加工時も元画像URLを投稿用copyへ引き継ぐ。WordPress側media IDを永続化する独自DB機構は追加しない。

| 条件 | 投稿本文 |
| --- | --- |
| `contract_type` 空または `0` | `description` 優先。空なら `title` |
| `contract_type >= 1` | `contents` 優先。空なら `description`、さらに空なら `title` |

投稿カテゴリは `cms_category_id` を先頭に入れ、`cms_target_category` があればCSVとして追加する。`cms_target_category` が空の場合だけ `target_category` を互換用に使う。数値に変換できない値はskipする。

### 3.4 SiteInfoProcessPoolの設計意図と次期実装要件

`SiteInfoProcessPool` は処理履歴テーブルではない。カテゴリ単位の並列ワーカーを管理する永続プールであり、1枠が同時に1つのカテゴリワーカーへleaseされる。

```text
SiteInfo
  └─ SiteCategory / SiteCategoryInfo
       └─ SiteInfoProcessPool 1枠
            └─ 1 worker（claim中だけ所有）
```

| 観点 | 本来の設計意図 | 現行実装 | 差分 | 次期実装要件 |
| --- | --- | --- | --- | --- |
| 役割 | カテゴリワーカーの永続実行枠 | カテゴリ単位のclaimと完了状態を保持 | SUCCESS/FAILが履歴状態にも見える | 利用可否・所有権を主責務と明記 |
| 容量 | 有効poolは処理対象容量、`CRAWL_PROCESS_LIMIT`はlogical worker上限 | 全対象poolを固定partitionへ割当 | thread数と処理カテゴリ数を分離 | 値を増やさず1 workerが複数カテゴリを完走可能 |
| 重複防止 | 同じカテゴリは同時に1workerだけ | 条件付きUPDATEで同一行の競合を防止 | categoryごとの一意性はDB未保証 | `site_category_id` の一意性確認後にunique制約を追加 |
| 所有権 | 複数JVMで衝突しないownerと世代を保持 | Job execution ID由来の `process_id` | instance識別、世代、heartbeatなし | owner instance、worker UUID、attempt/lease tokenを保持 |
| 生存確認 | heartbeatで稼働中leaseを識別 | claim時の `process_time` のみ | 長時間処理をstale誤判定し得る | `claimed_at` と `heartbeat_at` を分離し定期更新 |
| 解放 | 全終了経路で必ずAVAILABLEへ戻す | SUCCESS/FAILへ更新し候補化 | `process_id`が残り、release概念が曖昧 | owner/attempt一致のCASでownerをclearしてAVAILABLEへ戻す |
| 異常回収 | stale leaseを安全に回収 | timeout済みPROCESSINGを再claim | 監査、heartbeat、旧owner fencingが不足 | stale回収ログ、attempt増分、旧ownerの更新拒否 |

#### 3.4.1 並列数設定の移行

| 設定 | 現在 | 分類 | 次期方針 |
| --- | --- | --- | --- |
| `web-crawler.max-concurrency` / `MAX_CONCURRENCY` | 旧実装のthread数上限 | 廃止済み | 使用しない |
| `web-crawler.crawl-process-limit-count` / `CRAWL_PROCESS_LIMIT` | logical worker上限 | 確定 | 対象カテゴリ件数は制限せず、thread数は値を超えない。workflow実効値1なら1 workerが全対象を逐次処理 |
| `CRAWL_CONTENT_LIMIT` | カテゴリ内の記事詳細取得上限 | 継続 | category単位。worker数と無関係 |
| `POST_LIMIT` | WordPress新規投稿attemptのrun-wide上限 | 確定 | atomic reservation。失敗/部分成功でも返却せず、worker数倍にしない |
| datasource `maximum-pool-size` / `DB_MAX_POOL_SIZE` | DB connection pool上限 | 継続 | カテゴリworker数ではない。DB負荷設計として別管理 |

Java/Spring fallbackの `10` はworkflow外の安全値として維持する。scheduled workflowはPR #59の `CRAWL_PROCESS_LIMIT=1`、`CRAWL_CONTENT_LIMIT=200`、`POST_LIMIT=1000`を供給し、thread数を増やさない。

2026-08-05のソース・DDL確認では、`site_info` と `site_category` にカテゴリworkerの固定並列数カラムは見つからなかった。将来追加せず、容量変更は有効なpool枠の管理として行う。

#### 3.4.2 状態と所有情報

次期の概念状態は `AVAILABLE -> CLAIMED/PROCESSING -> AVAILABLE` とする。`RELEASING` は解放処理中の概念状態であり、releaseが単一UPDATEで完結する限りDBへ永続化しない。処理結果のSUCCESS/FAILはJob summaryまたは別の実行履歴へ分離する。

現行enumを即時変更せず、移行中は `NONE`、`SUCCESS`、`FAIL` をAVAILABLE相当、`PROCESSING` をCLAIMED相当として扱う。次期実装PRでDDL、データ移行、rollback SQLを示してから状態モデルを変更する。

次期poolが保持する情報:

| 項目 | 要件 |
| --- | --- |
| `site_info_id` | category経由で追跡可能。非正規化する場合は整合性制約を追加 |
| `site_category_id` | lease対象。原則1カテゴリ1枠でunique |
| `status` | AVAILABLEまたはPROCESSING |
| `process_id` / `worker_id` | `hostname + JVM process ID + UUID` 等、複数JVMで一意なowner |
| `owner_instance` | server/JVM単位の監査識別子 |
| `claimed_at` | 現leaseの取得時刻。heartbeatで上書きしない |
| `heartbeat_at` | ownerが生存を通知した最終時刻 |
| `updated_at` | DB行の最終更新時刻 |
| `attempt` / `lease_token` | claimごとに単調増加するfencing token |
| `job_execution_id` | Spring Batch実行との追跡。必須化はmigration設計で判断 |

#### 3.4.3 原子的claimの採用方式

| 候補 | 評価 |
| --- | --- |
| `SELECT ... FOR UPDATE SKIP LOCKED` | 複数行配布には有効だが、実DB MariaDB 10.0.19での構文・挙動を本番相当環境で確認できていない。長いtransactionを避ける方針とも調整が必要なため現時点では採用しない |
| 条件付きUPDATE（compare-and-set） | 現行方式を拡張でき、短いtransactionと更新件数で所有権を確定できる。採用 |
| versionカラムによる楽観ロック | Entity更新には有効だが、候補配布とstale回収に追加制御が必要。補助策として将来検討 |
| ownerと更新件数による確認 | 条件付きUPDATEの必須要素として採用 |

claimは、`status=AVAILABLE` またはowner確認済みのstale条件をWHERE句に含む単一UPDATEで行う。UPDATEはowner、owner instance、claimed/heartbeat時刻を設定し、attemptを1増やす。更新件数1件だけを成功とし、0件は競合負けとしてワーカーを開始しない。1件超はデータ不整合としてJobを停止する。

候補一覧の取得は所有権を与えない。claim成功後に返したownerとattemptを再読込し、両方が一致したleaseだけをworkerへ渡す。複数JVMが同じ候補を読んでも、同じ枠を同時所有できない。

#### 3.4.4 release保証

processor/writer内の通常完了更新だけにreleaseを依存させない。カテゴリ処理の最外周でleaseを管理し、概念上は次を満たす。

```java
ProcessPoolLease lease = processPool.claim(category);
try {
    processCategory(category);
} finally {
    processPool.release(lease);
}
```

Spring Batchのchunk/listener境界で単純な `finally` を置けない場合は、ItemProcessListener、StepExecutionListener、JobExecutionListenerとアプリケーション終了hookを組み合わせてもよい。ただし通常経路のreleaseはowner、attempt、`PROCESSING` をWHERE条件にした単一CAS UPDATEとし、`status=AVAILABLE`、owner/heartbeatをnullへ更新する。更新件数0は二重releaseまたはowner喪失として安全に無視し、監査ログを残す。

正常終了、記事0件、一覧/詳細HTTP失敗、parser/DB/AI/WordPress例外、timeout、skip上限超過、`RuntimeException`、`InterruptedException`、Job停止、通常のアプリ終了ではreleaseを試行する。`InterruptedException` は割り込み状態を復元してからreleaseする。JVM強制終了、OS障害、電源断はfinallyを保証できないためstale回収で復旧する。

古いownerは、新しいownerのowner IDとattemptに一致しないため、heartbeat、release、完了結果のいずれも更新できない。releaseは冪等で、同じleaseの二重releaseが新しいleaseへ影響してはならない。

#### 3.4.5 heartbeatとstale回収

現行 `processing-timeout-minutes=60` はclaim時刻から判定する暫定互換値であり、根拠が確定した最終lease timeoutではない。次期実装では `lease_timeout` と `heartbeat_interval` を分離し、次を満たす値を本番相当の最大カテゴリ処理時間とHTTP/AI/WordPress timeoutから決める。

- `heartbeat_interval > 0`
- `heartbeat_interval <= lease_timeout / 3`
- `lease_timeout` は、1回の外部呼び出しがretryを含めて占有し得る最大時間より長い
- 設定不足、0、負数、または上記関係を満たさない場合は起動時にfail fast

各JVMは1つのschedulerで、そのinstanceが所有する全leaseのheartbeatを一括更新する。更新条件はownerとattempt一致とし、外部HTTP待機中もschedulerが独立して更新できる構造にする。heartbeat更新が連続失敗したworkerは新しい外部副作用を開始せず、所有権再確認後に安全停止する。

stale条件は `now - heartbeat_at > lease_timeout` とする。heartbeat導入前の移行行だけは `process_time` をfallbackに使う。回収は、Job開始前のstale走査とclaim時のstale CASを組み合わせる。独立scheduled watchdogは常時Jobが動く運用になった時点で追加検討し、初期実装では回収経路を増やさない。

回収時はpool ID、category ID、旧owner、旧attempt、最終heartbeat、stale経過時間、新ownerを構造化ログへ残し、attemptを増やす。stale判定後でもUPDATE時にowner、attempt、heartbeatを再確認し、heartbeatが進んだ枠は奪わない。回収後の処理は通常claimと同じretry規則に従う。

#### 3.4.6 停止要因ごとの期待動作

| 事象 | timeout/retry | leaseへの影響 | 再実行 |
| --- | --- | --- | --- |
| DB deadlock | DBが選んだtransactionをrollback。限定回数・jitter付きretry | claim/releaseの成否を更新件数と再読込で確認 | retry枯渇時はworker未開始またはstale回収 |
| DB lock wait timeout | 短いCASを限定retry | owner未確定ならworker開始禁止 | 次回claim可 |
| stale lease / orphaned pool | heartbeat timeoutで回収 | owner/attemptをfenceして新leaseへ | 可 |
| thread hang | heartbeat schedulerは継続し得るため、worker処理deadlineも別途監視 | deadline超過でinterrupt・release試行 | 強制停止時はstale回収 |
| external HTTP hang | 接続/読取timeoutとretry上限 | finallyでrelease | 可 |
| WordPress API hang | client timeoutと投稿冪等性確認 | finallyでrelease | 投稿重複防止後に可 |
| AI API hang | client timeout、原文fallback方針 | 処理継続後release | 可 |

DBエンジンのdeadlockと、解放されず永久に利用不能になるstale leaseは別の障害として計測・通知する。

## 4. サイト別仕様

### 4.1 Yahoo!ニュース

| 項目 | 現行仕様 |
| --- | --- |
| 目的 | Yahoo!ニュースのトピックス一覧から記事候補を取り込み、WordPressカテゴリへ投稿する |
| 取得方式 | `contents_type=1` HTML |
| 一覧URL | `https://news.yahoo.co.jp/topics/{local,domestic,world,business,entertainment,sports,it,science}` |
| 一覧抽出 | DB selector後、Yahoo fallback selector |
| 詳細抽出 | DB body selector後、`article p` 等へfallback |
| title | 一覧レコード内のDB selector後、pickup link/textへfallback |
| URL | `contents_url_selectId` 後、`a[href*=/pickup/]` fallback |
| description | 詳細ページのmeta description系 |
| 画像 | `image_src` / `itemprop=image`、`og:image` / `twitter:image`、記事型JSON-LD、選択本文内画像、article内画像の順で最初の有効候補を `featured_image_url` に保存。相対URL、lazy-load、`srcset` / `picture` を解決し、ロゴ/広告/tracking/極小/UI画像等を除外 |
| 日時 | 現行保存カラム・parse実装なし |
| ページング | 現行実装なし |
| 除外 | 空title/url、200字超title、複数日付らしきtitle、画像ファイル名による除外 |
| Yahoo中間ページ | pickupページを記事URLとして扱う |
| 配信元ページ | 「記事全文を読む」リンクは取得して本文を取り直す可能性あり。配信元へ常に遷移する仕様ではない |
| WordPress投稿内容 | `contract_type=0` のため本文はdescriptionまたはtitle。投稿本文に画像が含まれるかにかかわらず、`featured_image_url` の元画像をmedia APIへuploadし、成功時に `featured_media` を設定 |
| 既知不備 | 国内重複、旧selector、URL selector列ズレ、tracking/canonical未正規化 |

### 4.2 happinesea.com

| 項目 | 現行仕様 |
| --- | --- |
| 目的 | happinesea.comのWordPress投稿を取り込み、baidu.tokyo側カテゴリ8へ投稿する |
| 取得方式 | `contents_type=2` WordPress REST |
| 設定URL | `https://happinesea.com/category/news` |
| 実API | `https://happinesea.com/wp-json/wp/v2/posts?per_page=20&_embed=1` |
| title | `title.rendered` をHTML除去 |
| URL | `link`、なければ `guid.rendered` |
| description | `excerpt.rendered` をHTML除去 |
| contents | `content.rendered` |
| featured image | `_embedded.wp:featuredmedia[0].source_url`、なければmedia API |
| published/modified date | RESTレスポンスにあるが現行保存カラムなし |
| author/tags/categories | RESTレスポンスにあるが現行保存カラムなし |
| pagination | APIは `X-WP-TotalPages` を返すが現行実装は1ページのみ |
| HTML fallback | 現行実装なし。HTML selector設定はWordPress取得では使われない |
| 既知不備 | RESTカテゴリ限定なし、pagination未実装 |

## 5. シーケンス別仕様

### 5.1 正常系

現行実装は次の通り。

1. active categoryにpoolがなければ `NONE` poolを作成する。
2. `NONE` / `FAIL` / `SUCCESS` / timeout `PROCESSING` poolを候補にする。
3. 条件付きUPDATEでownerを取得する。
4. 一覧をGETし、記事候補を抽出する。
5. 詳細またはREST本文を取得する。
6. URL未登録分だけ `site_contents` へ保存する。
7. `SKIP_CMS_POST=true` ならpoolをSUCCESSにする。
8. `SKIP_CMS_POST=false` なら既存CMSカテゴリ同期と新規投稿を行う。
9. 投稿成功時は `cms_content_id` と `SUCCESS` を保存する。
10. poolをSUCCESSにする。

run開始時に候補を固定partitionへ割り当てる。各workerは担当カテゴリだけを昇順に処理し、カテゴリごとにclaim、heartbeat、一覧・詳細取得、[2.5 URL正規化](#25-url正規化)の二段階重複判定、保存、必要なCMS処理、owner/attempt付きreleaseを完了してから次へ進む。claim競合負けはskipし、別workerへ再割当しない。

### 5.2 一覧取得失敗

retryを使い切った後、カテゴリ処理を例外扱いにし、crawl failureを加算してpoolをFAILにする。

### 5.3 詳細取得失敗

記事単位でwarnログを出してskipする。全記事が詳細取得できなければpoolをFAILにする。

### 5.4 重複URL

現行は、同一バッチ内の `seenUrls`、既存DBの `findAllByUrlIn`、`site_contents.url` unique indexで取得元URLの重複を防ぐ。次期実装は一覧段階の暫定normalized URLと詳細取得後の確定normalized URLで二度照合し、canonical確定後に既存行へ衝突した場合もinsertしない。

### 5.5 AI失敗で原文fallback

`AI_MODE` が `deepseek` または `openai` でも設定不足・API失敗・不正応答なら、DBの元contentsを投稿対象にして処理を継続する。

### 5.6 WordPress投稿失敗

対象記事をFAILにし、WordPress failure summaryを加算する。poolはFAILになり、他poolの処理は継続する。

### 5.7 process pool timeout後再実行

現行は `process_time < now - processing-timeout-minutes` の `PROCESSING` を再claimし、新ownerが `process_id` を上書きする。次期実装は `heartbeat_at` と `lease_timeout` で判定し、ownerとattemptをCAS条件に含める。古いownerのheartbeat、release、完了更新はすべて0件更新となる。

### 5.8 同一カテゴリ並列取得競合

複数スレッド/JVMが同じpoolを候補として見ても、条件付きUPDATEで1件だけclaimに成功する。競合負けは解析せず、更新件数0としてworkerを起動しない。`site_category_id` の重複pool行がないことを移行前SQLで確認し、0件の場合だけunique制約を追加する。

### 5.9 本番E2EとActions停止期間

2026-08のGitHub Actions quota停止期間はhosted validationを実行しない。local Sandboxでunit/Spring/full test、`bootJar`、静的workflow検証を行い、production接続はread-only preflight後に次の順で実施する。

1. Gate 1: 1カテゴリ、`CRAWL_CONTENT_LIMIT=1`、`POST_LIMIT=1`。
2. Gate 2: 少数カテゴリ、1 worker、上限5から20で同一workerの逐次実行を確認。
3. Gate 3: 事前SELECTで対象数・未処理数・予想投稿数が安全な場合だけ200/1000相当を1回実行。

target host不一致、想定外の大量未処理、重複増加、連続5xx、DB不安定、無限loop、上限超過、category ownership重複、baidu.tokyo以外へのwriteを検出した場合は即停止する。正常投稿は正式コンテンツとして残し、テスト後に削除しない。

2026-09のquota refresh後に、5回/日の実時刻、200/1000の実効値、runner時間、Actions minutes、処理カテゴリ/記事数、assignment、Batch完了、WordPress投稿を別のpost-release verificationとして確認する。

## 6. 人間判断事項

- Yahoo!ニュースで配信元ページ本文まで取得するか、Yahooページ上のdescription/titleのみ扱うか。
- Yahoo!ニュースから配信元メディアへのexternal canonicalを承認し、配信元URLを記事識別の正本にするか。決定までは拒否する。
- Yahoo!ニュースの原文保存範囲、WordPress表示範囲、削除依頼対応。
- 本番schema authorityをFlywayに戻すか、guarded SQL initを正本にするか。
- V6相当のDB差分をいつ適用するか。
- pool状態をAVAILABLE/PROCESSINGへ移行し、実行結果履歴をどこへ保存するか。
- 実測処理時間に基づく `lease_timeout` と `heartbeat_interval` の具体値。
- `crawl-process-limit-count` をsmoke用件数制限として残すか、pool容量へ完全統合するか。
- 国内カテゴリ重複をどちらへ統一するか。
- `contents_url_selectId` と `contents_url_select_id` の統一方針。
- happinesea.comをRESTカテゴリID付きURLへ限定するか。
