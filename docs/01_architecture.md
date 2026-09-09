# アーキテクチャ概要
これはSpring Boot 3.5.3アプリケーションで、Spring Batchを使用してウェブクローリングを行います。Jsoupを使用してウェブサイトのコンテンツを解析し、MySQL（dev/prod）またはH2（test）にデータを保存します。主要なエンティティ：`SiteInfo`（サイト）-> `SiteCategory`（CSSセレクタ付きカテゴリ）-> `SiteContents`（解析された記事）。

Junitを実行する際に、プロファイル`test`を使って、マイグレーションを使用せず、H2機能を利用して、Entity定義から、毎回DBスキマーを再構築&テストデータ投入してテストを行う。`ContentsParser`のHTML解析ロジックと、`SiteContentsService`のステータス管理ロジックに重点を置く。モックを使用して、外部依存関係を分離する。
単体テスト(dev)を実行する際に、Github Secretsに保存したDB接続情報を使用して、MySQLに接続してマイグレーションを使用して、DBスキマーを構築&テストデータ投入してテストを行う。DBに設定したテストデータを使用して、アプリケーション全体のフローをテストする。
本番実行、prodプロファイルを使用して、Github Secretsに保存したDB接続情報を使用して、MySQLに接続して実行する。

データフロー：バッチジョブが`SiteInfoProcessPool`でスレッドを管理して、ステータスをPROCESSINGに更新、カテゴリURLを解析してコンテンツリストを抽出、`SiteContents`をNONE/SUCCESS/FAILステータスで保存。
バッチはcron(GitHub ActionsのScheduled triggers)で自動化する。想定する実行シーンとして、サイトごとに１バッチは都度実行して、複数のスレッド処理を行う。
APIにコンテンツを投稿する際に、１回のリクエスト`postContentsLimitCount`件で投稿することを制限します。

DBや外部APIの接続情報は直接設定ファイルに保存せずに、環境変数としてGithub Secretsに保存し、Spring Bootの`application.yml`で参照します。



# 開発ワークフロー
- **ビルド/テスト**: `./gradlew build` または `./gradlew test`。Jacocoでカバレッジ。
- **開発実行**: `SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun`（MySQL via env vars）。
- **本番実行**: `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun`（GitHub Secrets for DB/CMS）。
- **デバッグ**: `CrawlerComponents.siteInfoProcessor()`にブレークポイントを設定。
- **マイグレーション**: Flywayスクリプト in `src/main/resources/db/migration/`。テストでは、エンティティから、毎回H2上でスキマー自動生成する。

# コードパターン
- **Enums**: `PersistableEnum`を実装し、文字列値（例：`ProcessStatus.NONE("1")`）。`@Convert(converter = ProcessStatusConverter.class)`を使用。
- **Converters**: JPAマッピングのために`EnumAttributeConverter<T>`を拡張。
- **Services**: ステータス更新に`@Transactional`。`@Autowired`でリポジトリを注入。
- **Parsing**: HTML抽出に`Jsoup.connect(url).get().select(selector)`。
- **Config**: `application.yml`の`web-crawler`下にカスタムプロパティ（例：`post-contents-limit-count`）。

# 規約
- エンティティフィールドはsnake_case DBカラム（例：`site_categoy_id` - タイポ注意）。
- プロファイル：`dev`（MySQL、Flyway有効）、`test`（H2、ddl-auto create）、`prod`（env vars）。
- ログ：Lombok `@Slf4j`、解析失敗で警告ログ。
- 依存関係：不明な機能のためのカスタム`rws-lib:1.0.0_preview`。

参照：解析例`ContentsParser.loadCategoryContentsList()`、ステータス処理`SiteContentsService.changSiteInfoProcess2Processing()`。

# 現行仕様の正本
2026-08-04時点の実装・実DB・対象サイト確認に基づく現行仕様は、次を正本として参照する。

- [web-crawler system understanding](design/web-crawler-system-understanding.md)
- [web-crawler functional and master data specification](design/web-crawler-master-data-spec.md)
- [web-crawler test specification](testing/web-crawler-test-spec.md)

この3文書では、現在も有効な仕様、一部古い設計、将来構想、未決定事項を分けている。古い資料と矛盾する場合は、実DB、実装、テストの順に優先する。

# Wordpressへの投稿について
Wordpressへの投稿はREST APIを利用します。

## Wordpress API操作のチュートリアル
### カテゴリ一覧取得
```
・リクエスト
GET https://baidu.tokyo/wp-json/wp/v2/categories

・レスポンス
[
    {
        "id": 7,
        "count": 0,
        "description": "",
        "link": "https://baidu.tokyo/it",
        "name": "IT",
        "slug": "it",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/7",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=7"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    },
    {
        "id": 5,
        "count": 0,
        "description": "",
        "link": "https://baidu.tokyo/entertainment",
        "name": "エンタメ",
        "slug": "entertainment",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/5",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=5"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    },
    {
        "id": 8,
        "count": 0,
        "description": "",
        "link": "https://baidu.tokyo/science",
        "name": "サイエンス",
        "slug": "science",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/8",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=8"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    },
    {
        "id": 6,
        "count": 0,
        "description": "",
        "link": "https://baidu.tokyo/sports",
        "name": "スポーツ",
        "slug": "sports",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/6",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=6"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    },
    {
        "id": 2,
        "count": 1,
        "description": "",
        "link": "https://baidu.tokyo/domestic",
        "name": "国内",
        "slug": "domestic",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/2",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=2"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    },
    {
        "id": 3,
        "count": 0,
        "description": "",
        "link": "https://baidu.tokyo/world",
        "name": "国際",
        "slug": "world",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/3",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=3"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    },
    {
        "id": 9,
        "count": 0,
        "description": "",
        "link": "https://baidu.tokyo/local",
        "name": "地域",
        "slug": "local",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/9",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=9"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    },
    {
        "id": 4,
        "count": 0,
        "description": "",
        "link": "https://baidu.tokyo/business",
        "name": "経済",
        "slug": "business",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/4",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=4"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    },
    {
        "id": 1,
        "count": 1,
        "description": "最新ニュース速報",
        "link": "https://baidu.tokyo/news",
        "name": "総合",
        "slug": "news",
        "taxonomy": "category",
        "parent": 0,
        "meta": [],
        "acf": [],
        "_links": {
            "self": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories/1",
                    "targetHints": {
                        "allow": [
                            "GET"
                        ]
                    }
                }
            ],
            "collection": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/categories"
                }
            ],
            "about": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/taxonomies/category"
                }
            ],
            "wp:post_type": [
                {
                    "href": "https://baidu.tokyo/wp-json/wp/v2/posts?categories=1"
                }
            ],
            "curies": [
                {
                    "name": "wp",
                    "href": "https://api.w.org/{rel}",
                    "templated": true
                }
            ]
        }
    }
]

```


## 投稿一覧取得
```
リクエスト
GET https://baidu.tokyo/wp-json/wp/v2/posts

レスポンス
[
{
"id": 51,
"date": "2025-10-03T21:43:50",
"date_gmt": "2025-10-03T12:43:50",
"guid": {
"rendered": "https://baidu.tokyo/?p=51"
},
"modified": "2025-10-03T21:43:55",
"modified_gmt": "2025-10-03T12:43:55",
"slug": "%e8%a9%95%e8%ab%96%ef%bc%9a%e4%b8%ad%e5%9b%bd%e3%81%ae%e6%b5%b7%e6%b4%8b%e8%aa%bf%e6%9f%bb%e8%88%b9%e3%80%81%e5%a5%84%e7%be%8e%e5%a4%a7%e5%b3%b6%e6%b2%96%e3%81%ae%e6%8e%92%e4%bb%96%e7%9a%84%e7%b5%8c",
"status": "publish",
"type": "post",
"link": "https://baidu.tokyo/20251003/51.html",
"title": {
"rendered": "評論：中国の海洋調査船、奄美大島沖の排他的経済水域で活動　2日前にも"
},
"content": {
"rendered": "\n<p>米国による台湾領土未定論は、カイロ宣言やポツダム宣言の効力そのものを否定する立場に近い。<br>一方で、中国は外交部の発言にとどまらず、行動を通じて「サンフランシスコ平和条約」を承認しない姿勢を示している。</p>\n\n\n\n<p>日本は、敗戦によるアジア諸国への責任を十分に整理せず、中国内戦や分断に介入してきた経緯がある。そのため中国は、日本にポツダム宣言を徹底させる手段として、軍事力を行使する可能性を高めつつある。</p>\n\n\n\n<p>日本国内では依然として「中国に負けていない」との声が根強い。<br>だが、現実に「日本は中国に敗れた」という共通認識を形成するためには、戦争を通じた決着しか残されていないのかもしれない。</p>\n\n\n\n<p>その構図は、日本を「東アジアのウクライナ化」へと導くものであり、米中双方にとって共通の認識となりつつある。</p>\n\n\n\n<p></p>\n",
"protected": false
},
"excerpt": {
"rendered": "<p>米国による台湾領土未定論は、カイロ宣言やポツダム宣言の効力そのものを否定する立場に近い。一方で、中国は外交部の発言にとどまらず、行動を通じて「サンフランシスコ平和条約」を承認しない姿勢を示している。 日本は、敗戦によるア [&hellip;]</p>\n",
"protected": false
},
"author": 1,
"featured_media": 0,
"comment_status": "closed",
"ping_status": "open",
"sticky": false,
"template": "",
"format": "standard",
"meta": {
"footnotes": ""
},
"categories": [
2
],
"tags": [],
"class_list": [
"post-51",
"post",
"type-post",
"status-publish",
"format-standard",
"hentry",
"category-domestic"
],
"acf": [],
"_links": {
"self": [
{
"href": "https://baidu.tokyo/wp-json/wp/v2/posts/51",
"targetHints": {
"allow": [
"GET"
]
}
}
],
"collection": [
{
"href": "https://baidu.tokyo/wp-json/wp/v2/posts"
}
],
"about": [
{
"href": "https://baidu.tokyo/wp-json/wp/v2/types/post"
}
],
"author": [
{
"embeddable": true,
"href": "https://baidu.tokyo/wp-json/wp/v2/users/1"
}
],
"replies": [
{
"embeddable": true,
"href": "https://baidu.tokyo/wp-json/wp/v2/comments?post=51"
}
],
"version-history": [
{
"count": 4,
"href": "https://baidu.tokyo/wp-json/wp/v2/posts/51/revisions"
}
],
"predecessor-version": [
{
"id": 55,
"href": "https://baidu.tokyo/wp-json/wp/v2/posts/51/revisions/55"
}
],
"wp:attachment": [
{
"href": "https://baidu.tokyo/wp-json/wp/v2/media?parent=51"
}
],
"wp:term": [
{
"taxonomy": "category",
"embeddable": true,
"href": "https://baidu.tokyo/wp-json/wp/v2/categories?post=51"
},
{
"taxonomy": "post_tag",
"embeddable": true,
"href": "https://baidu.tokyo/wp-json/wp/v2/tags?post=51"
}
],
"curies": [
{
"name": "wp",
"href": "https://api.w.org/{rel}",
"templated": true
}
]
}
},
{
"id": 1,
"date": "2021-08-29T11:24:12",
"date_gmt": "2021-08-29T11:24:12",
"guid": {
"rendered": "https://baidu.tokyo/?p=1"
},
"modified": "2021-08-31T13:45:15",
"modified_gmt": "2021-08-31T04:45:15",
"slug": "hello-world",
"status": "publish",
"type": "post",
"link": "https://baidu.tokyo/20210829/1.html",
"title": {
"rendered": "始めまして！東京ウォッチャーズNEWS速報サイトを立ち上げました。"
},
"content": {
"rendered": "\n<p>まだまだ、整えていますが、宜しくお願い致します。</p>\n",
"protected": false
},
"excerpt": {
"rendered": "<p>まだまだ、整えていますが、宜しくお願い致します。</p>\n",
"protected": false
},
"author": 1,
"featured_media": 0,
"comment_status": "open",
"ping_status": "open",
"sticky": false,
"template": "",
"format": "standard",
"meta": {
"footnotes": ""
},
"categories": [
1
],
"tags": [],
"class_list": [
"post-1",
"post",
"type-post",
"status-publish",
"format-standard",
"hentry",
"category-news"
],
"acf": [],
"_links": {
"self": [
{
"href": "https://baidu.tokyo/wp-json/wp/v2/posts/1",
"targetHints": {
"allow": [
"GET"
]
}
}
],
"collection": [
{
"href": "https://baidu.tokyo/wp-json/wp/v2/posts"
}
],
"about": [
{
"href": "https://baidu.tokyo/wp-json/wp/v2/types/post"
}
],
"author": [
{
"embeddable": true,
"href": "https://baidu.tokyo/wp-json/wp/v2/users/1"
}
],
"replies": [
{
"embeddable": true,
"href": "https://baidu.tokyo/wp-json/wp/v2/comments?post=1"
}
],
"version-history": [
{
"count": 1,
"href": "https://baidu.tokyo/wp-json/wp/v2/posts/1/revisions"
}
],
"predecessor-version": [
{
"id": 13,
"href": "https://baidu.tokyo/wp-json/wp/v2/posts/1/revisions/13"
}
],
"wp:attachment": [
{
"href": "https://baidu.tokyo/wp-json/wp/v2/media?parent=1"
}
],
"wp:term": [
{
"taxonomy": "category",
"embeddable": true,
"href": "https://baidu.tokyo/wp-json/wp/v2/categories?post=1"
},
{
"taxonomy": "post_tag",
"embeddable": true,
"href": "https://baidu.tokyo/wp-json/wp/v2/tags?post=1"
}
],
"curies": [
{
"name": "wp",
"href": "https://api.w.org/{rel}",
"templated": true
}
]
}
}
]
```

## 投稿
```
POST https://baidu.tokyo/wp-json/wp/v2/posts
HEADERS Content-Type:application/json
Authorization Basic base64(username:appPassword)
BODY 
{
  "title": "テスト投稿（API）",
  "content": "これはPostmanからの投稿テストです",
  "status": "publish"
}

```
