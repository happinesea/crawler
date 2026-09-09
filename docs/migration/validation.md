# Migration validation

Java 17: gradlew.bat test bootJar PASS. 228 tests, 0 failures, 0 errors, 1 skipped. MariaDB integration and production/network acceptance tests not run. Runtime Java/config/SQL/test payloads are byte-identical to imported ran files. Markdown links pass.

Standard git diff --check reports inherited whitespace in imported source/content. It is not claimed as passing. Existing source formatting and Markdown hard breaks are intentionally preserved; new/unmatched whitespace is audited separately in ran migration evidence.
