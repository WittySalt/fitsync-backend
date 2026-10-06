# Stops the app stack (services, redis, kafka) and keeps every volume, so the data survives.
# Postgres and minio from docker-compose.yml keep running unless -Infra is given, which stops
# them too (still without removing volumes).
#
#   powershell -NoProfile -File scripts\demo-down.ps1
#   powershell -NoProfile -File scripts\demo-down.ps1 -Infra
#   powershell -NoProfile -File scripts\demo-down.ps1 -DryRun
param(
    [string]$BackendDir = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = '.env',
    [string]$InfraProject = 'backend',
    [string]$AppProject = 'fitsync-app',
    [switch]$Infra,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'

function Invoke-Step([string]$Label, [string[]]$Docker) {
    Write-Host ("> docker " + ($Docker -join ' '))
    if ($DryRun) { return }
    & docker @Docker
    if ($LASTEXITCODE -ne 0) { throw "$Label failed (exit $LASTEXITCODE)" }
}

Set-Location $BackendDir
# "down" without -v removes the containers and network but never the named volumes.
Invoke-Step 'app down' @('compose', '-f', 'docker-compose.prod.yml', '-p', $AppProject, '--env-file', $EnvFile, '--profile', 'events', 'down')
if ($Infra) {
    Invoke-Step 'infra stop' @('compose', '-f', 'docker-compose.yml', '-p', $InfraProject, 'stop', 'postgres', 'minio')
}
Write-Host 'Stopped. Volumes kept.'
