# Brings the whole local stack up for recording: infra first (docker-compose.yml: postgres, minio),
# then the app stack (docker-compose.prod.yml, events profile: redis, kafka and the eight services),
# then waits until all 12 containers report healthy and lists them.
#
#   powershell -NoProfile -File scripts\demo-up.ps1
#   powershell -NoProfile -File scripts\demo-up.ps1 -DryRun     # prints the commands, runs nothing
#
# Compose files and .env are only read. Images are not built here; build them first with
# scripts\build-images.ps1 if a service changed since the last build.
param(
    [string]$BackendDir = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = '.env',
    [string]$InfraProject = 'backend',
    [string]$AppProject = 'fitsync-app',
    [int]$TimeoutSeconds = 300,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'

$expected = @(
    'fitsync-postgres', 'fitsync-minio', 'fitsync-redis', 'fitsync-kafka',
    'fitsync-auth-service', 'fitsync-user-service', 'fitsync-workout-service',
    'fitsync-nutrition-service', 'fitsync-analytics-service',
    'fitsync-recommendation-service', 'fitsync-notification-service', 'fitsync-api-gateway'
)

function Invoke-Step([string]$Label, [string[]]$Docker) {
    Write-Host ("> docker " + ($Docker -join ' '))
    if ($DryRun) { return }
    & docker @Docker
    if ($LASTEXITCODE -ne 0) { throw "$Label failed (exit $LASTEXITCODE)" }
}

Set-Location $BackendDir
if (-not $DryRun -and -not (Test-Path $EnvFile)) { throw "Env file '$EnvFile' not found in $BackendDir" }

Write-Host '1/3 Infra: postgres, minio'
Invoke-Step 'infra up' @('compose', '-f', 'docker-compose.yml', '-p', $InfraProject, 'up', '-d', 'postgres', 'minio', 'minio-init')

Write-Host '2/3 App stack: redis, kafka, eight services (events profile)'
Invoke-Step 'app up' @('compose', '-f', 'docker-compose.prod.yml', '-p', $AppProject, '--env-file', $EnvFile, '--profile', 'events', 'up', '-d')

Write-Host "3/3 Waiting for $($expected.Count) containers to be healthy (up to $TimeoutSeconds s)"
if ($DryRun) {
    Write-Host ('Would wait for: ' + ($expected -join ', '))
    return
}
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    $states = @{}
    foreach ($name in $expected) {
        $s = (& docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $name 2>$null)
        if ($LASTEXITCODE -ne 0 -or -not $s) { $s = 'missing' }
        $states[$name] = "$s".Trim()
    }
    $pending = @($expected | Where-Object { $states[$_] -ne 'healthy' })
    if ($pending.Count -eq 0) { break }
    Start-Sleep -Seconds 5
} while ((Get-Date) -lt $deadline)

foreach ($name in $expected) { Write-Host ('{0,-34} {1}' -f $name, $states[$name]) }
if ($pending.Count -gt 0) { throw ("Not healthy after $TimeoutSeconds s: " + ($pending -join ', ')) }
Write-Host 'All containers healthy. Next: scripts\demo-reseed.ps1'
