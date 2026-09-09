# Three-source comparison (2026-09-09)

- ran `2ea27f1170c2351f6172b3f87db1b82718100000`: Java 17, Boot 3.5.3, Batch starter, JPA, Flyway V1-V8, H2 tests plus MariaDB integration tests; implemented CMS posting, canonical URLs, lease fencing and category assignment.
- legacy repository loveapple/web-crawler `ef82b07903bb7db83982ad29b38d6e95b692961b`: Java 23, Boot 3.5.3, 33 byte-identical files, 27 differing shared paths, and only `.circleci/config.yml` unique to legacy. Its Java 23/Pages/Coveralls CI is historical and scheduled for retirement; it is not imported or executed. ran has 42 additional crawler files. Source differences include parser, batch config, entities, repositories, settings and tests; ran adds CMS services, URL identity, ownership/heartbeat, migrations and test coverage.
- target `9be570be705ae1eb7f4b0826f811c5840eeed6a3`: Java 17, Boot 2.7.5-SNAPSHOT, JDBC starter, one generated application and context test, no ingestion implementation, Flyway or JPA model. Replace the bootstrap application/config/test with the ran implementation while preserving repository history; licensing is handled separately from source selection.

Selection is based on implemented behavior and its tests, not commit dates. No legacy-only business implementation was found. Dependency versions and application source are imported unchanged from ran; no schema is applied. Batch resolves through the Boot dependency platform; the migration does not introduce an independent Batch version.

Production assumptions in imported documents are historical until verified in ran-deck. Pages, Coveralls, DNS and legacy repository deletion are out of scope. Current operation is not cut over by this PR.

Priority: current operational/design fit, then ran assets, useful legacy web-crawler assets, and finally useful old target assets. Existing history/license does not give old code priority. No target bootstrap business behavior was found worth retaining. The later repository distribution decision supersedes the license retained during migration.
