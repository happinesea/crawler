# Hourly Crawl Latest Status

- Run ID: 34211073963
- Commit: 32fd1cb64a4ab9add3b21038e14db749b61c6973
- Event: push
- Timestamp UTC: 2026-09-08T10:07:17Z
- Preflight ready: true
- Crawl exit code: 124
- System acceptance: skipped
- Artifact: build/libs/web-crawler-0.0.1-SNAPSHOT.jar
- Artifact SHA-256: a0fc4c826075aaacf2a92506f09b6a00db134f728c79b16827fb3567369faadd
- Target SiteInfo IDs: 
- Target SiteCategory IDs: 
- Post limit: 1000
- Featured-image post filter: false
- Public posts API status: ok

## System Acceptance Evidence

- system-test.txt was not created.

## Latest Public Posts

- Count returned: 10
- 4595 | 2026-09-08T19:04:29 | https://baidu.tokyo/20260908/4595.html | 園児から「しね」手紙 いじめ相当
- 4592 | 2026-09-08T19:04:00 | https://baidu.tokyo/20260908/4592.html | 東海地方で大雨 現地のSNS投稿
- 4589 | 2026-09-08T16:43:41 | https://baidu.tokyo/20260908/4589.html | デヴィ夫人に罰金20万円を求刑
- 4586 | 2026-09-08T16:43:21 | https://baidu.tokyo/20260908/4586.html | 福岡女性遺体 殺人容疑で3人逮捕
- 4583 | 2026-09-08T14:49:26 | https://baidu.tokyo/20260908/4583.html | 土砂崩れ作業員1人生き埋め 静岡
- 4580 | 2026-09-08T14:48:45 | https://baidu.tokyo/20260908/4580.html | 車水没 市は通報3分前に水位把握
- 4576 | 2026-09-08T14:19:34 | https://baidu.tokyo/20260908/4576.html | 大谷 今季の投手復帰断念を明かす
- 4573 | 2026-09-08T10:23:26 | https://baidu.tokyo/20260908/4573.html | 親睦行事の駅伝で夫熱中症死 提訴
- 4570 | 2026-09-08T10:22:58 | https://baidu.tokyo/20260908/4570.html | 豪雨で車被災 負担重なり生活圧迫
- 4567 | 2026-09-08T10:22:30 | https://baidu.tokyo/20260908/4567.html | 早朝に窓叩く音 女性保護した母娘

## Crawl Log Tail

```text
2026-09-08 10:05:43.873 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Before cleanup stats (total=2/2, idle=2/0, active=0, waiting=0)
2026-09-08 10:05:43.873 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - After  cleanup stats (total=2/2, idle=2/0, active=0, waiting=0)
2026-09-08 10:05:43.873 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Fill pool skipped, pool has sufficient level or currently being filled.
2026-09-08 10:05:45.697 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:05:45.697 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:05:45.697 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1191 cmsContentId=2947 categories=[2]
2026-09-08 10:05:45.697 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/2949
2026-09-08 10:05:45.697 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:05:45.698 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:05:48.786 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.heartbeatOwnedProcessing]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2026-09-08 10:05:48.787 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Opened new EntityManager [SessionImpl(396911651<open>)] for JPA transaction
2026-09-08 10:05:48.787 [scheduling-1] DEBUG o.h.e.t.internal.TransactionImpl - On TransactionImpl creation, JpaCompliance#isJpaTransactionComplianceEnabled == false
2026-09-08 10:05:48.787 [scheduling-1] DEBUG o.h.e.t.internal.TransactionImpl - begin
2026-09-08 10:05:49.076 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@3139e16b]
2026-09-08 10:05:49.076 [scheduling-1] DEBUG org.hibernate.orm.sql.ast.create - Created new SQL alias : sipp1_0
2026-09-08 10:05:49.077 [scheduling-1] DEBUG org.hibernate.orm.sql.ast.create - Registration of TableGroup [StandardTableGroup(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p))] with identifierForTableGroup [com.happinesea.webcrawler.entity.SiteInfoProcessPool] for NavigablePath [com.happinesea.webcrawler.entity.SiteInfoProcessPool] 
2026-09-08 10:05:49.077 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmParameter : SqmNamedParameter(id)
2026-09-08 10:05:49.077 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmPath : SqmBasicValuedSimplePath(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p).siteInfoProcessId) 
2026-09-08 10:05:49.077 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmParameter : SqmNamedParameter(processingStatus)
2026-09-08 10:05:49.077 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmPath : SqmBasicValuedSimplePath(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p).processStatus) 
2026-09-08 10:05:49.077 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmParameter : SqmNamedParameter(owner)
2026-09-08 10:05:49.077 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmPath : SqmBasicValuedSimplePath(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p).processId) 
2026-09-08 10:05:49.077 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmParameter : SqmNamedParameter(leaseAttempt)
2026-09-08 10:05:49.077 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmPath : SqmBasicValuedSimplePath(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p).leaseAttempt) 
2026-09-08 10:05:49.077 [scheduling-1] DEBUG org.hibernate.SQL - update site_info_process_pool sipp1_0 set heartbeat_at=?,process_time=? where sipp1_0.site_info_process_id=? and sipp1_0.process_status=? and sipp1_0.process_id=? and sipp1_0.lease_attempt=?
2026-09-08 10:05:49.225 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Initiating transaction commit
2026-09-08 10:05:49.225 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Committing JPA transaction on EntityManager [SessionImpl(396911651<open>)]
2026-09-08 10:05:49.225 [scheduling-1] DEBUG o.h.e.t.internal.TransactionImpl - committing
2026-09-08 10:05:49.527 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Closing JPA EntityManager [SessionImpl(396911651<open>)] after transaction
2026-09-08 10:05:50.441 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:05:50.441 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:05:50.441 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1192 cmsContentId=2949 categories=[2]
2026-09-08 10:05:50.441 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/2951
2026-09-08 10:05:50.441 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:05:50.441 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:05:55.432 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - keepalive: connection com.mysql.cj.jdbc.ConnectionImpl@16fc0bcd is alive
2026-09-08 10:05:58.172 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:05:58.172 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:05:58.177 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1193 cmsContentId=2951 categories=[2]
2026-09-08 10:05:58.177 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/2953
2026-09-08 10:05:58.177 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:05:58.177 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:02.773 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - keepalive: connection com.mysql.cj.jdbc.ConnectionImpl@2466ccf6 is alive
2026-09-08 10:06:06.107 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:06.107 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:06.107 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1194 cmsContentId=2953 categories=[2]
2026-09-08 10:06:06.107 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3015
2026-09-08 10:06:06.108 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:06.108 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:10.958 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:10.958 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:10.958 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1227 cmsContentId=3015 categories=[2]
2026-09-08 10:06:10.959 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3017
2026-09-08 10:06:10.959 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:10.959 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:13.874 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Before cleanup stats (total=2/2, idle=2/0, active=0, waiting=0)
2026-09-08 10:06:13.874 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - After  cleanup stats (total=2/2, idle=2/0, active=0, waiting=0)
2026-09-08 10:06:13.874 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Fill pool skipped, pool has sufficient level or currently being filled.
2026-09-08 10:06:17.694 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:17.694 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:17.702 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1228 cmsContentId=3017 categories=[2]
2026-09-08 10:06:17.703 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3019
2026-09-08 10:06:17.703 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:17.703 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:22.740 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:22.740 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:22.744 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1229 cmsContentId=3019 categories=[2]
2026-09-08 10:06:22.744 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3021
2026-09-08 10:06:22.745 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:22.745 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:25.444 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - keepalive: connection com.mysql.cj.jdbc.ConnectionImpl@16fc0bcd is alive
2026-09-08 10:06:27.092 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - keepalive: connection com.mysql.cj.jdbc.ConnectionImpl@2466ccf6 is alive
2026-09-08 10:06:29.578 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:29.578 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:29.578 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1230 cmsContentId=3021 categories=[2]
2026-09-08 10:06:29.579 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3023
2026-09-08 10:06:29.579 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:29.579 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:31.442 [HikariPool-1:connection-closer] DEBUG com.zaxxer.hikari.pool.PoolBase - HikariPool-1 - Closing connection com.mysql.cj.jdbc.ConnectionImpl@16fc0bcd: (connection has passed maxLifetime)
2026-09-08 10:06:34.550 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:34.550 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:34.550 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1231 cmsContentId=3023 categories=[2]
2026-09-08 10:06:34.550 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3025
2026-09-08 10:06:34.550 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:34.550 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:39.152 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:39.152 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:39.153 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1232 cmsContentId=3025 categories=[2]
2026-09-08 10:06:39.153 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3027
2026-09-08 10:06:39.153 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:39.153 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:43.874 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Before cleanup stats (total=1/2, idle=1/0, active=0, waiting=0)
2026-09-08 10:06:43.874 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - After  cleanup stats (total=0/2, idle=0/0, active=0, waiting=0)
2026-09-08 10:06:43.874 [HikariPool-1:housekeeper] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Fill pool skipped, pool has sufficient level or currently being filled.
2026-09-08 10:06:43.874 [HikariPool-1:connection-closer] DEBUG com.zaxxer.hikari.pool.PoolBase - HikariPool-1 - Closing connection com.mysql.cj.jdbc.ConnectionImpl@2466ccf6: (connection has passed idleTimeout)
2026-09-08 10:06:48.940 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:48.940 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:48.940 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1233 cmsContentId=3027 categories=[2]
2026-09-08 10:06:48.941 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3029
2026-09-08 10:06:48.941 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:48.941 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:49.528 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.heartbeatOwnedProcessing]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2026-09-08 10:06:49.528 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Opened new EntityManager [SessionImpl(1912297257<open>)] for JPA transaction
2026-09-08 10:06:49.528 [scheduling-1] DEBUG o.h.e.t.internal.TransactionImpl - On TransactionImpl creation, JpaCompliance#isJpaTransactionComplianceEnabled == false
2026-09-08 10:06:49.528 [scheduling-1] DEBUG o.h.e.t.internal.TransactionImpl - begin
2026-09-08 10:06:49.528 [HikariPool-1:connection-adder] DEBUG com.zaxxer.hikari.pool.PoolBase - HikariPool-1 - Attempting to create/setup new connection (14386a51-d5fe-4ec6-a03c-0728b6c02084)
2026-09-08 10:06:51.324 [HikariPool-1:connection-adder] DEBUG com.zaxxer.hikari.pool.PoolBase - HikariPool-1 - Established new connection (14386a51-d5fe-4ec6-a03c-0728b6c02084)
2026-09-08 10:06:51.324 [HikariPool-1:connection-adder] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@424142e4
2026-09-08 10:06:51.468 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@6e84efe2]
2026-09-08 10:06:51.469 [scheduling-1] DEBUG org.hibernate.orm.sql.ast.create - Created new SQL alias : sipp1_0
2026-09-08 10:06:51.469 [scheduling-1] DEBUG org.hibernate.orm.sql.ast.create - Registration of TableGroup [StandardTableGroup(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p))] with identifierForTableGroup [com.happinesea.webcrawler.entity.SiteInfoProcessPool] for NavigablePath [com.happinesea.webcrawler.entity.SiteInfoProcessPool] 
2026-09-08 10:06:51.470 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmParameter : SqmNamedParameter(id)
2026-09-08 10:06:51.470 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmPath : SqmBasicValuedSimplePath(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p).siteInfoProcessId) 
2026-09-08 10:06:51.470 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmParameter : SqmNamedParameter(processingStatus)
2026-09-08 10:06:51.470 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmPath : SqmBasicValuedSimplePath(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p).processStatus) 
2026-09-08 10:06:51.470 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmParameter : SqmNamedParameter(owner)
2026-09-08 10:06:51.470 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmPath : SqmBasicValuedSimplePath(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p).processId) 
2026-09-08 10:06:51.470 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmParameter : SqmNamedParameter(leaseAttempt)
2026-09-08 10:06:51.470 [scheduling-1] DEBUG o.h.q.s.sql.BaseSqmToSqlAstConverter - Determining mapping-model type for SqmPath : SqmBasicValuedSimplePath(com.happinesea.webcrawler.entity.SiteInfoProcessPool(p).leaseAttempt) 
2026-09-08 10:06:51.470 [scheduling-1] DEBUG org.hibernate.SQL - update site_info_process_pool sipp1_0 set heartbeat_at=?,process_time=? where sipp1_0.site_info_process_id=? and sipp1_0.process_status=? and sipp1_0.process_id=? and sipp1_0.lease_attempt=?
2026-09-08 10:06:51.616 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Initiating transaction commit
2026-09-08 10:06:51.616 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Committing JPA transaction on EntityManager [SessionImpl(1912297257<open>)]
2026-09-08 10:06:51.616 [scheduling-1] DEBUG o.h.e.t.internal.TransactionImpl - committing
2026-09-08 10:06:51.917 [scheduling-1] DEBUG o.s.orm.jpa.JpaTransactionManager - Closing JPA EntityManager [SessionImpl(1912297257<open>)] after transaction
2026-09-08 10:06:53.036 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:53.036 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:53.037 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1234 cmsContentId=3029 categories=[2]
2026-09-08 10:06:53.038 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3031
2026-09-08 10:06:53.038 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:53.038 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:06:56.715 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:06:56.716 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:06:56.716 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1235 cmsContentId=3031 categories=[2]
2026-09-08 10:06:56.716 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3033
2026-09-08 10:06:56.716 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:06:56.717 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:07:06.021 [main] DEBUG o.s.web.client.RestTemplate - Response 200 OK
2026-09-08 10:07:06.021 [main] DEBUG o.s.web.client.RestTemplate - Reading to [java.lang.String] as "application/json;charset=UTF-8"
2026-09-08 10:07:06.021 [main] INFO  c.h.w.service.WordPressPostService - Updated WordPress categories. siteContentsId=1236 cmsContentId=3033 categories=[2]
2026-09-08 10:07:06.021 [main] DEBUG o.s.web.client.RestTemplate - HTTP POST https://baidu.tokyo/wp-json/wp/v2/posts/3035
2026-09-08 10:07:06.021 [main] DEBUG o.s.web.client.RestTemplate - Accept=[text/plain, application/json, application/*+json, */*]
2026-09-08 10:07:06.021 [main] DEBUG o.s.web.client.RestTemplate - Writing [{categories=[2]}] as "application/json"
2026-09-08 10:07:07.869 [SpringApplicationShutdownHook] DEBUG o.s.c.a.AnnotationConfigApplicationContext - Closing org.springframework.context.annotation.AnnotationConfigApplicationContext@165b8a71, started on Tue Sep 08 09:39:10 UTC 2026
2026-09-08 10:07:07.870 [SpringApplicationShutdownHook] DEBUG o.s.c.s.DefaultLifecycleProcessor - Stopping beans in phase 1073741823
2026-09-08 10:07:07.871 [SpringApplicationShutdownHook] DEBUG o.s.c.s.DefaultLifecycleProcessor - Bean 'applicationTaskExecutor' completed its stop procedure
2026-09-08 10:07:07.871 [SpringApplicationShutdownHook] DEBUG o.s.c.s.DefaultLifecycleProcessor - Stopping beans in phase -2147483647
2026-09-08 10:07:07.871 [SpringApplicationShutdownHook] DEBUG o.s.c.s.DefaultLifecycleProcessor - Bean 'springBootLoggingLifecycle' completed its stop procedure
2026-09-08 10:07:07.871 [SpringApplicationShutdownHook] DEBUG o.s.s.c.ThreadPoolTaskScheduler - Shutting down ExecutorService 'taskScheduler'
2026-09-08 10:07:07.872 [SpringApplicationShutdownHook] DEBUG o.s.b.c.c.s.JobRegistrySmartInitializingSingleton - Unregistering job: crawlJob
2026-09-08 10:07:07.872 [SpringApplicationShutdownHook] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Closing JPA EntityManagerFactory for persistence unit 'default'
2026-09-08 10:07:07.872 [SpringApplicationShutdownHook] DEBUG o.h.internal.SessionFactoryImpl - HHH000031: Closing
2026-09-08 10:07:07.873 [SpringApplicationShutdownHook] DEBUG o.h.type.spi.TypeConfiguration$Scope - Un-scoping TypeConfiguration [org.hibernate.type.spi.TypeConfiguration$Scope@56e4f98f] from SessionFactory [org.hibernate.internal.SessionFactoryImpl@12478b4e]
2026-09-08 10:07:07.873 [SpringApplicationShutdownHook] DEBUG o.h.s.i.AbstractServiceRegistryImpl - Implicitly destroying ServiceRegistry on de-registration of all child ServiceRegistries
2026-09-08 10:07:07.873 [SpringApplicationShutdownHook] DEBUG o.h.b.r.i.BootstrapServiceRegistryImpl - Implicitly destroying Boot-strap registry on de-registration of all child ServiceRegistries
2026-09-08 10:07:07.874 [SpringApplicationShutdownHook] INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Shutdown initiated...
2026-09-08 10:07:07.874 [SpringApplicationShutdownHook] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Before shutdown stats (total=1/2, idle=1/0, active=0, waiting=0)
2026-09-08 10:07:07.875 [HikariPool-1:connection-closer] DEBUG com.zaxxer.hikari.pool.PoolBase - HikariPool-1 - Closing connection com.mysql.cj.jdbc.ConnectionImpl@424142e4: (connection evicted)
2026-09-08 10:07:07.875 [SpringApplicationShutdownHook] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - After  shutdown stats (total=0/2, idle=0/0, active=0, waiting=0)
2026-09-08 10:07:07.876 [SpringApplicationShutdownHook] INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Shutdown completed.
2026-09-08 10:07:07.876 [SpringApplicationShutdownHook] DEBUG o.s.s.c.ThreadPoolTaskExecutor - Shutting down ExecutorService 'applicationTaskExecutor'
```
