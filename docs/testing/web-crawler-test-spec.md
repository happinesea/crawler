# web-crawler test specification

## 1. 目的

この文書は、`web-crawler` の現行仕様とPR #34で確定した次期process pool/canonical URL仕様を、将来Codexがテストコードへ落とし込める粒度で整理する。通常CIは外部サイト、WordPress本番、AI本番API、ConoHa本番DBへ依存しない。ライブサイト確認や本番前smoke testは通常CIと分離する。

次期仕様の期待値は[機能・マスタ仕様](../design/web-crawler-master-data-spec.md)を正本とする。以下で「次期」と記載したケースは、このPRで実装済みという意味ではなく、次の実装PRの受入条件である。

## 2. テストレベル

| レベル | 目的 | 依存の扱い |
| --- | --- | --- |
| 単体テスト | parser、URL、カテゴリ解決、AI fallback、投稿body mapping | fixtureとmock |
| Repositoryテスト | JPA query、状態候補、unique URL、H2互換 | H2。MySQL固有は別枠 |
| Service結合テスト | DB状態更新、CMS投稿成功/失敗、pool完了 | H2 + mock service |
| Spring Batch Job/Stepテスト | chunk(1)、summary、ExitStatus、競合skip | H2 + mock parser/CMS |
| MySQL統合テスト | MariaDBのDDL、claim更新、timeout、unique制約 | 本番以外のMySQL/MariaDB |
| WordPress API契約テスト | request/response mapping、status code、media upload | MockRestServiceServerまたはローカルWP |
| HTTP parser回帰テスト | Yahoo/happinesea fixtureで構造変化に強いか | 縮小fixture |
| 本番前smoke test | 実DB、実サイト、WordPress投稿の最小確認 | 明示承認。通常CI外 |

## 3. 既存自動テストとCI

- CI: `.circleci/config.yml` の `web-crawler-tests` job
- 実行: `tools/ci/run-web-crawler-tests.sh`
- Java: Temurin 17
- レポート: `build/reports/tests/test`, `build/test-results/test`
- test profile: H2、`ddl-auto=create`、Flyway disabled、`SKIP_CMS_POST=true`
- 既存mock: `ContentsParserTest` はJsoup static mock、`WordPressPostServiceTest` と `OpenAiCompatibleAiClientTest` は `MockRestServiceServer`、ServiceはMockitoBeanあり。

通常CIへ入れないもの:

- Yahoo!ニュースやhappinesea.comへのライブHTTP。
- WordPress本番投稿。
- AI本番API呼び出し。
- ConoHa本番DB更新またはFlyway実行。

## 4. Fixture方針

- 第三者記事本文を大量保存しない。
- 実HTMLを使う場合は、必要なDOM構造、meta、link、画像、本文1-2文程度へ縮小する。
- Yahoo fixtureは広告リンク、関連記事、pickupリンク、more bodyリンク、description metaを再現する。
- happinesea fixtureはWordPress REST JSONを中心にし、HTML fixtureはfallback採用時だけ使う。
- fixture内URLは `https://news.example/`、`https://happinesea.example/` 等へ置換してよい。

## 5. 単体テスト仕様

| ID | 対象 | 入力 | 期待結果 |
| --- | --- | --- | --- |
| UT-URL-001 | 相対URL変換 | base URIつきHTML、`/pickup/1` | 絶対URLになる |
| UT-URL-002 | URL selector fallback | `contents_url_selectId` 空、`a[href*=/pickup/]` あり | pickup URLを抽出 |
| UT-URL-003 | tracking parameter | `?utm_source=x` 付きURL | 現行は除去しない。次期期待値はCAN-007で定義 |
| UT-TITLE-001 | title selector | DB selector一致 | title取得 |
| UT-TITLE-002 | title fallback | selector不一致、pickup link textあり | fallbackでtitle取得 |
| UT-TITLE-003 | title過剰連結 | 200字超または複数日付混入 | title不採用 |
| UT-DESC-001 | description meta | `meta[name=description]` | description取得 |
| UT-DESC-002 | og fallback | name descriptionなし、ogあり | og description取得 |
| UT-BODY-001 | body selector | DB body selector一致 | contentsへouterHTML保存 |
| UT-BODY-002 | body fallback | `.article_body` 0件、`article p`あり | fallbackで本文取得 |
| UT-BODY-003 | more body | more link文言一致 | more URLへ遷移しmore selector本文を採用 |
| UT-IMG-001 | capture image | article内先頭画像あり | 本文に画像候補を追加 |
| UT-IMG-002 | image absolute | 相対画像URL | 絶対URLへ変換 |
| UT-IMG-003 | normal body image | 通常HTML本文に相対`img[src]` | 絶対URLを`featured_image_url`へ保存 |
| UT-IMG-004 | lazy-load image | data URIまたは空`src`、有効`data-src` | lazy-load URLを候補に採用 |
| UT-IMG-005 | srcset / picture | 複数descriptorの`srcset`または`picture source` | 有効な高解像度候補を絶対URL化 |
| UT-IMG-006 | excluded first images | tracking、1px、logo等の後に通常画像 | 除外候補をskipして次の画像を採用 |
| UT-IMG-007 | no image | 本文・metadataに画像なし | `featured_image_url`なしで本文取得成功 |
| UT-IMG-008 | metadata priority | `image_src`、`og:image`、JSON-LD、本文画像が共存 | 定義した優先順位で1件を採用 |
| UT-IMG-009 | Yahoo regression fixture | 2026-08-13事象相当の最小HTML | `newsatcl-pctr.c.yimg.jp`画像を抽出し本文画像も保持 |
| UT-WP-001 | WordPress REST list | posts JSON | title/link/content/excerptを抽出 |
| UT-WP-002 | `_embed` media | embedded featured mediaあり | `featured_image_url` 保存 |
| UT-WP-003 | media fallback | `featured_media` idのみ | media APIからsource_url取得 |
| UT-WP-004 | REST content欠落 | title/linkあり、content空 | 対象外としてskip |
| UT-AI-001 | `AI_MODE=off` | 任意contents | AI clientを呼ばず同一contents |
| UT-AI-002 | unsupported mode | `AI_MODE=foo` | 原文fallback |
| UT-AI-003 | API失敗 | client例外 | 原文fallback、failure/fallback加算 |
| UT-AI-004 | AI post copy | `featured_image_url`あり、AI成功 | 分析後copyにも画像URLを保持 |
| UT-CMS-001 | contract NONE | descriptionあり | 投稿bodyはdescription |
| UT-CMS-002 | contract NONE descriptionなし | titleあり | 投稿bodyはtitle |
| UT-CMS-003 | contract >=1 | contentsあり | 投稿bodyはcontents |
| UT-CMS-004 | category CSV | `cms_category_id=2`, `cms_target_category=2,4` | categories `[2,4]` |
| UT-CMS-005 | invalid category | `cms_target_category=a,4` | `a` skip、4採用 |
| UT-CMS-006 | image exclusion | logo/news_*.png/ad/pixel | media uploadせず本文から除外 |
| UT-CMS-007 | source WordPress no featured | body imgのみ | featured_media未設定 |
| UT-SUMMARY-001 | summary counter | failuresあり | 後続処理は継続し、最終Jobは`FAILED` |

### 5.1 canonical URL・正規化テスト（次期）

通常CIではHTTP redirect chainとHTMLをfixture化し、外部DNSへ接続しない。`DB状態` の「保存」は将来カラムを示し、現行1カラム移行中は同じ意味をテスト用DTOで検証する。

| ID | 前提 | 入力 | 期待結果 | DB状態 | ログ | 再実行可否 |
| --- | --- | --- | --- | --- | --- | --- |
| CAN-001 | redirectなし | canonicalなし、ogなし | requested URLを採用 | requested/normalized保存 | fallbackをdebug | 可、同一URLは重複skip |
| CAN-002 | redirectなし | absolute canonical | same-originならcanonical採用 | canonical/normalized保存 | 採用理由 | 可 |
| CAN-003 | redirectなし | relative canonical `/article/1` | response URL基準で絶対化して採用 | 絶対canonical保存 | 採用理由 | 可 |
| CAN-004 | redirectあり | requested A -> final B、canonical C | 優先順位によりB採用、Cも候補として保持 | redirected/ canonical/normalized保存 | redirect優先 | 可 |
| CAN-005 | canonicalなし | 有効な `og:url` | og URL採用 | normalized保存 | og fallback | 可 |
| CAN-006 | 任意候補 | `#section` 付き | fragment除去 | fragmentなしnormalized | なし | 可 |
| CAN-007 | tracking除去規則あり | `utm_*`, `yclid`, `gclid`, `fbclid` | trackingだけ除去、業務query保持 | normalized保存 | 除去keyを秘密値なしでdebug | 可 |
| CAN-008 | default port | `http:80` / `https:443` | default port除去 | normalized保存 | なし | 可 |
| CAN-009 | 大文字scheme/host | `HTTPS://NEWS.EXAMPLE/A` | scheme/host小文字化 | normalized保存 | なし | 可 |
| CAN-010 | 同一記事 | host大小、fragment、tracking差の2 URL | 同じnormalized URL | 1行だけ存在 | duplicate count加算 | 可 |
| CAN-011 | same-origin | 同一origin canonical | 自動承認 | canonical採用 | classification=same-origin | 可 |
| CAN-012 | same-site許可 | `news.example.com` -> `www.example.com` | PSL判定とサイト設定により承認 | canonical採用 | classification=same-site | 可 |
| CAN-013 | allowlistあり | approved external canonical | 承認 | canonical採用 | classification=approved-external | 可 |
| CAN-014 | allowlistなし | unapproved external canonical | 拒否して次順位へfallback | 不採用canonicalは監査用保持 | warn、hostと理由 | 可 |
| CAN-015 | URI parser | hostなし、userinfo、壊れたescape | 候補拒否して次順位へfallback | 不正値を識別URLにしない | warn、秘密値なし | 可 |
| CAN-016 | canonical elementあり | 空白または空href | 候補なし扱い | canonical null | debug | 可 |
| CAN-017 | canonical複数 | 先頭不正、2番目有効 | 先頭の有効候補を採用 | 採用値保存 | 複数検出warn | 可 |
| CAN-018 | AMPページ | same-origin main canonical | main URLへ正規化 | main normalized保存 | AMP canonical採用 | 可 |
| CAN-019 | Yahoo fixture | Yahooから配信元external canonical | 人間判断前の既定拒否、Yahoo URL採用 | source/canonical候補を分離 | policy拒否warn | 可 |
| CAN-020 | happinesea fixture | WordPress same-origin canonical | canonical採用 | canonical/normalized保存 | classification=same-origin | 可 |
| CAN-021 | 一覧で暫定照合済み | 詳細で別canonicalへ確定 | 確定normalized URLで同一batchを再照合 | 重複ならinsertなし | canonical duplicate | 可 |
| CAN-022 | 既存DB行あり | 新URLのcanonicalが既存normalizedと一致 | 既存行と衝突し新規insertなし | 既存1行を維持 | DB duplicate count | 可 |
| CAN-023 | path規則 | 連続slash、非root末尾slash、percent表記差 | 規則どおり同一normalized URL | 1行だけ存在 | なし | 可 |
| CAN-024 | HTTP URL | redirect/canonicalでHTTPS確認済み/未確認 | 確認済みだけHTTPS化、未確認はHTTP維持 | 選択結果保存 | upgrade理由 | 可 |
| CAN-025 | pagination URL | same-origin canonicalが本文1ページ目 | canonicalへ寄せる | main normalized保存 | pagination canonical採用 | 可 |

## 6. Yahoo!ニュース parser回帰テスト

| ID | Fixture | 期待結果 |
| --- | --- | --- |
| YH-001 | 正常な一覧、25件のpickup link | 1件以上のURL/title抽出 |
| YH-002 | 広告リンク混在 | `href` がpickupでない広告を除外 |
| YH-003 | 関連記事混在 | title過剰連結を除外 |
| YH-004 | URLパラメータ付き | 現行はそのまま保存。次期はCAN-007の規則でtrackingだけ除去 |
| YH-005 | リダイレクトURL | 現行は抽出URLを保存。次期はCAN-004の規則で最終redirect URLを優先 |
| YH-006 | 記事本文あり | `article p` fallbackで本文取得 |
| YH-007 | descriptionのみ | bodyが空なら記事skip。descriptionのみ保存は未決定 |
| YH-008 | 配信元リンクあり | more body文言一致時だけ遷移 |
| YH-009 | 記事削除 | NotFound扱いで記事skip |
| YH-010 | selector不一致 | fallback selectorが機能 |
| YH-011 | HTML構造変更 | 全selector 0件ならpool FAIL |
| YH-012 | title欠落 | 候補skip |
| YH-013 | 画像なし | featured_mediaなしで投稿可能 |

## 7. happinesea.comテスト

| ID | Fixture | 期待結果 |
| --- | --- | --- |
| HP-REST-001 | REST posts array | `SiteContents`候補生成 |
| HP-REST-002 | `X-WP-TotalPages=3` | `wordpress-source.max-pages` の範囲で3ページを取得 |
| HP-REST-003 | `_embed`あり | featured image取得 |
| HP-REST-004 | `_embed`なし、featured_mediaあり | media API fallback |
| HP-REST-005 | excerptなし | description null許容 |
| HP-REST-006 | contentなし | 対象skip |
| HP-REST-007 | 更新記事 | 現行はURL重複で再保存しない。更新検知未実装 |
| HP-REST-008 | 削除記事 | 現行は削除検知なし |
| HP-REST-009 | API 500/timeout | retry後カテゴリFAIL |
| HP-REST-010 | `source_category_id`未設定、カテゴリURLが`/category/news` | REST category slugをIDへ解決し、posts APIへ`categories`を付与 |
| HP-REST-011 | category slugをRESTで解決できない | 無条件posts取得を行わずカテゴリ処理失敗 |
| HP-HTML-001 | HTML一覧 | 現行contents_type=2ではHTML selectorを使わない |

## 8. 状態遷移テスト

次のSTケースは現行のSUCCESS/FAIL型poolを対象とする。次期AVAILABLE型leaseの受入条件は8.1から8.4に定義する。

| ID | 初期状態 | イベント | 期待状態 | DB更新 | ログ/ExitStatus | 再実行 |
| --- | --- | --- | --- | --- | --- | --- |
| ST-POOL-001 | `NONE` | claim成功 | `PROCESSING` | owner/time更新 | 継続 | timeoutまで不可 |
| ST-POOL-002 | `FAIL` | claim成功 | `PROCESSING` | owner/time更新 | 継続 | 可 |
| ST-POOL-003 | `SUCCESS` | claim成功 | `PROCESSING` | owner/time更新 | 継続 | 可 |
| ST-POOL-004 | stale `PROCESSING` | claim成功 | `PROCESSING` | owner上書き | 継続 | 可 |
| ST-POOL-005 | fresh `PROCESSING` | claim試行 | 変更なし | update 0 | skip log | 不可 |
| ST-POOL-006 | `PROCESSING` | crawl+CMS成功 | `SUCCESS` | owner一致finish | completed | 可 |
| ST-POOL-007 | `PROCESSING` | crawl失敗 | `FAIL` | owner一致finish | 後続カテゴリ継続、最終Job `FAILED` | 可 |
| ST-POOL-008 | owner不一致 | finish | 変更なし | update 0 | warn | 新owner側に委譲 |
| ST-CONT-001 | 新規URL | save | `NONE` | insert | saved count | 投稿対象 |
| ST-CONT-002 | 既存URL | save | 変更なし | insertなし | duplicate count | 対象外 |
| ST-CONT-003 | `NONE` | WP成功 | `SUCCESS` | cms id保存 | success count | 再投稿なし |
| ST-CONT-004 | `NONE` | WP失敗 | `FAIL` | status保存 | failure count | 可 |
| ST-CONT-005 | AI fallback | WP成功 | `SUCCESS` | 原文またはAI結果保存 | AI fallback count | 再投稿なし |

### 8.1 pool claim・所有権テスト（次期）

| ID | 前提 | 入力 | 期待結果 | DB状態 | ログ | 再実行可否 |
| --- | --- | --- | --- | --- | --- | --- |
| POOL-CLM-001 | AVAILABLE 1枠 | owner Aがclaim | lease 1件取得 | PROCESSING、owner A、attempt+1、時刻設定 | claim success | release後可 |
| POOL-CLM-002 | AVAILABLE 3枠 | 1 workerが1件要求 | 並び順先頭の1件だけ取得 | 1件PROCESSING、2件AVAILABLE | pool/category ID | 残りは可 |
| POOL-CLM-003 | AVAILABLE 1枠 | 2 thread同時claim | 成功は1threadだけ | UPDATE合計1件、ownerは1つ | 競合側update=0 | release後可 |
| POOL-CLM-004 | AVAILABLE 1枠 | instance A/Bを別transactionで同時claim | 成功は1instanceだけ | owner instanceは1つ | 競合側update=0 | release後可 |
| POOL-CLM-005 | 全枠PROCESSINGか無効 | worker起動要求 | workerを開始しない | 変更なし | available=0 | 枠解放後可 |
| POOL-CLM-006 | heartbeatがtimeout超過 | 新ownerがstale claim | 回収後に新lease取得 | owner/attempt更新、旧owner失効 | stale recovery監査ログ | 可 |
| POOL-CLM-007 | heartbeatがtimeout未満 | 別ownerがclaim | 取得失敗 | 変更なし | update=0 | timeout/release後可 |
| POOL-CLM-008 | owner A/attempt 3でPROCESSING | owner Bまたはattempt 2がheartbeat/update | 更新拒否 | 変更なし | owner mismatch warn | 正ownerのみ可 |
| POOL-CLM-009 | 候補取得後に他ownerが先取 | CAS UPDATE | 更新0件をclaim失敗として扱いworker未起動 | 他owner状態維持 | claim lost | 次候補で可 |
| POOL-CLM-010 | category重複poolなし | migration前検査 | unique制約追加可能 | categoryあたり1枠 | 検査件数0 | 可 |

POOL-CLM-003は同一JVMのbarrier付きconcurrency test、POOL-CLM-004は別DataSource接続またはTestcontainers上の2 application contextで複数JVM相当を再現する。H2だけの結果でMariaDB競合試験を完了扱いにしない。

### 8.2 pool releaseテスト（次期）

各ケースはowner A/attempt Nでclaim済みとし、カテゴリ処理の最外周が独立した短いtransactionでreleaseすることを検証する。

| ID | 前提 | 入力 | 期待結果 | DB状態 | ログ | 再実行可否 |
| --- | --- | --- | --- | --- | --- | --- |
| POOL-REL-001 | claim済み | 正常終了 | 1回release | AVAILABLE、owner/heartbeat null | release success | 即時可 |
| POOL-REL-002 | claim済み | 記事0件 | 業務結果を記録後release | AVAILABLE | zero items + release | 即時可 |
| POOL-REL-003 | claim済み | 一覧/詳細HTTP例外 | finally相当でrelease | AVAILABLE | HTTP failure + release | retry規則に従い可 |
| POOL-REL-004 | claim済み | parser例外 | release | AVAILABLE | parser failure + release | 可 |
| POOL-REL-005 | claim済み | 記事保存transaction rollback | 別transactionでrelease | AVAILABLE、記事insertなし | DB failure + release | 可 |
| POOL-REL-006 | claim済み | AI例外 | fallbackまたは失敗記録後release | AVAILABLE | AI結果 + release | 可 |
| POOL-REL-007 | claim済み | WordPress例外 | 投稿失敗記録後release | AVAILABLE、contents FAIL | WP failure + release | 冪等性確認後可 |
| POOL-REL-008 | claim済み | `InterruptedException` | interrupt flag復元後release | AVAILABLE | interrupted + release | Job方針に従う |
| POOL-REL-009 | claim済み | skip上限超過 | Step失敗化してrelease | AVAILABLE | skip limit + release | 可 |
| POOL-REL-010 | claim済み | Job stop要求 | 実行中worker停止処理後release | AVAILABLE | stopped + release | 可 |
| POOL-REL-011 | claim済み | releaseを2回呼ぶ | 1回目1件、2回目0件で安全 | AVAILABLE維持 | duplicate release debug | 可 |
| POOL-REL-012 | owner Aのlease | owner Bがrelease | 拒否 | PROCESSING owner A維持 | owner mismatch warn | owner Aのみ可 |
| POOL-REL-013 | Aのstale leaseをBが回収済み | Aが遅延release | 新leaseを解放しない | PROCESSING owner B/新attempt維持 | stale owner warn | B完了後可 |
| POOL-REL-014 | 通常終了hook登録済み | application context close | 所有leaseをrelease | AVAILABLE | shutdown release summary | 可 |
| POOL-REL-015 | JVM強制終了を模擬 | release実行なし | staleになるまで占有、回収後利用可能 | 回収後owner/attempt更新 | stale recovery | timeout後可 |

### 8.3 heartbeat・stale回収テスト（次期）

| ID | 前提 | 入力 | 期待結果 | DB状態 | ログ | 再実行可否 |
| --- | --- | --- | --- | --- | --- | --- |
| POOL-HB-001 | 正ownerのlease | scheduler tick | heartbeat更新 | heartbeatだけ進みclaimed_at不変 | heartbeat debug/metric | 継続可 |
| POOL-HB-002 | `age < lease_timeout` | stale走査 | 回収しない | owner/attempt不変 | recovered=0 | timeout後可 |
| POOL-HB-003 | `age > lease_timeout` | Job開始前走査 | stale候補として回収可能化 | CAS成功時owner/attempt更新またはAVAILABLE | 旧owner付き監査ログ | 可 |
| POOL-HB-004 | stale回収対象 | 回収実行 | 監査項目を全て出力 | 状態は回収結果どおり | pool/category/旧owner/attempt/age/新owner | 可 |
| POOL-HB-005 | releaseとstale回収がbarrier同期 | 同時CAS | 片方だけ成功し整合性維持 | AVAILABLEまたは新owner PROCESSINGの一方 | loser update=0 | 可 |
| POOL-HB-006 | 外部HTTPがheartbeat interval超継続 | schedulerを別threadで実行 | HTTP待機中もheartbeatが複数回進む | fresh PROCESSING維持 | heartbeat metric | 完了後可 |
| POOL-HB-007 | owner JVM消失 | heartbeat停止、timeout経過、別instance claim | 孤児枠を再利用 | 新owner/attempt | stale recovery | 可 |
| POOL-HB-008 | stale判定後に旧owner heartbeat成功 | 回収CAS | heartbeat再確認で回収失敗 | 旧owner維持 | recovery lost | 継続可 |
| POOL-HB-009 | heartbeat DB更新が連続失敗 | workerが次の外部副作用前にownership確認 | 所有不明なら安全停止 | 新規副作用なし、最終的にreleaseかstale | heartbeat failure | 回収後可 |

### 8.4 category assignment / worker lifecycleテスト

| ID | 前提 | 入力 | 期待結果 | DB状態 | ログ | 再実行可否 |
| --- | --- | --- | --- | --- | --- | --- |
| POOL-CON-001 | `C=10, T=3` | shuffled category IDs | ASCの連続assignment `3/3/4` | union 10、intersection 0 | plan IDs | 可 |
| POOL-CON-002 | `C=14, T=4` | shuffled category IDs | `3/3/3/5` | union 14、intersection 0 | plan IDs | 可 |
| POOL-CON-003 | `C=12, T=4` | shuffled category IDs | `3/3/3/3` | union 12、intersection 0 | plan IDs | 可 |
| POOL-CON-004 | `C=3, T=5` | category 1,2,3 | `0/0/0/0/3`をplanに保持し、非空のworker 5だけsubmit | category欠落/重複0 | submitted=1 | 可 |
| POOL-CON-005 | 1 workerへA,B,C割当 | A処理開始 | A完了後B、B完了後C | 各poolを個別claim/release | start/complete/next | 可 |
| POOL-CON-006 | Aは新規URL0、Bは新規あり | assignment実行 | AをSUCCESS扱いしBを処理 | duplicate insert 0 | duplicate/new count | 可 |
| POOL-CON-007 | Aが回収可能なparser/HTTP失敗、B正常 | assignment実行 | AをFAIL releaseしBを処理、最終Job FAILED | Bまで完了 | failure summary | 可 |
| POOL-CON-008 | Aでinterrupt/DB/transaction障害 | assignment実行 | run-wide fatalとしてBを開始しない | fenced cleanup | Step FAILED | 再実行前に状態確認 |
| POOL-CON-009 | worker 1が先に完了 | 固定assignmentを並列実行 | worker 1はworker 2の残カテゴリを取得しない | ownership交差0 | worker/category IDs | 可 |
| POOL-CON-010 | 複数worker/category、`POST_LIMIT=3` | 4workerが投稿予約 | 全worker合計attemptが3以下 | cms投稿attempt<=3 | `wordpressPostAttemptCount` | 可 |
| POOL-CON-011 | WordPress成功後DB保存失敗 | 次カテゴリあり | reservationを返却せずrunを停止 | 同一runで後続postなし | attempt=1/fatal | 状態確認後可 |

## 9. WordPress投稿契約テスト

本章はCrawler内部の `WordPressPostService` によるWordPress公式REST API連携を対象とする。`ArticleImagePolicy` は元画像URL候補の判定だけを担い、WordPress APIを呼び出さない。`featured_image_url` は元画像URLであり、`POST /wp-json/wp/v2/media` が返したmedia IDを `POST /wp-json/wp/v2/posts` の `featured_media` に設定する。独自media API、media ID永続管理、独立投稿Job・サービスはテスト対象となる現行仕様に含めない。

| ID | 条件 | 期待 |
| --- | --- | --- |
| WP-001 | 201 response with id | cms idを返す |
| WP-002 | 200 response with id | cms idを返す |
| WP-003 | 400 | 例外、秘密情報なし |
| WP-004 | 401/403 | 例外、秘密情報なし |
| WP-005 | 404 | 例外 |
| WP-006 | 429 | 例外。現行retryなし |
| WP-007 | 500 | 例外 |
| WP-008 | timeout | 例外 |
| WP-009 | 不正JSON | 例外 |
| WP-010 | 空response | 例外 |
| WP-011 | duplicate source URL | 現行はWordPress側重複照合なし。未実装として記録 |
| WP-012 | media upload success | body image URLを投稿先media URLへ置換 |
| WP-013 | media upload failure | 画像を除外し投稿継続 |
| WP-014 | featured image configured | `POST /media` の返却media IDを `POST /posts` の `featured_media` に設定 |
| WP-015 | category update success | `POST /posts/{id}` にcategories送信 |
| WP-016 | featured upload failure | downloadまたはmedia API失敗 | warn後、`featured_media`なしで記事投稿継続 |
| WP-017 | featured/body same image | 同一取得元URLが両方に存在 | media uploadは1回、同じmedia id/URLを再利用 |
| WP-018 | featured image absent | metadata・本文に有効画像なし | `featured_media`を送信しない |
| WP-019 | repair対象の`featured_image_url`が空 | 保存済み元記事URLを再解析 | 通常画像選定でURLを再取得し、既存DB行へ保存 |
| WP-020 | 既存postの`featured_media=0` | 有効な`featured_image_url`あり | mediaをparent付きで登録し、`POST /posts/{id}`へ`featured_media`だけを送信。新規postなし |
| WP-021 | repairのmedia成功後にpost更新失敗 | 同じparentに同一bytesのmediaあり | 既存media IDを再利用し、mediaを重複登録しない |
| WP-022 | repair-only mode | `REPAIR_CMS_CONTENT_ID`指定 | 1 leaseだけclaimし、カテゴリcrawl、通常保存、新規CMS投稿を行わない |
| WP-023 | featured-only repair | DB側カテゴリ設定あり | post更新payloadにtitle、content、categoriesを含めず、既存post ID・URL・カテゴリを維持 |
| WP-024 | repairのsource再取得失敗 | parser例外 | WordPress APIへ到達せず、failure summaryを加算してleaseをFAIL解放 |

## 9.1 本番起動・workflow契約

| ID | 条件 | 期待 |
| --- | --- | --- |
| PROD-001 | `WP_BASE_URL`が空、`CMS_HOST`あり、CMS投稿ON | `CMS_HOST`をfallbackとして起動成功 |
| PROD-002 | `TARGET_SITE_INFO_IDS=2` | SiteInfo 2のpoolだけをsnapshotへ選択 |
| PROD-003 | prod profile起動 | `schema-mysql-compatible.sql`を自動実行しない |
| PROD-004 | artifact build失敗 | crawlを実行せずworkflow失敗 |
| PROD-005 | crawl process非0終了 | 過去DB行の受入条件に関係なくworkflow失敗 |
| PROD-006 | CMS投稿OFFの限定run | Yahoo専用受入判定をskipし、crawl終了コードで成否判定 |
| PROD-007 | `TARGET_SITE_CATEGORY_IDS=3` | category 3のpoolだけをsnapshot・assignment前に選択 |
| PROD-008 | SiteInfoとSiteCategoryを同時指定 | 両条件に一致するpoolのみ選択し、別SiteInfo配下なら対象0件 |
| PROD-009 | category target空 | scheduled/pushを含めcategoryによる追加filterなし |
| PROD-010 | category targetが不正形式 | claim前にfail-fast |
| PROD-011 | workflow_dispatchの`post_limit=1` | `POST_LIMIT=1`。空ならrepository variable、未設定なら`1000` |
| PROD-012 | workflow_dispatchの`post_featured_image_only=true` | `featured_image_url` が非空のCMS未投稿候補だけを昇順で選び、その後に`POST_LIMIT`を適用。既定falseのscheduled/pushは従来動作 |
| PROD-013 | workflow_dispatchの`repair_cms_content_id`指定 | 通常crawl/post経路を通らず、対象CMS IDのrepairだけを1 leaseで実行 |

## 10. MySQL統合テスト

通常CIのH2では不十分な項目:

- `site_info_process_pool.site_info_process_id` AUTO_INCREMENT有無。
- `process_id varchar(64)` 前提。
- MariaDB `UPDATE ... WHERE status in (...) OR stale PROCESSING` の同時claim。
- owner/attempt/heartbeatを条件にしたclaim、heartbeat、release、stale回収CASの競合。
- categoryあたり1枠のunique制約と、追加前の重複検査。
- `site_contents.url varchar(1024)` unique indexの実挙動。
- requested/redirected/canonical/normalized/source URLカラムとnormalized URLのuniqueまたはhash unique制約。
- Flyway履歴なし本番DBとmigration V1-V6の差分。

MySQL統合テストは本番DBではなくMariaDB 10.11へ `integrationTest` を実行する。2026-08はGitHub Actions quota停止中のため、ローカルでMariaDB接続を用意できない場合は未実行として明記し、hosted結果を合格根拠にしない。Actions runtimeは2026-09 quota refresh後のpost-release verificationへ延期する。

実装済み自動テストの対応: `UrlCanonicalizerTest` はCAN-004/010/014/023、`SiteInfoProcessRepositoryConcurrencyTest` はPOOL-CLM-001/006/008とPOOL-REL-004、`ContentsParserTest` はYH-001/002およびHP-REST-001/002/003、`MariaDbMigrationIntegrationTest` はV1/V5重複を含む空DB migration、V6 upgrade、DDL、atomic claimを検証する。

## 11. 本番前smoke test

本番前smokeは別承認で、次の最小条件に限定する。

- `CRAWL_PROCESS_LIMIT=1`（1 worker。対象カテゴリ件数の限定ではない）
- `CRAWL_CONTENT_LIMIT=1`
- `POST_LIMIT=1`
- Gate 1では `TARGET_SITE_CATEGORY_IDS` を1カテゴリへ必ず限定する。Gate 2では少数カテゴリを明示し、同一workerの逐次処理を確認する
- featured-image E2Eでは `POST_FEATURED_IMAGE_ONLY=true` を指定し、画像なしの古いCMS未投稿行を投稿せず、画像URLあり候補へ `POST_LIMIT` を適用
- AI検証時以外は `AI_MODE=off`
- WordPress投稿を伴う場合は `SKIP_CMS_POST=false` の明示承認
- 実行前に対象category、対象pool、投稿先カテゴリ、rollback/削除方針を確認

合格条件:

- DBに新規または既存対象が追跡できる。
- WordPress RESTが2xxで、`cms_content_id` が保存される。
- 公開URLがHTTP 200。
- 秘密情報がログに出ない。
- 失敗時に対象contents/poolがFAILになり、後続処理が継続できる。

## 12. 次工程生成準備評価

| 項目 | 評価 | 根拠 |
| --- | --- | --- |
| 単体テスト生成 | 準備完了 | 5章のUT ID |
| canonical URLテスト生成 | 準備完了 | 5.1章のCAN ID |
| Yahoo fixture生成 | 準備完了 | 外部canonicalは判断前の既定拒否をCAN-019で固定 |
| happinesea RESTテスト生成 | 準備完了 | 7章のREST ID |
| Repository/Serviceテスト生成 | 準備完了 | 8章の現行ST IDと次期POOL ID |
| WordPress mockテスト生成 | 準備完了 | 9章 |
| MySQL統合テスト生成 | 準備完了 | ケースは定義済み。実行基盤とschema authorityは実装PR開始前の人間判断 |
| 本番smoke自動化 | 未準備 | 投稿・削除・公開範囲の人間承認が必要 |
