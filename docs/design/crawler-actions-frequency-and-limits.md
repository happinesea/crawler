# Crawler GitHub Actions frequency and limits design

## Status

- Status: merged by PR #59 (`3294574`); the 2026-08-21 category-rotation change retains this schedule and workflow fallback contract
- Date: 2026-08-20
- Base: `origin/main` at `97fd26fa0bf44c9f9017cd8f871dd096d76bbbd0`
- Target workflow: `.github/workflows/hourly-crawl.yml`

This design reduces scheduled workflow startup frequency while retaining finite crawler and WordPress posting safety limits. GitHub-hosted execution is explicitly outside this change: no Actions workflow, production crawler, WordPress request, DB operation, or repository-setting change is used for verification.

## Required schedule

GitHub Actions cron expressions are UTC. The workflow shall contain exactly these five scheduled triggers:

| UTC cron | UTC time | JST time | Date relationship |
|---|---:|---:|---|
| `0 23 * * *` | 23:00 | 08:00 | next JST calendar day |
| `0 1 * * *` | 01:00 | 10:00 | same JST calendar day |
| `30 2 * * *` | 02:30 | 11:30 | same JST calendar day |
| `30 8 * * *` | 08:30 | 17:30 | same JST calendar day |
| `0 13 * * *` | 13:00 | 22:00 | same JST calendar day |

Each cron entry shall have an adjacent comment stating its JST time. The old hourly expression `0 * * * *` shall be removed. The existing `workflow_dispatch` and `push` triggers remain; trigger topology changes only by replacing the scheduled cron list.

The theoretical scheduled startup count changes from 24 to 5 per day, a reduction of 19 starts or `1 - 5 / 24 = 79.166...%`, reported as 79.2%. For a 31-day month, scheduled starts change from 744 to 155, a reduction of 589.

The 79.2% value describes only scheduled workflow startup count. It does not predict GitHub Actions minutes, HTTP requests, DB load, process load, runtime, or processed article count. Per-run ceilings increase, so actual runtime and load can increase and must be measured after release. The intended saving is fewer repetitions of runner startup, checkout, JDK setup, MySQL client setup, and Gradle build.

## Expression order and event matrix

The two workflow expressions retain this textual operand order:

```yaml
POST_LIMIT: ${{ inputs.post_limit || vars.POST_LIMIT || '1000' }}
CRAWL_CONTENT_LIMIT: ${{ inputs.crawl_content_limit || vars.CRAWL_CONTENT_LIMIT || '200' }}
```

This is expression order, not a universal statement that only operator-entered values occupy the first operand. For `workflow_dispatch`, GitHub supplies the declared default when an input is omitted. The delivered nonempty default is indistinguishable from another nonempty input to the expression.

The current repository has neither `CRAWL_CONTENT_LIMIT` nor `POST_LIMIT` configured as an Actions repository variable. The following matrix defines the statically expected values:

| Event and input state | Repository variables | Expected `CRAWL_CONTENT_LIMIT` | Expected `POST_LIMIT` | Reason |
|---|---|---:|---:|---|
| `schedule` | both unset | `200` | `1000` | `inputs` is unavailable for a scheduled event; unset `vars` resolves empty, then literals win |
| `push` | both unset | `200` | `1000` | no dispatch inputs; unset variables fall through to literals |
| dispatch with explicit crawl `17` and post `23` | any values | `17` | `23` | both nonempty input operands win |
| dispatch omits crawl and post, both variables unset | both unset | `200` | `1000` | GitHub supplies crawl default `200`; post default is empty and falls through to literal `1000` |
| dispatch omits crawl and post | crawl variable `88`, post variable `99` | `200` | `99` | crawl default `200` masks `vars.CRAWL_CONTENT_LIMIT`; empty post default allows `vars.POST_LIMIT` |
| dispatch explicitly sends crawl `1` and post `1` | any values | `1` | `1` | bounded manual values win |

Therefore the literal chain still reads input, variable, fallback, but repository-variable behavior differs by input declaration. In particular, the mandated nonempty dispatch default `crawl_content_limit=200` means an omitted crawl input does not allow a future `vars.CRAWL_CONTENT_LIMIT` value to win. Changing that behavior would require an empty or sentinel dispatch default and is outside this requirement. The optional `post_limit` keeps an empty default, so `vars.POST_LIMIT` can win when no nonempty post input is supplied.

For scheduled execution, `200/1000` is a static expectation based on the workflow expression, current unset variables, and documented context behavior. GitHub-hosted execution is `NOT RUN` under the zero-Actions constraint, so this work does not claim runtime proof from a hosted schedule.

## Throughput and bounded-operation behavior

Crawler business logic, filtering order, repair branching, and topology remain unchanged. Effective throughput intentionally increases for events that consume the changed defaults or literals:

- a scheduled or push event with the currently unset variables changes from crawl/post ceilings `3/10` to `200/1000`;
- a normal dispatch that accepts the crawl default and leaves post empty with no post variable also resolves to `200/1000`;
- `post_featured_image_only=true` still filters eligible rows before applying `POST_LIMIT`, so a limited featured-image check must explicitly pass `post_limit=1`;
- bounded manual, featured-image-only, and repair operations must explicitly pass `crawl_content_limit=1` and `post_limit=1` when one-item behavior is required; operators must not rely on the new normal defaults for a bounded run.

The repair statement is based only on current source evidence. `CrawlerComponents` checks `isCmsRepairConfigured()` and returns from the repair branch before normal category loading. `SiteContentsService.saveAllProcessPools()` also branches to `repairConfiguredCmsContent()` before `findContents4Post()` and before `postContentsLimitCount` is applied. Thus the current repair branch bypasses normal article loading and normal post-list limiting, while the workflow still resolves the environment values. This is a static source conclusion, not a production execution result. Explicit `1/1` remains required in the approved repair recipe to keep workflow intent bounded and protect against future branch changes.

The limits remain finite safety valves. `200` and `1000` are not an unlimited mode and do not alter selection, duplicate detection, process-pool ownership, AI, featured-image policy, retry, or WordPress request construction.

## Validation behavior

The existing workflow preflight validation for `POST_LIMIT` remains unchanged. It accepts values matching `^[1-9][0-9]*$` and rejects malformed values, zero, and negative values before crawler execution. Existing target SiteInfo/category validation also remains unchanged. This change does not add a new validator or broaden validation semantics for unrelated inputs.

## Java and Spring defaults inventory

All application and injection defaults remain at the existing value `10`:

| File | Property or injection | Retained definition | Effective default when workflow env is absent |
|---|---|---|---:|
| `src/main/resources/application.yml` | post limit | `post-contents-limit-count: ${POST_LIMIT:10}` | `10` |
| `src/main/resources/application.yml` | process limit | `crawl-process-limit-count: ${CRAWL_PROCESS_LIMIT:10}` | `10` |
| `src/main/resources/application.yml` | content limit | `crawl-content-limit-count: ${CRAWL_CONTENT_LIMIT:${POST_LIMIT:10}}` | `10` |
| `src/main/resources/application-prod.yml` | post limit | `post-contents-limit-count: ${POST_LIMIT:10}` | `10` |
| `src/main/resources/application-prod.yml` | process limit | `crawl-process-limit-count: ${CRAWL_PROCESS_LIMIT:10}` | `10` |
| `src/main/resources/application-prod.yml` | content limit | `crawl-content-limit-count: ${CRAWL_CONTENT_LIMIT:${POST_LIMIT:10}}` | `10` |
| `CrawlerComponents` | content injection | `@Value("${web-crawler.crawl-content-limit-count:10}")` | `10` |
| `BatchConfig` | process injection | `@Value("${web-crawler.crawl-process-limit-count:10}")` | `10` |
| `SiteContentsService` | post injection | `@Value("${web-crawler.post-contents-limit-count}")` | `10` through either application profile property above |

The scheduled workflow supplies explicit process, content, and post values through its environment. Java defaults remain 10 for workflow-external safety. Since `CRAWL_PROCESS_LIMIT` now means logical worker count, it no longer inherits `POST_LIMIT`; `CRAWL_CONTENT_LIMIT -> POST_LIMIT` remains unchanged for compatibility.

## Scope and documentation consistency

The reviewed implementation allowlist is:

- `.github/workflows/hourly-crawl.yml` for the five crons, comments, input default/description, and literal fallbacks;
- [`ran-deck` operating evidence](https://github.com/happinesea/ran-deck/blob/main/docs/operations/web-crawler-production-release-2026-08-05.md) for planned and then locally verified operating evidence;
- `tasks/README.md` for canonical task state;
- `docs/design/crawler-actions-frequency-and-limits.md`;
- `docs/plans/2026-08-20-crawler-actions-frequency-and-limits.md`;
- `docs/testing/crawler-actions-frequency-and-limits-test-spec.md`;
- `docs/testing/web-crawler-test-spec.md`, limited to changing PROD-011's workflow fallback expectation from `10` to `1000`.

The last path is required because PROD-011 is an existing canonical workflow contract. Leaving its literal at `10` would contradict the changed workflow and the new focused test specification. This documentation-only correction does not change the Java/Spring default of `10`.

No other tracked or untracked path is allowed. In particular, this work does not change or execute Java/Spring code, crawler business logic, Spring Batch, DB schema/data, WordPress, repository variables/secrets/settings, production infrastructure, deployment topology, workflows, dispatches, or reruns.
