# Crawler Actions frequency and limits test specification

## 1. Purpose and status

This specification defines the local, static acceptance tests used for PR #59's crawler workflow schedule and limit changes. PR #59 is merged. The later category-rotation design may change Java/Spring resources where its own design and tests explicitly require it.

Source evidence:

- `docs/design/crawler-actions-frequency-and-limits.md`
- `docs/plans/2026-08-20-crawler-actions-frequency-and-limits.md`
- `.superpowers/sdd/2026-08-20-crawler-actions-frequency-and-limits/design-test-review.md`
- `.github/workflows/hourly-crawl.yml` on `origin/main`
- the Java/Spring defaults and repair branches named in sections 9 and 10

All tests are local and non-production. They must execute zero GitHub Actions workflows, dispatches, or reruns and must not execute the crawler, contact WordPress, access a production DB, or change repository variables or GitHub settings.

## 2. Acceptance boundary

For PR #59 itself, the final feature-level union of tracked and untracked changes was limited to exactly these paths:

```text
.github/workflows/hourly-crawl.yml
docs/design/crawler-actions-frequency-and-limits.md
Operational evidence: <https://github.com/happinesea/ran-deck/blob/main/docs/operations/web-crawler-production-release-2026-08-05.md>
docs/plans/2026-08-20-crawler-actions-frequency-and-limits.md
docs/testing/crawler-actions-frequency-and-limits-test-spec.md
docs/testing/web-crawler-test-spec.md
tasks/README.md
```

For that PR, `docs/testing/web-crawler-test-spec.md` was allowed only for changing PROD-011's workflow literal fallback from `10` to `1000`. Java/Spring defaults remained `10`. This historical path boundary does not prohibit the approved category-rotation implementation and its tests.

No Java, Spring resource, DB, crawler, WordPress, GitHub setting, repository-variable, Matt Pocock setup, generated dependency, or unrelated workflow path is allowed.

The existing fail-fast rule covered here is the current `POST_LIMIT` check using `^[1-9][0-9]*$`. This specification does not add a `CRAWL_CONTENT_LIMIT` validator. It verifies that the existing preflight block, including SiteInfo/category validation, is unchanged.

## 3. Evidence limits

Static and local evidence can prove:

- YAML parses and contains the required trigger, input, expression, and job structure;
- exact cron text, adjacent JST comments, UTC-to-JST arithmetic, and five triggers per complete JST day;
- the expected result of the event/input matrix under the explicitly supplied string values;
- dispatch declarations, expression operand order, retained preflight text, unrelated inputs/env/steps, and repair/featured-image source ordering;
- the exact Java/Spring default inventory and absence of Java/resource diffs;
- local Gradle tests, `bootJar`, scope, staged-path, and whitespace checks.

Static and local evidence cannot prove:

- hosted GitHub expression evaluation, hosted event payloads, or actual scheduled values;
- that GitHub fires all five scheduled jobs at the exact wall-clock times;
- production crawler, repair, featured-image, WordPress, DB, or runner behavior;
- actual article/post counts from a ceiling value;
- a 79.2% decrease in Actions minutes, HTTP/DB load, runtime, or processed volume;
- that no external actor ran a workflow; the execution audit proves only that this task issued no run command.

The matrix helper is a conceptual model for the reviewed empty/nonempty string operands. For dispatch, omission is modeled with the YAML-declared default delivered as the first operand: crawl omission delivers `200`, while post omission delivers an empty string. For schedule and push, inputs are modeled as empty/unavailable. Schedule and push `200/1000` are **statically expected** only; hosted execution is `NOT RUN`.

Source-order assertions show the current repair and featured-image branches appear before the normal paths they bypass or filter. They do not prove runtime execution, side effects, or production behavior.

## 4. Complete immutable fixture

Create one fixture only at `$env:TEMP\validate-crawler-actions-workflow.py`. It must be complete before its first execution and must accept exactly these parameters:

```text
--candidate <workflow YAML path>
--baseline <origin/main workflow YAML path>
--repo-root <worktree root>
```

Export `origin/main:.github/workflows/hourly-crawl.yml` to `$env:TEMP\hourly-crawl-origin-main.yml` as UTF-8 without BOM. The fixture reads candidate and baseline as UTF-8, parses both with the same local YAML parser, collects all failures, and exits nonzero only after reporting every failed assertion.

Before the first run, the fixture must contain every assertion used by SCH-001 through SCH-005, MAT-001 through MAT-007, VAL-001 through VAL-005, REG-001 through REG-012, and JVM-001 through JVM-006. This includes:

- exact cron count/order, old-hourly absence, cron grammar, adjacent JST comments, date rollover, and five-per-JST-day grouping;
- crawl/post dispatch defaults and descriptions, exact env expression text, and the complete event/input matrix;
- normalized candidate-versus-baseline workflow comparison allowing only schedule, crawl dispatch default, post description, and the two env-expression changes;
- retained input/env keys, complete preflight retention, positive-integer behavior, repair branch ordering, and featured-image filtering before `POST_LIMIT`;
- every exact Java/Spring default string listed in section 10.

Use this conceptual helper only for the matrix's explicitly listed string values:

```python
def resolve(input_value, repository_value, literal):
    return input_value or repository_value or literal
```

Record the fixture SHA-256 before RED. Do not add, remove, or weaken assertions after that point. Run the exact same fixture as follows:

```text
RED:   candidate = origin/main export; baseline = origin/main export
GREEN: candidate = worktree workflow; baseline = same origin/main export
```

The fixture path, bytes/SHA-256, baseline, parser, and repo-root stay identical; only `--candidate` changes. RED must include all enumerated baseline mismatches in FIX-002. Import, parse, source-read, or fixture defects are not acceptable RED evidence. The documentation diff, repository scope, staged paths, whitespace, Gradle, and safety gates remain separate objective checks because they inspect artifacts beyond the candidate workflow contract.

No dependency, fixture, lockfile, or generated test artifact may be added to the repository. If the default Python lacks YAML support, use an already bundled workspace interpreter rather than changing repository or global dependencies.

## 5. Schedule test cases

| ID | Purpose | Precondition | Input | Verification | Expected result | Execution method |
| --- | --- | --- | --- | --- | --- | --- |
| SCH-001 | Require exactly the five designed schedules | Implemented worktree YAML parses | Parsed `on.schedule` | Extract every cron value and compare ordered values and count | Exactly `0 23 * * *`, `0 1 * * *`, `30 2 * * *`, `30 8 * * *`, `0 13 * * *` in that order; count 5; no extras | Run the immutable fixture against the worktree candidate |
| SCH-002 | Remove the old hourly trigger | SCH-001 source available | Parsed schedule and raw text | Search for `0 * * * *` | Zero occurrences in the schedule | Run the immutable fixture |
| SCH-003 | Validate cron grammar | SCH-001 values extracted | Five cron strings | Require five fields; minutes `0` or `30` in range, hours `1`, `2`, `8`, `13`, or `23` in range, and the other fields `*` | All five are valid daily GitHub Actions POSIX cron forms | Run fixture grammar assertions; record an already available workflow linter separately if used |
| SCH-004 | Verify UTC-to-JST mapping and comments | Fixed JST offset UTC+09:00 | One fixed UTC date and the five times | Add nine hours and compare each result with its adjacent comment and date | `23:00 UTC -> 08:00 JST` next day; `01:00 -> 10:00`, `02:30 -> 11:30`, `08:30 -> 17:30`, `13:00 -> 22:00` same day | Use Python `datetime` with fixed UTC+09:00 in the immutable fixture |
| SCH-005 | Prove five scheduled triggers per JST day | SCH-004 passes | Five UTC times over three consecutive dates | Convert to JST, group by JST date, and count the middle complete date | Exactly five triggers on the complete JST date; theoretical count is 24/day -> 5/day | Run fixture multi-day grouping assertion |

## 6. Event and input matrix test cases

The fixture requires these exact expression strings while evaluating the event-specific operands below:

```yaml
POST_LIMIT: ${{ inputs.post_limit || vars.POST_LIMIT || '1000' }}
CRAWL_CONTENT_LIMIT: ${{ inputs.crawl_content_limit || vars.CRAWL_CONTENT_LIMIT || '200' }}
```

| ID | Purpose | Precondition | Input | Verification | Expected result | Execution method |
| --- | --- | --- | --- | --- | --- | --- |
| MAT-001 | Model scheduled values with current variables unset | Repository-setting evidence states both variables are unset; hosted run prohibited | Event `schedule`; input values empty/unavailable; repository values empty; literals `200` and `1000` | Inspect exact expressions and evaluate both conceptual chains | Statically expected `CRAWL_CONTENT_LIMIT=200`, `POST_LIMIT=1000`; hosted result `NOT RUN` | Run immutable fixture and record unset-variable evidence separately |
| MAT-002 | Model push values with current variables unset | Both variables unset | Event `push`; input values empty/unavailable; repository values empty | Evaluate both conceptual chains | Statically expected `200/1000`; hosted result `NOT RUN` | Run immutable fixture; do not push as a test |
| MAT-003 | Model explicit dispatch values | Dispatch declarations and expressions match design | Crawl input `17`, post input `23`, repository values `88/99` | Evaluate delivered nonempty input operands | Result `17/23`; both repository values are masked | Run immutable fixture only, not hosted dispatch |
| MAT-004 | Model dispatch omission with variables unset | Crawl default is `200`; post default is empty | Delivered crawl input `200`, delivered post input empty, repository values empty | Evaluate both chains | Result `200/1000` | Run immutable fixture |
| MAT-005 | Prove omitted/defaulted crawl masks repository variable | Crawl default is `200` | Delivered crawl input `200`, crawl repository value `88`, literal `200` | Evaluate crawl chain | Result `200`; repository value `88` does not win | Run immutable fixture; do not describe this as universal repository precedence |
| MAT-006 | Prove empty post input allows variable or fallback | Post default is empty | Case A: post input empty, repository `99`; case B: both empty | Evaluate post chain twice | Case A result `99`; case B result `1000` | Run immutable fixture |
| MAT-007 | Preserve bounded explicit dispatch values | Explicit values are accepted by current validation | Crawl input `1`, post input `1`, any repository values | Evaluate both chains and positive-integer check | Result `1/1`; both values accepted | Run immutable fixture |

## 7. Validation and fail-fast test cases

| ID | Purpose | Precondition | Input | Verification | Expected result | Execution method |
| --- | --- | --- | --- | --- | --- | --- |
| VAL-001 | Keep existing fail-fast implementation unchanged | Baseline and candidate parse | Entire `Check required secrets` step | Compare name, id, shell, and run script to baseline after structured YAML parsing | Byte-equivalent script content, including target-ID checks, POST_LIMIT regex/message, secret checks, and exits | Immutable fixture extracts and compares the complete named step |
| VAL-002 | Reject malformed positive integer | Existing POST_LIMIT regex retained | `POST_LIMIT=abc` | Apply `^[1-9][0-9]*$` | Rejected before crawler execution | Immutable fixture regex assertion; optional local Bash confirmation only |
| VAL-003 | Reject zero | Same as VAL-002 | `POST_LIMIT=0` | Apply retained regex | Rejected | Immutable fixture |
| VAL-004 | Reject negative integer | Same as VAL-002 | `POST_LIMIT=-1` | Apply retained regex | Rejected | Immutable fixture |
| VAL-005 | Accept valid positive integers | Same as VAL-002 | `1`, `200`, `1000` | Apply retained regex to each | All accepted | Immutable fixture |

## 8. Workflow and bounded-operation regression cases

| ID | Purpose | Precondition | Input | Verification | Expected result | Execution method |
| --- | --- | --- | --- | --- | --- | --- |
| REG-001 | Retain dispatch and push topology | Baseline and candidate parse | Top-level `on` mapping | Normalize only five reviewed fields and compare remaining workflow to baseline | `workflow_dispatch` and `push` remain; only scheduled list changes topology | Immutable fixture normalized comparison |
| REG-002 | Preserve SiteInfo/category targeting and validation | Same as REG-001 | SiteInfo/category inputs, env mappings, and preflight calls | Compare complete input objects, env expressions, and preflight text | Empty defaults, mappings, comma-separated integer validation, and fail-fast remain unchanged | Immutable fixture |
| REG-003 | Record normal dispatch throughput change | Both repository variables unset; dispatch fields omitted | GitHub-delivered crawl default `200` and empty post default | Apply MAT-004 and inspect declarations | Normal dispatch is statically expected to resolve `200/1000`; this is intentional, not unchanged throughput | Immutable fixture; hosted dispatch `NOT RUN` |
| REG-004 | Preserve bounded normal dispatch recipe | Operator requires one-item ceilings | Explicit crawl `1`, post `1` | Apply MAT-007 and validation | Statically expected `1/1`; bounded runs must not rely on normal defaults | Immutable fixture; no crawler run |
| REG-005 | Preserve featured-image-only surface | Baseline and candidate parse | `post_featured_image_only` input and `POST_FEATURED_IMAGE_ONLY` env | Compare complete input object and env expression | Choice/options/default and mapping unchanged | Immutable fixture normalized comparison |
| REG-006 | Preserve bounded featured-image-only recipe and order | One-item featured-image check requested | `post_featured_image_only=true`, explicit crawl `1`, explicit post `1` | Resolve values and assert `postFeaturedImageOnly` filtering appears before `Math.min(..., postContentsLimitCount)` | Statically expected `1/1`; source still filters eligible rows before applying post limit | Immutable fixture source-order and matrix assertions; runtime `NOT RUN` |
| REG-007 | Preserve repair dispatch surface | Baseline and candidate parse | `repair_cms_content_id` input and `REPAIR_CMS_CONTENT_ID` env | Compare complete input object and env expression | Description/default/mapping unchanged | Immutable fixture normalized comparison |
| REG-008 | Preserve bounded repair recipe | Approved repair intent is one-item bounded | Nonempty repair ID, `skip_cms_post=false`, explicit crawl `1`, explicit post `1` | Resolve values and validate explicit limits | Statically expected `1/1`; explicit values remain required as an operator guard | Immutable fixture; no repair execution |
| REG-009 | Prove repair bypasses normal crawl path in current source | Current `CrawlerComponents.java` available | `isCmsRepairConfigured()` branch and normal category load | Assert repair check/return occurs before `getSiteCategory()` and `loadCategoryContentsList()` | Current repair branch returns before normal category loading | Immutable fixture source-order assertion; static evidence only |
| REG-010 | Prove repair bypasses normal post selection/limit in current source | Current `SiteContentsService.java` available | `saveAllProcessPools`, repair branch, `findContents4Post`, and `postContentsLimitCount` application | Assert repair branch/return occurs before normal post query and limit calculation | Current repair branch bypasses normal post candidate selection and post-limit application | Immutable fixture source-order assertion; static evidence only |
| REG-011 | Preserve CMS skip, AI mode, and unrelated env | Baseline and candidate parse | Full crawl env map | Compare every env key/value except the two reviewed limit expressions | `SKIP_CMS_POST`, `AI_MODE`, secrets, variables, literals, and all unrelated env remain identical | Immutable fixture normalized comparison |
| REG-012 | Preserve unrelated job and step semantics | Baseline and candidate parse | Job configuration and ordered steps | Compare after masking only five reviewed fields | No runner, permission, concurrency, timeout, defaults, condition, action, shell, build, crawl, acceptance, status, or commit-step change | Immutable fixture normalized comparison |

## 9. Canonical test-document consistency

| ID | Purpose | Precondition | Input | Verification | Expected result | Execution method |
| --- | --- | --- | --- | --- | --- | --- |
| DOC-001 | Permit only the PROD-011 workflow fallback correction | Baseline `docs/testing/web-crawler-test-spec.md` is available | Baseline and worktree file with normalized LF | In memory, replace only PROD-011 text `未設定なら` plus literal `10` with literal `1000`; compare the complete expected string to worktree | Entire file matches except PROD-011 workflow literal `10 -> 1000`; PROD-011 explicit `post_limit=1` remains; Java/Spring defaults remain `10` per JVM-001 through JVM-006 | Run exact whole-file normalized comparison; fail on any other byte-normalized difference |

## 10. Java/Spring default inventory and cases

The retained inventory is:

| File | Exact retained definition | Effective default without workflow env |
| --- | --- | --- |
| `application.yml` | `post-contents-limit-count: ${POST_LIMIT:10}` | `10` |
| `application.yml` | `crawl-process-limit-count: ${CRAWL_PROCESS_LIMIT:10}` | `10` |
| `application.yml` | `crawl-content-limit-count: ${CRAWL_CONTENT_LIMIT:${POST_LIMIT:10}}` | `10` |
| `application-prod.yml` | `post-contents-limit-count: ${POST_LIMIT:10}` | `10` |
| `application-prod.yml` | `crawl-process-limit-count: ${CRAWL_PROCESS_LIMIT:10}` | `10` |
| `application-prod.yml` | `crawl-content-limit-count: ${CRAWL_CONTENT_LIMIT:${POST_LIMIT:10}}` | `10` |
| `CrawlerComponents` | `@Value("${web-crawler.crawl-content-limit-count:10}")` | Injection fallback `10` |
| `BatchConfig` | `@Value("${web-crawler.crawl-process-limit-count:10}")` | Injection fallback `10` |
| `SiteContentsService` | `@Value("${web-crawler.post-contents-limit-count}")` | Profile property above supplies `10` |

| ID | Purpose | Precondition | Input | Verification | Expected result | Execution method |
| --- | --- | --- | --- | --- | --- | --- |
| JVM-001 | Retain default-profile post/process/content safety values | `application.yml` available | Three exact property strings in inventory | Require each exact string once in the profile block | All defaults remain `10`; process fallback is independent of `POST_LIMIT` | Config unit test and exact-string assertion |
| JVM-002 | Retain production-profile post/process/content safety values | `application-prod.yml` available | Three exact property strings in inventory | Require each exact string once in the profile block | All defaults remain `10`; process fallback is independent of `POST_LIMIT` | Config unit test and exact-string assertion |
| JVM-003 | Retain CrawlerComponents content injection fallback | Source available | Exact `@Value` from inventory | Exact-string assertion | Fallback remains `10` | Immutable fixture |
| JVM-004 | Retain BatchConfig process injection fallback | Source available | Exact `@Value` from inventory | Exact-string assertion | Fallback remains `10` | Immutable fixture |
| JVM-005 | Retain SiteContentsService post injection | Source and both profiles available | Exact property-only `@Value` and profile post defaults | Assert injection has no new literal and both profiles supply `POST_LIMIT:10` | Effective non-workflow post default remains `10` | Immutable fixture |
| JVM-006 | Prove PR #59 itself had no Java/resource implementation diff | PR #59 baseline and merge available | `src/main/java` and `src/main/resources` at PR #59 | Compare that historical PR range | No Java/resource diff in PR #59; later category-rotation changes are evaluated by their own specification | Compare PR #59 commit range only |

## 11. Fixture, static, build, scope, and safety cases

| ID | Purpose | Precondition | Input | Verification | Expected result | Execution method |
| --- | --- | --- | --- | --- | --- | --- |
| FIX-001 | Freeze a complete fixture before first execution | All fixture-owned cases in section 4 are implemented | Fixture bytes and assertion-ID inventory | Record SHA-256 and confirm every required assertion exists before running candidate | Complete inventory present; no later fixture edit allowed | Hash fixture and save assertion-ID list outside Git |
| FIX-002 | Establish objective baseline RED | FIX-001 frozen; parser and source reads work | Candidate and baseline both origin/main export | Run complete fixture and collect all failures | Nonzero exit including schedule count/exact/hourly/comments, crawl default, post description, and both env-literal mismatches; no import/parse/source defect | Run frozen fixture with origin/main candidate |
| FIX-003 | Reach GREEN with exact same fixture | FIX-002 is valid; implementation present | Candidate worktree workflow; identical baseline/repo-root/parser/fixture | Verify fixture SHA-256 unchanged, then rerun | Exit 0 with every fixture-owned case passing | Run frozen fixture changing only candidate argument |
| FIX-004 | Keep fixture and dependencies ephemeral | Fixture evidence recorded | Temporary fixture, baseline export, and runtime files | Remove temporary files and inspect repository | No fixture, dependency, lockfile, or generated support file in repository | Delete temporary files; inspect status and untracked list |
| STA-001 | Parse YAML syntax and contract types | Local YAML parser available without repository changes | Worktree workflow | Parse document and inspect mappings/lists for `on`, schedule, dispatch, jobs, and env | Parse succeeds with expected contract types | Frozen fixture with explicit UTF-8 reading |
| STA-002 | Perform complete workflow static validation | STA-001 passes | Parsed workflow and raw text | Execute all fixture-owned cases | All static assertions pass | FIX-003 GREEN; optional existing `actionlint` result may be supplementary only |
| BLD-001 | Detect Java test regressions | JDK 17 selected; production profile not run | Current crawler tests | Run Gradle tests | `BUILD SUCCESSFUL`; tests pass | From `web-crawler`, run `.\gradlew.bat test --stacktrace --no-daemon --console=plain` |
| BLD-002 | Verify boot jar builds locally | JDK 17 selected | Current crawler source | Run `bootJar` and verify jar exists without executing it | `BUILD SUCCESSFUL`; boot jar created and not run | From `web-crawler`, run `.\gradlew.bat bootJar --stacktrace --no-daemon --console=plain` |
| GIT-001 | Enforce objective final path allowlist | Final implementation/docs present | Tracked paths from `git diff --name-only origin/main --`; untracked from `git ls-files --others --exclude-standard` | Normalize slashes, union, sort unique, compare with exact seven-path list; calculate outside and missing sets | Union equals allowlist; any outside or missing path fails; no Matt Pocock or unrelated path | Run an ephemeral PowerShell checker that throws on `Compare-Object` delta |
| GIT-002 | Check tracked and untracked whitespace before staging | GIT-001 path set available | Tracked diff and each allowed untracked UTF-8 file | Run Git check for tracked files; scan untracked files for trailing spaces/tabs and final LF | No whitespace error; every untracked text file ends with LF | Run `git diff --check` plus explicit untracked-file scan in scope checker |
| GIT-003 | Enforce exact staged allowlist | Controller has explicitly staged only reviewed files | `git diff --cached --name-only` | Normalize/sort and compare to exact seven-path allowlist | Exact match; no extra or missing staged path | Run staged path checker after explicit path-by-path `git add`; never use `git add -A` |
| GIT-004 | Check staged whitespace | GIT-003 passes | Staged diff | Run Git staged whitespace check | Exit 0 with no whitespace errors | Run `git diff --cached --check` before commit |
| SAFE-001 | Guarantee zero task-triggered Actions activity | Executors follow local-only boundary | Command/tool audit | Search for dispatch, rerun, workflow-run, push-trigger, or repository-setting write operations | None issued by this work; hosted cases remain `NOT RUN` | Review execution logs; do not invoke a workflow as a test |
| SAFE-002 | Guarantee zero production crawler, WordPress, and DB activity | Only static, regex, Gradle test, and build tasks allowed | Command/tool and Gradle-task audit | Search for jar/bootRun execution, prod profile, WordPress calls, MySQL/DB operations, migrations, or production credentials | None occurred; building `bootJar` is allowed but running it is not | Review execution logs; any production-side operation is immediate FAIL |

## 12. Required execution order and verdict

1. Record branch, `origin/main` SHA, and baseline `git status --short`.
2. Create the complete fixture, verify FIX-001, and execute FIX-002 before workflow implementation.
3. After implementation, execute FIX-003, STA-001, STA-002, SCH-001 through SCH-005, MAT-001 through MAT-007, VAL-001 through VAL-005, and REG-001 through REG-012.
4. Execute DOC-001, JVM-001 through JVM-006, BLD-001, BLD-002, GIT-001, and GIT-002.
5. After explicit staging by the controller, execute GIT-003 and GIT-004.
6. Execute SAFE-001 and SAFE-002, record hosted execution as `NOT RUN`, then remove temporary files and execute FIX-004.

Each case is PASS, FAIL, or NOT RUN. An unexecuted case must not be reported as PASS. Any hosted Actions run or production-side activity caused by testing is a safety failure, not additional evidence. Final local acceptance is PASS only when every mandatory local case passes, the first frozen fixture run is valid RED, the same fixture is GREEN against the worktree, scope/staged gates pass, and both safety cases pass.
