> Historical. Not current source of truth. Original paths describe the pre-migration ran layout. CI/CD follow-up belongs to ran-deck / infrastructure review.

# Crawler Actions Frequency and Limits Implementation Plan

> Historical path note: `docs/operations/**` references below describe the
> original `loveapple/ran` layout. Current operational documents are owned by
> private [`happinesea/ran-deck`](https://github.com/happinesea/ran-deck/tree/main/docs/operations).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Reduce scheduled crawler workflow starts from 24 to 5 per day and set workflow-level content/post safety ceilings to 200/1000 without changing Java, crawler, DB, WordPress, or production state.

**Architecture:** Keep the current trigger and expression topology. Change only the scheduled cron list and requested workflow literals, then evaluate the complete static contract against both origin/main and the worktree with one parameterized fixture. Preserve Java defaults so non-workflow execution remains at 10.

**Tech Stack:** GitHub Actions YAML, GitHub expression strings, PowerShell 7, Python 3 with a local YAML parser, Gradle Wrapper 8.11.1, JDK 17.

**Spec:** docs/design/crawler-actions-frequency-and-limits.md

## Global Constraints

- Base is origin/main SHA 97fd26fa0bf44c9f9017cd8f871dd096d76bbbd0.
- Worktree is C:/projects/ran-worktrees/crawler-actions-frequency-limits on branch codex/crawler-actions-frequency-limits.
- Execute zero GitHub Actions workflows, dispatches, reruns, or production crawlers.
- Do not make a WordPress request, DB change, repository-variable change, merge, or production deployment.
- Keep workflow_dispatch, push, existing fail-fast code, unrelated inputs/env/steps, Java defaults, crawler logic, Batch, DB, WordPress, and topology unchanged.
- Treat schedule/push 200/1000 as a static expectation. Hosted execution remains NOT RUN.
- Require explicit crawl_content_limit=1 and post_limit=1 for bounded manual, featured-image-only, and repair recipes.
- Use C:/Program Files/Java/jdk-17 for Gradle validation.
- Agent 4 implements without commit/push/PR. The controller publishes only after Agent 5 and Agent 6 pass.

## Exact allowed paths

The final union of tracked and untracked changes must equal this allowlist:

1. .github/workflows/hourly-crawl.yml
2. docs/design/crawler-actions-frequency-and-limits.md
3. docs/operations/web-crawler-production-release-2026-08-05.md
4. docs/plans/2026-08-20-crawler-actions-frequency-and-limits.md
5. docs/testing/crawler-actions-frequency-and-limits-test-spec.md
6. docs/testing/web-crawler-test-spec.md
7. tasks/README.md

The existing docs/testing/web-crawler-test-spec.md is included only to change PROD-011's workflow literal fallback from 10 to 1000. Without that one-line correction, the canonical workflow contract would contradict the implementation. Its Java default statements remain 10. No other tracked or untracked path is allowed.

---

### Task 1: Create the complete parameterized RED/GREEN fixture

**Files:**

- Read: .github/workflows/hourly-crawl.yml
- Read: Java/Spring default and repair-branch sources listed below
- Create outside Git: $env:TEMP/validate-crawler-actions-workflow.py
- Create outside Git: $env:TEMP/hourly-crawl-origin-main.yml

**Interfaces:**

- Consumes the reviewed design and focused test specification.
- Produces one immutable fixture that validates either a baseline candidate or a worktree candidate.

- [ ] **Step 1: Select a local Python with YAML support**

First run:

~~~powershell
$python = (Get-Command python).Source
& $python -c "import sys, yaml; print(sys.executable); print(yaml.__version__)"
[IO.File]::WriteAllText(
  (Join-Path $env:TEMP 'crawler-actions-python.txt'),
  $python,
  [Text.Encoding]::ASCII
)
~~~

If the default Python lacks yaml, call codex_app__load_workspace_dependencies, assign its bundled interpreter path to $python, repeat the import command, and write that successful interpreter path to crawler-actions-python.txt. Do not add a repository dependency or install a global package.

- [ ] **Step 2: Export the exact baseline workflow bytes**

Run from the worktree root:

~~~powershell
$baseline = Join-Path $env:TEMP 'hourly-crawl-origin-main.yml'
$text = (git show origin/main:.github/workflows/hourly-crawl.yml) -join [Environment]::NewLine
[IO.File]::WriteAllText($baseline, $text + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
~~~

- [ ] **Step 3: Write every assertion before the first execution**

Create $env:TEMP/validate-crawler-actions-workflow.py with the complete content below. Do not add assertions after observing RED.

~~~python
from __future__ import annotations

import argparse
import copy
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import yaml

parser = argparse.ArgumentParser()
parser.add_argument("--candidate", required=True)
parser.add_argument("--baseline", required=True)
parser.add_argument("--repo-root", required=True)
args = parser.parse_args()

candidate_path = Path(args.candidate)
baseline_path = Path(args.baseline)
repo = Path(args.repo_root)
candidate_text = candidate_path.read_text(encoding="utf-8")
baseline_text = baseline_path.read_text(encoding="utf-8")
candidate = yaml.safe_load(candidate_text)
baseline = yaml.safe_load(baseline_text)
failures: list[str] = []


def check(condition: bool, test_id: str, detail: str) -> None:
    if not condition:
        failures.append(f"{test_id}: {detail}")


expected_crons = [
    "0 23 * * *",
    "0 1 * * *",
    "30 2 * * *",
    "30 8 * * *",
    "0 13 * * *",
]
expected_comments = [
    ("0 23 * * *", "08:00 JST (next calendar day)"),
    ("0 1 * * *", "10:00 JST"),
    ("30 2 * * *", "11:30 JST"),
    ("30 8 * * *", "17:30 JST"),
    ("0 13 * * *", "22:00 JST"),
]

trigger = candidate.get("on", {})
schedule = trigger.get("schedule", [])
crons = [item.get("cron") for item in schedule if isinstance(item, dict)]
check(len(crons) == 5, "SCHED-COUNT", f"expected 5, got {crons}")
check(crons == expected_crons, "SCHED-EXACT", f"unexpected order/content: {crons}")
check("0 * * * *" not in crons, "SCHED-HOURLY", "old hourly cron remains")

comment_rows: list[tuple[str, str]] = []
for line in candidate_text.splitlines():
    match = re.match(r'^\s*-\s+cron:\s+"([^"]+)"\s+#\s+(.+?)\s*$', line)
    if match:
        comment_rows.append((match.group(1), match.group(2)))
check(
    comment_rows == expected_comments,
    "SCHED-COMMENTS",
    f"cron/JST comments are not adjacent and exact: {comment_rows}",
)


def cron_utc_datetime(cron: str, utc_day: datetime) -> datetime | None:
    fields = cron.split()
    if len(fields) != 5 or not fields[0].isdigit() or not fields[1].isdigit():
        return None
    minute = int(fields[0])
    hour = int(fields[1])
    if not 0 <= minute <= 59 or not 0 <= hour <= 23:
        return None
    return datetime(
        utc_day.year,
        utc_day.month,
        utc_day.day,
        hour,
        minute,
        tzinfo=timezone.utc,
    )


jst = timezone(timedelta(hours=9))
anchor_utc_day = datetime(2026, 8, 20, tzinfo=timezone.utc)
mapped_jst: dict[str, datetime] = {}
for cron in crons:
    utc_run = cron_utc_datetime(cron, anchor_utc_day)
    if utc_run is not None:
        mapped_jst[cron] = utc_run.astimezone(jst)

expected_mapping = {
    "0 23 * * *": datetime(2026, 8, 21, 8, 0, tzinfo=jst),
    "0 1 * * *": datetime(2026, 8, 20, 10, 0, tzinfo=jst),
    "30 2 * * *": datetime(2026, 8, 20, 11, 30, tzinfo=jst),
    "30 8 * * *": datetime(2026, 8, 20, 17, 30, tzinfo=jst),
    "0 13 * * *": datetime(2026, 8, 20, 22, 0, tzinfo=jst),
}
check(
    mapped_jst == expected_mapping,
    "SCHED-UTC-JST-ARITHMETIC",
    f"UTC+09 mapping differs: {mapped_jst}",
)
next_day_run = mapped_jst.get("0 23 * * *")
check(
    next_day_run is not None
    and next_day_run.date() == (anchor_utc_day + timedelta(days=1)).date()
    and (next_day_run.hour, next_day_run.minute) == (8, 0),
    "SCHED-23-NEXT-JST-DAY",
    f"23:00 UTC did not become next-day 08:00 JST: {next_day_run}",
)
check(
    all(
        mapped_jst.get(cron) is not None
        and mapped_jst[cron].date() == anchor_utc_day.date()
        for cron in expected_crons[1:]
    ),
    "SCHED-OTHER-SAME-JST-DAY",
    f"01:00/02:30/08:30/13:00 UTC date relation differs: {mapped_jst}",
)

grouped_by_jst_date: dict[object, list[datetime]] = {}
for day_offset in (-1, 0, 1):
    utc_day = anchor_utc_day + timedelta(days=day_offset)
    for cron in crons:
        utc_run = cron_utc_datetime(cron, utc_day)
        if utc_run is None:
            continue
        jst_run = utc_run.astimezone(jst)
        grouped_by_jst_date.setdefault(jst_run.date(), []).append(jst_run)

middle_complete_jst_date = anchor_utc_day.date()
middle_runs = sorted(grouped_by_jst_date.get(middle_complete_jst_date, []))
middle_times = [(run.hour, run.minute) for run in middle_runs]
check(
    len(middle_runs) == 5,
    "SCHED-JST-DAY-COUNT",
    f"middle complete JST day has {len(middle_runs)} runs: {middle_runs}",
)
check(
    middle_times == [(8, 0), (10, 0), (11, 30), (17, 30), (22, 0)],
    "SCHED-JST-DAY-TIMES",
    f"middle complete JST day times differ: {middle_times}",
)

dispatch = trigger.get("workflow_dispatch")
check(isinstance(dispatch, dict), "DISPATCH-RETAINED", "workflow_dispatch missing")
inputs = dispatch.get("inputs", {}) if isinstance(dispatch, dict) else {}
check(
    inputs.get("crawl_content_limit", {}).get("default") == "200",
    "DISPATCH-CRAWL-DEFAULT",
    "crawl_content_limit default is not 200",
)
check(
    inputs.get("post_limit", {}).get("default") == "",
    "DISPATCH-POST-EMPTY",
    "post_limit default must remain empty",
)
check(
    "1000" in inputs.get("post_limit", {}).get("description", ""),
    "DISPATCH-POST-DESCRIPTION",
    "post_limit description does not state 1000",
)

env = candidate.get("jobs", {}).get("crawl", {}).get("env", {})
check(
    env.get("POST_LIMIT")
    == "${{ inputs.post_limit || vars.POST_LIMIT || '1000' }}",
    "ENV-POST-LITERAL",
    f"unexpected POST_LIMIT expression: {env.get('POST_LIMIT')}",
)
check(
    env.get("CRAWL_CONTENT_LIMIT")
    == "${{ inputs.crawl_content_limit || vars.CRAWL_CONTENT_LIMIT || '200' }}",
    "ENV-CRAWL-LITERAL",
    f"unexpected CRAWL_CONTENT_LIMIT expression: {env.get('CRAWL_CONTENT_LIMIT')}",
)


def normalized_workflow(document: dict) -> dict:
    result = copy.deepcopy(document)
    result["on"]["schedule"] = "<allowed-schedule-change>"
    result["on"]["workflow_dispatch"]["inputs"]["crawl_content_limit"]["default"] = (
        "<allowed-crawl-default-change>"
    )
    result["on"]["workflow_dispatch"]["inputs"]["post_limit"]["description"] = (
        "<allowed-post-description-change>"
    )
    result["jobs"]["crawl"]["env"]["POST_LIMIT"] = "<allowed-post-expression-change>"
    result["jobs"]["crawl"]["env"]["CRAWL_CONTENT_LIMIT"] = (
        "<allowed-crawl-expression-change>"
    )
    return result


check(
    normalized_workflow(candidate) == normalized_workflow(baseline),
    "REG-UNRELATED-WORKFLOW",
    "workflow semantics changed outside the five reviewed fields",
)
for retained_input in (
    "site_info_ids",
    "site_category_ids",
    "skip_cms_post",
    "crawl_process_limit",
    "post_featured_image_only",
    "repair_cms_content_id",
):
    check(retained_input in inputs, "REG-INPUTS", f"missing {retained_input}")
for retained_env in (
    "TARGET_SITE_INFO_IDS",
    "TARGET_SITE_CATEGORY_IDS",
    "SKIP_CMS_POST",
    "CRAWL_PROCESS_LIMIT",
    "POST_FEATURED_IMAGE_ONLY",
    "REPAIR_CMS_CONTENT_ID",
    "AI_MODE",
):
    check(retained_env in env, "REG-ENV", f"missing {retained_env}")

check(
    'if ! [[ "$POST_LIMIT" =~ ^[1-9][0-9]*$ ]]; then' in candidate_text
    and "POST_LIMIT must be a positive integer." in candidate_text,
    "VALIDATION-SOURCE",
    "existing POST_LIMIT fail-fast source changed",
)
positive = re.compile(r"^[1-9][0-9]*$")
for value in ("abc", "0", "-1"):
    check(not positive.fullmatch(value), "VALIDATION-REJECT", f"accepted {value}")
for value in ("1", "200", "1000"):
    check(bool(positive.fullmatch(value)), "VALIDATION-ACCEPT", f"rejected {value}")


def resolve(input_value: str, repository_value: str, literal: str) -> str:
    return input_value or repository_value or literal


# Static event/input matrix. Dispatch omission supplies declared defaults.
check(resolve("", "", "200") == "200", "MATRIX-SCHEDULE-CRAWL", "schedule crawl")
check(resolve("", "", "1000") == "1000", "MATRIX-SCHEDULE-POST", "schedule post")
check(resolve("17", "88", "200") == "17", "MATRIX-EXPLICIT-CRAWL", "explicit crawl")
check(resolve("23", "99", "1000") == "23", "MATRIX-EXPLICIT-POST", "explicit post")
check(resolve("200", "88", "200") == "200", "MATRIX-DEFAULT-MASK", "crawl default")
check(resolve("", "99", "1000") == "99", "MATRIX-POST-VAR", "post variable")
check(resolve("200", "", "200") == "200", "MATRIX-NORMAL-CRAWL", "normal dispatch")
check(resolve("", "", "1000") == "1000", "MATRIX-NORMAL-POST", "normal dispatch")
check(resolve("1", "88", "200") == "1", "MATRIX-BOUNDED-CRAWL", "bounded crawl")
check(resolve("1", "99", "1000") == "1", "MATRIX-BOUNDED-POST", "bounded post")
check(resolve("1", "99", "1000") == "1", "MATRIX-FEATURED-POST", "featured-only post")
check(resolve("1", "88", "200") == "1", "MATRIX-REPAIR-CRAWL", "repair crawl")
check(resolve("1", "99", "1000") == "1", "MATRIX-REPAIR-POST", "repair post")

crawler_components = (
    repo / "web-crawler/src/main/java/com/happinesea/webcrawler/config/CrawlerComponents.java"
).read_text(encoding="utf-8")
site_service = (
    repo / "web-crawler/src/main/java/com/happinesea/webcrawler/service/SiteContentsService.java"
).read_text(encoding="utf-8")
check(
    crawler_components.index("if (siteContentsService.isCmsRepairConfigured())")
    < crawler_components.index("SiteCategory category = claimedProcess.getSiteCategory()"),
    "REPAIR-CRAWL-BRANCH",
    "repair no longer branches before normal category loading",
)
check(
    site_service.index("if (isCmsRepairConfigured())")
    < site_service.index("findContents4Post"),
    "REPAIR-POST-BRANCH",
    "repair no longer branches before normal post selection",
)
check(
    site_service.index("if (postFeaturedImageOnly)")
    < site_service.index("int limit = Math.min(contentsList.size(), postContentsLimitCount)"),
    "FEATURED-LIMIT-ORDER",
    "featured-image filtering no longer precedes POST_LIMIT",
)

default_needles = {
    "web-crawler/src/main/resources/application.yml": (
        "post-contents-limit-count: ${POST_LIMIT:10}",
        "crawl-process-limit-count: ${CRAWL_PROCESS_LIMIT:${POST_LIMIT:10}}",
        "crawl-content-limit-count: ${CRAWL_CONTENT_LIMIT:${POST_LIMIT:10}}",
    ),
    "web-crawler/src/main/resources/application-prod.yml": (
        "post-contents-limit-count: ${POST_LIMIT:10}",
        "crawl-process-limit-count: ${CRAWL_PROCESS_LIMIT:${POST_LIMIT:10}}",
        "crawl-content-limit-count: ${CRAWL_CONTENT_LIMIT:${POST_LIMIT:10}}",
    ),
    "web-crawler/src/main/java/com/happinesea/webcrawler/config/CrawlerComponents.java": (
        '@Value("${web-crawler.crawl-content-limit-count:10}")',
    ),
    "web-crawler/src/main/java/com/happinesea/webcrawler/config/BatchConfig.java": (
        '@Value("${web-crawler.crawl-process-limit-count:10}")',
    ),
    "web-crawler/src/main/java/com/happinesea/webcrawler/service/SiteContentsService.java": (
        '@Value("${web-crawler.post-contents-limit-count}")',
    ),
}
for relative_path, needles in default_needles.items():
    source = (repo / relative_path).read_text(encoding="utf-8")
    for needle in needles:
        check(needle in source, "JVM-DEFAULT-INVENTORY", f"{relative_path}: {needle}")

if failures:
    for failure in failures:
        print(f"FAIL {failure}")
    sys.exit(1)
print("PASS complete crawler Actions static contract")
~~~

- [ ] **Step 4: Run the immutable fixture against origin/main and verify RED**

Run:

~~~powershell
$fixture = Join-Path $env:TEMP 'validate-crawler-actions-workflow.py'
$baseline = Join-Path $env:TEMP 'hourly-crawl-origin-main.yml'
$python = (Get-Content -Raw (Join-Path $env:TEMP 'crawler-actions-python.txt')).Trim()
$evidenceDir = Join-Path $env:TEMP 'crawler-actions-frequency-limits-evidence'
[void](New-Item -ItemType Directory -Force $evidenceDir)
$fixtureShaFile = Join-Path $evidenceDir 'fixture.sha256'
$fixtureShaLog = Join-Path $evidenceDir 'fixture-sha-phases.txt'
$redLog = Join-Path $evidenceDir 'origin-main-red.txt'
$redExitFile = Join-Path $evidenceDir 'origin-main-red.exit'
$fixtureSha = (Get-FileHash $fixture -Algorithm SHA256).Hash.ToUpperInvariant()
$fixtureSha | Set-Content -Encoding ascii $fixtureShaFile
"BEFORE_RED=$fixtureSha" | Set-Content -Encoding ascii $fixtureShaLog

$red = & $python $fixture --candidate $baseline --baseline $baseline --repo-root (Get-Location) 2>&1
$redExit = $LASTEXITCODE
$red | Tee-Object -FilePath $redLog
$redExit | Set-Content -Encoding ascii $redExitFile
if ($redExit -eq 0) { throw 'Expected origin/main RED, got PASS' }
$requiredRed = @(
  'SCHED-COUNT',
  'SCHED-EXACT',
  'SCHED-HOURLY',
  'SCHED-COMMENTS',
  'SCHED-UTC-JST-ARITHMETIC',
  'SCHED-23-NEXT-JST-DAY',
  'SCHED-OTHER-SAME-JST-DAY',
  'SCHED-JST-DAY-COUNT',
  'SCHED-JST-DAY-TIMES',
  'DISPATCH-CRAWL-DEFAULT',
  'DISPATCH-POST-DESCRIPTION',
  'ENV-POST-LITERAL',
  'ENV-CRAWL-LITERAL'
)
foreach ($id in $requiredRed) {
  if (-not ($red -match $id)) { throw "Missing expected RED failure: $id" }
}
$afterRedSha = (Get-FileHash $fixture -Algorithm SHA256).Hash.ToUpperInvariant()
"AFTER_RED=$afterRedSha" | Add-Content -Encoding ascii $fixtureShaLog
if ($afterRedSha -ne $fixtureSha) {
  throw "Fixture changed during RED: expected=$fixtureSha actual=$afterRedSha"
}
"RED_EXIT=$redExit" | Add-Content -Encoding ascii $fixtureShaLog
~~~

Expected: nonzero exit with every listed requirement ID, matching BEFORE_RED/AFTER_RED SHA-256 values, and saved RED stdout/exit evidence. A YAML/import/syntax failure is not acceptable RED.

Do not edit the fixture after this point. The saved fixture.sha256 is the objective identity. The same fixture file, baseline argument, and repo-root argument are used for GREEN; only candidate changes.

### Task 2: Apply the minimal workflow change and verify GREEN

**Files:**

- Modify: .github/workflows/hourly-crawl.yml

**Interfaces:**

- Consumes the immutable fixture and exact design matrix.
- Produces exactly five cron entries and workflow-level 200/1000 resolution.

- [ ] **Step 1: Replace only the hourly schedule**

Use exactly:

~~~yaml
  schedule:
    - cron: "0 23 * * *" # 08:00 JST (next calendar day)
    - cron: "0 1 * * *" # 10:00 JST
    - cron: "30 2 * * *" # 11:30 JST
    - cron: "30 8 * * *" # 17:30 JST
    - cron: "0 13 * * *" # 22:00 JST
~~~

- [ ] **Step 2: Change only the requested input and env fields**

Set crawl_content_limit dispatch default to "200". Keep post_limit default empty, update its description to state repository variable or 1000, set POST_LIMIT literal fallback to '1000', and set CRAWL_CONTENT_LIMIT literal fallback to '200'. Do not reorder or alter any unrelated trigger, input, environment variable, validation block, or job step.

- [ ] **Step 3: Run the unchanged fixture against the worktree**

Run:

~~~powershell
$evidenceDir = Join-Path $env:TEMP 'crawler-actions-frequency-limits-evidence'
$fixture = Join-Path $env:TEMP 'validate-crawler-actions-workflow.py'
$baseline = Join-Path $env:TEMP 'hourly-crawl-origin-main.yml'
$python = (Get-Content -Raw (Join-Path $env:TEMP 'crawler-actions-python.txt')).Trim()
$fixtureShaFile = Join-Path $evidenceDir 'fixture.sha256'
$fixtureShaLog = Join-Path $evidenceDir 'fixture-sha-phases.txt'
$greenLog = Join-Path $evidenceDir 'worktree-green.txt'
$redLog = Join-Path $evidenceDir 'origin-main-red.txt'
$redExit = [int](Get-Content -Raw (Join-Path $evidenceDir 'origin-main-red.exit'))
$fixtureSha = (Get-Content -Raw $fixtureShaFile).Trim().ToUpperInvariant()
$beforeGreenSha = (Get-FileHash $fixture -Algorithm SHA256).Hash.ToUpperInvariant()
"BEFORE_GREEN=$beforeGreenSha" | Add-Content -Encoding ascii $fixtureShaLog
if ($beforeGreenSha -ne $fixtureSha) {
  throw "Fixture changed before GREEN: expected=$fixtureSha actual=$beforeGreenSha"
}

$green = & $python $fixture --candidate .github/workflows/hourly-crawl.yml --baseline $baseline --repo-root (Get-Location) 2>&1
$greenExit = $LASTEXITCODE
$green | Tee-Object -FilePath $greenLog
if ($greenExit -ne 0) { throw "Worktree static contract failed: exit=$greenExit" }

$afterGreenSha = (Get-FileHash $fixture -Algorithm SHA256).Hash.ToUpperInvariant()
"AFTER_GREEN=$afterGreenSha" | Add-Content -Encoding ascii $fixtureShaLog
"GREEN_EXIT=$greenExit" | Add-Content -Encoding ascii $fixtureShaLog
if ($afterGreenSha -ne $fixtureSha) {
  throw "Fixture changed during GREEN: expected=$fixtureSha actual=$afterGreenSha"
}
[ordered]@{
  fixture_sha256 = $fixtureSha
  red_exit = $redExit
  green_exit = $greenExit
  red_log = $redLog
  green_log = $greenLog
} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $evidenceDir 'red-green-summary.json')
~~~

Expected: exit 0 and PASS, identical BEFORE_RED/AFTER_RED/BEFORE_GREEN/AFTER_GREEN SHA-256 values, and red-green-summary.json plus separate logs. Any SHA mismatch is an objective failure. This is static evidence only; do not dispatch a workflow.

### Task 3: Update canonical documentation without changing runtime code

**Files:**

- Modify: docs/testing/web-crawler-test-spec.md
- Modify: docs/operations/web-crawler-production-release-2026-08-05.md
- Modify: tasks/README.md

**Interfaces:**

- Consumes reviewed implementation and local evidence.
- Produces consistent canonical test and operation records.

- [ ] **Step 1: Correct only PROD-011's workflow fallback**

Change only the final literal in PROD-011:

~~~text
empty uses repository variable; when unset, 10
-> empty uses repository variable; when unset, 1000
~~~

Keep Java/Spring defaults at 10 and leave every other test-contract row unchanged.

- [ ] **Step 2: Finalize the operation record**

Replace the planned-only wording with actual local implementation evidence while preserving:

- 24 to 5 starts/day, 19 fewer, theoretical 79.2%;
- 744 to 155 starts per 31 days, 589 fewer;
- hosted execution NOT RUN;
- omitted dispatch crawl default 200 masks a crawl repository variable;
- omitted post default remains empty and allows a post repository variable;
- business logic unchanged but defaulted throughput intentionally higher;
- bounded manual, featured-only, and repair runs require explicit 1/1.

- [ ] **Step 3: Update the dedicated task only**

Record current state, before/after schedule, five JST times, 200/1000, local-only results, date 2026-08-20, branch, pending commit/PR state, and next action. Do not rewrite unrelated tasks.

### Task 4: Enforce the exact tracked/untracked allowlist

**Files:**

- Verify all seven allowed paths.
- Create outside Git: $env:TEMP/check-crawler-actions-scope.ps1

**Interfaces:**

- Consumes current index/worktree status.
- Produces a machine-failing exact-path and whitespace gate.

- [ ] **Step 1: Create and run the scope checker before staging**

Write this complete checker:

~~~powershell
$ErrorActionPreference = 'Stop'
$allowed = @(
  '.github/workflows/hourly-crawl.yml',
  'docs/design/crawler-actions-frequency-and-limits.md',
  'docs/operations/web-crawler-production-release-2026-08-05.md',
  'docs/plans/2026-08-20-crawler-actions-frequency-and-limits.md',
  'docs/testing/crawler-actions-frequency-and-limits-test-spec.md',
  'docs/testing/web-crawler-test-spec.md',
  'tasks/README.md'
) | Sort-Object -Unique
$tracked = @(git diff --name-only origin/main --)
$untracked = @(git ls-files --others --exclude-standard)
$actual = @($tracked + $untracked) |
  Where-Object { $_ } |
  ForEach-Object { $_ -replace '\\', '/' } |
  Sort-Object -Unique
$delta = @(Compare-Object $allowed $actual)
if ($delta) {
  $delta | Format-Table | Out-String | Write-Error
  throw 'Changed path union does not exactly match allowlist'
}
foreach ($path in $untracked) {
  $content = [IO.File]::ReadAllText((Resolve-Path $path))
  if ($content -match '(?m)[ \t]+$') { throw "Trailing whitespace: $path" }
  if (-not $content.EndsWith([char]10)) { throw "Missing final newline: $path" }
}
$forbidden = @(git diff --name-only origin/main -- web-crawler/src/main web-crawler/src/test)
if ($forbidden) { throw "Java/test source diff: $($forbidden -join ', ')" }
Write-Output ('PASS allowlist: ' + ($actual -join ', '))
~~~

Run:

~~~powershell
& $env:TEMP/check-crawler-actions-scope.ps1
git diff --check
~~~

Expected: exact seven-path PASS and no whitespace errors. This checker uses the union of tracked diff and untracked files; it does not rely on origin/main...HEAD.

- [ ] **Step 2: Prove PROD-011 is the only broad-test-spec edit**

Normalize line endings, replace only the origin/main PROD-011 fallback text 10 with 1000 in memory, and compare that expected string to the worktree file. Fail if any other byte-normalized content differs.

~~~powershell
$old = ((git show origin/main:docs/testing/web-crawler-test-spec.md) -join [char]10) + [char]10
$new = [IO.File]::ReadAllText((Resolve-Path 'docs/testing/web-crawler-test-spec.md'))
$old = $old -replace [char]13, ''
$new = $new -replace [char]13, ''
$tick = [char]96
$expected = $old.Replace("未設定なら$($tick)10$($tick)", "未設定なら$($tick)1000$($tick)")
if ($new -ne $expected) { throw 'PROD-011 contains more than the approved fallback edit' }
~~~

### Task 5: Run complete local verification with JDK 17

**Files:**

- Verify workflow, all documentation, and unchanged Java/Spring sources.
- Do not modify production code or configuration.

**Interfaces:**

- Consumes reviewed worktree.
- Produces Agent 5 and Agent 6 evidence.

- [ ] **Step 1: Select JDK 17**

~~~powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
java -version
~~~

Expected: major version 17.

- [ ] **Step 2: Rerun the immutable fixture**

Run this full verification. It reloads the persisted interpreter and fixture hash, checks identity before and after both executions, and writes separate evidence:

~~~powershell
$evidenceDir = Join-Path $env:TEMP 'crawler-actions-frequency-limits-evidence'
$fixture = Join-Path $env:TEMP 'validate-crawler-actions-workflow.py'
$baseline = Join-Path $env:TEMP 'hourly-crawl-origin-main.yml'
$python = (Get-Content -Raw (Join-Path $env:TEMP 'crawler-actions-python.txt')).Trim()
$fixtureSha = (Get-Content -Raw (Join-Path $evidenceDir 'fixture.sha256')).Trim().ToUpperInvariant()
$fixtureShaLog = Join-Path $evidenceDir 'fixture-sha-phases.txt'

function Assert-FixtureHash {
  param([string]$Phase)
  $actual = (Get-FileHash $fixture -Algorithm SHA256).Hash.ToUpperInvariant()
  "RERUN_$($Phase.ToUpperInvariant())=$actual" | Add-Content -Encoding ascii $fixtureShaLog
  if ($actual -ne $fixtureSha) {
    throw "Fixture SHA mismatch at ${Phase}: expected=$fixtureSha actual=$actual"
  }
}

$requiredRed = @(
  'SCHED-COUNT',
  'SCHED-EXACT',
  'SCHED-HOURLY',
  'SCHED-COMMENTS',
  'SCHED-UTC-JST-ARITHMETIC',
  'SCHED-23-NEXT-JST-DAY',
  'SCHED-OTHER-SAME-JST-DAY',
  'SCHED-JST-DAY-COUNT',
  'SCHED-JST-DAY-TIMES',
  'DISPATCH-CRAWL-DEFAULT',
  'DISPATCH-POST-DESCRIPTION',
  'ENV-POST-LITERAL',
  'ENV-CRAWL-LITERAL'
)

Assert-FixtureHash 'before-red'
$redRerun = & $python $fixture --candidate $baseline --baseline $baseline --repo-root (Get-Location) 2>&1
$redRerunExit = $LASTEXITCODE
$redRerun | Tee-Object -FilePath (Join-Path $evidenceDir 'verification-origin-main-red.txt')
if ($redRerunExit -eq 0) { throw 'Verification rerun expected RED, got PASS' }
foreach ($id in $requiredRed) {
  if (-not ($redRerun -match $id)) { throw "Verification RED missing: $id" }
}
Assert-FixtureHash 'after-red'

Assert-FixtureHash 'before-green'
$greenRerun = & $python $fixture --candidate .github/workflows/hourly-crawl.yml --baseline $baseline --repo-root (Get-Location) 2>&1
$greenRerunExit = $LASTEXITCODE
$greenRerun | Tee-Object -FilePath (Join-Path $evidenceDir 'verification-worktree-green.txt')
if ($greenRerunExit -ne 0 -or -not ($greenRerun -match 'PASS complete crawler Actions static contract')) {
  throw "Verification GREEN failed: exit=$greenRerunExit"
}
Assert-FixtureHash 'after-green'

[ordered]@{
  fixture_sha256 = $fixtureSha
  red_exit = $redRerunExit
  green_exit = $greenRerunExit
  utc_jst_assertions = @(
    'SCHED-UTC-JST-ARITHMETIC',
    'SCHED-23-NEXT-JST-DAY',
    'SCHED-OTHER-SAME-JST-DAY',
    'SCHED-JST-DAY-COUNT',
    'SCHED-JST-DAY-TIMES'
  )
} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $evidenceDir 'verification-rerun-summary.json')
~~~

Expected: enumerated RED against origin/main, GREEN against the worktree, and the same persisted SHA-256 at all four rerun checkpoints. Any fixture hash difference is a test failure.

- [ ] **Step 3: Enumerate every retained Java/Spring default**

Confirm the following exact inventory:

| File | Required retained value |
|---|---|
| application.yml | post 10, process nested POST_LIMIT 10, content nested POST_LIMIT 10 |
| application-prod.yml | post 10, process nested POST_LIMIT 10, content nested POST_LIMIT 10 |
| CrawlerComponents | crawl content injection fallback 10 |
| BatchConfig | crawl process injection fallback 10 |
| SiteContentsService | post property injection, whose profile properties default to 10 |

Then run:

~~~powershell
git diff --exit-code origin/main -- web-crawler/src/main/java web-crawler/src/main/resources
~~~

Expected: no Java or resource diff.

- [ ] **Step 4: Run Gradle test and bootJar**

~~~powershell
Set-Location web-crawler
.\gradlew.bat clean test bootJar --stacktrace
~~~

Expected: BUILD SUCCESSFUL under JDK 17. Do not connect to production MariaDB.

- [ ] **Step 5: Rerun strict scope and whitespace checks**

Return to the repository root and run:

~~~powershell
& $env:TEMP/check-crawler-actions-scope.ps1
git diff --check
git status --short
~~~

Expected: exact seven-path allowlist, no whitespace errors, and no Matt Pocock paths.

### Task 6: Review, stage explicitly, and publish only after all gates pass

**Files:**

- Review the complete seven-path diff.
- Do not introduce another implementation file.

**Interfaces:**

- Consumes Agent 3 design/test PASS, Agent 5 implementation PASS, and Agent 6 sandbox PASS.
- Produces one reviewed branch and one Draft PR.

- [ ] **Step 1: Require all six independent agent gates**

Return any BLOCKER or MAJOR finding to its owning agent, apply the correction, and repeat the same reviewer. Do not proceed on a partial PASS.

- [ ] **Step 2: Stage only the explicit allowlist**

Use git add with the seven exact paths. Never use git add -A.

- [ ] **Step 3: Enforce staged paths and staged whitespace**

~~~powershell
$allowed = @(
  '.github/workflows/hourly-crawl.yml',
  'docs/design/crawler-actions-frequency-and-limits.md',
  'docs/operations/web-crawler-production-release-2026-08-05.md',
  'docs/plans/2026-08-20-crawler-actions-frequency-and-limits.md',
  'docs/testing/crawler-actions-frequency-and-limits-test-spec.md',
  'docs/testing/web-crawler-test-spec.md',
  'tasks/README.md'
) | Sort-Object
$staged = @(git diff --cached --name-only) |
  ForEach-Object { $_ -replace '\\', '/' } |
  Sort-Object
if (Compare-Object $allowed $staged) { throw 'Staged allowlist mismatch' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged whitespace check failed' }
git diff --cached --stat
~~~

Expected: exactly seven staged paths and no staged whitespace errors.

- [ ] **Step 4: Commit, push, and create one Draft PR**

The controller commits only the staged allowlist, pushes only codex/crawler-actions-frequency-limits, and creates one Draft PR. Do not merge and do not invoke any workflow.

- [ ] **Step 5: Verify zero task-triggered Actions runs**

Before push, inspect every workflow trigger/path filter. If any changed path would trigger Actions for the feature-branch push or PR, stop before push. After publication, use read-only GitHub CLI/API inspection to confirm no run ID was created for this branch. Do not rerun or dispatch anything.

## Self-review

- Requirement coverage: event matrix, five crons, real UTC+09 arithmetic, next-day mapping, three-day JST grouping, throughput impact, bounded operations, repair source evidence, PROD-011 consistency, complete defaults inventory, immutable RED/GREEN fixture, exact allowlist, staged checks, local build, and zero-Actions restrictions all have explicit gates.
- Fixture integrity: all assertions exist before the baseline run; the same fixture and baseline are reused with only the candidate source changed; SHA-256 is persisted before RED and must match after RED, before/after GREEN, and every verification rerun.
- Scope integrity: the final gate unions tracked and untracked files, separately checks untracked whitespace, then checks exact staged paths and staged whitespace.
