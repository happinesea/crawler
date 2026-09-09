$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$workflowPath = Join-Path $root '.github\workflows\hourly-crawl.yml'
$workflow = Get-Content -Raw -LiteralPath $workflowPath

function Assert-Contains([string] $needle) {
    if (-not $workflow.Contains($needle)) {
        throw "hourly-crawl.yml is missing: $needle"
    }
}

Assert-Contains 'PROCESS_OWNER_INSTANCE: gha-${{ github.run_id }}-${{ github.run_attempt }}'
Assert-Contains 'name: Release runner-owned stale process pools'
Assert-Contains 'timeout --signal=TERM --kill-after=30s 28m java -jar'
Assert-Contains 'owner_instance = ''${PROCESS_OWNER_INSTANCE}'''
Assert-Contains 'process_status = ''2'''
Assert-Contains 'last_result_status = ''9'''
Assert-Contains 'process_id = NULL'
Assert-Contains 'owner_instance = NULL'
Assert-Contains 'claimed_at = NULL'
Assert-Contains 'heartbeat_at = NULL'
Assert-Contains 'steps.crawl.outputs.exit_code != ''0'''

$update = [regex]::Match($workflow, '(?s)UPDATE site_info_process_pool.+?WHERE process_status = ''2''\s+AND owner_instance = ''\$\{PROCESS_OWNER_INSTANCE\}'';')
if (-not $update.Success) {
    throw 'cleanup SQL must release only PROCESSING rows owned by PROCESS_OWNER_INSTANCE'
}

$rows = @(
    [pscustomobject]@{ process_status = '2'; owner_instance = 'gha-33848476897-1'; released = $false },
    [pscustomobject]@{ process_status = '2'; owner_instance = 'gha-other-run-1'; released = $false }
)

foreach ($row in $rows) {
    if ($row.process_status -eq '2' -and $row.owner_instance -eq 'gha-33848476897-1') {
        $row.process_status = '1'
        $row.owner_instance = $null
        $row.released = $true
    }
}

if (($rows | Where-Object { $_.released }).Count -ne 1) {
    throw 'cleanup fixture expected exactly one released row'
}
if ($rows[1].process_status -ne '2' -or $rows[1].owner_instance -ne 'gha-other-run-1') {
    throw 'cleanup fixture must not release another owner row'
}

if ($workflow -match 'DB_PASSWORD=.*echo|echo.*DB_PASSWORD|WP_APPLICATION_PASSWORD=.*echo|echo.*WP_APPLICATION_PASSWORD') {
    throw 'workflow cleanup must not echo configured secrets'
}

Write-Output 'hourly-crawl cleanup fixture PASS'
