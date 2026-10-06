# Reseeds the three demo accounts on the local stack, restarts the services that cache or
# summarise that data, waits for health and prints what to eyeball.
#
#   powershell -NoProfile -File scripts\demo-reseed.ps1
#   powershell -NoProfile -File scripts\demo-reseed.ps1 -DryRun
#
# The addresses come from scripts\demo-accounts.env (git-ignored), or the example file when that
# does not exist. Parameters override both. The seed deletes and rebuilds the data of those
# accounts only. Run it after 08:00 Singapore time, so today's breakfast is not in the future.
param(
    [string]$SteadyEmail,
    [string]$DriftingEmail,
    [string]$FreshEmail,
    [string]$BackendDir = (Split-Path -Parent $PSScriptRoot),
    [string]$EnvFile = '.env',
    [string]$AppProject = 'fitsync-app',
    [string]$PostgresContainer = 'fitsync-postgres',
    [int]$TimeoutSeconds = 180,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'

function Read-DemoAccounts([string]$Path) {
    $map = @{}
    foreach ($line in (Get-Content -Path $Path)) {
        if ($line -match '^\s*([A-Z_]+)\s*=\s*(.+?)\s*$') { $map[$Matches[1]] = $Matches[2] }
    }
    return $map
}

$local = Join-Path $PSScriptRoot 'demo-accounts.env'
$example = Join-Path $PSScriptRoot 'demo-accounts.example.env'
$file = if (Test-Path $local) { $local } else { $example }
$accounts = Read-DemoAccounts $file
if (-not $SteadyEmail)   { $SteadyEmail   = $accounts['DEMO_STEADY_EMAIL'] }
if (-not $DriftingEmail) { $DriftingEmail = $accounts['DEMO_DRIFTING_EMAIL'] }
if (-not $FreshEmail)    { $FreshEmail    = $accounts['DEMO_FRESH_EMAIL'] }
foreach ($e in @($SteadyEmail, $DriftingEmail, $FreshEmail)) {
    if (-not $e -or $e -notmatch '^[^\s@''"]+@[^\s@''"]+$') { throw "Missing or invalid demo email '$e' (source: $file)" }
}
if ($file -eq $example -and -not $DryRun) {
    Write-Warning 'Using the example addresses. Copy demo-accounts.example.env to demo-accounts.env and fill in the real ones.'
}

function Invoke-Step([string]$Label, [string[]]$Docker) {
    Write-Host ("> docker " + ($Docker -join ' '))
    if ($DryRun) { return }
    & docker @Docker
    if ($LASTEXITCODE -ne 0) { throw "$Label failed (exit $LASTEXITCODE)" }
}

Set-Location $BackendDir
$seed = Join-Path $PSScriptRoot 'seed-demo-fitsync.sql'
if (-not (Test-Path $seed)) { throw "Seed not found: $seed" }
$remote = '/tmp/seed-demo-fitsync.sql'

# The file is copied into the container instead of piped, so PowerShell 5.1 never re-encodes it.
Invoke-Step 'copy seed' @('cp', $seed, "${PostgresContainer}:$remote")

$runs = @(
    @{ Persona = 'steady';   Email = $SteadyEmail },
    @{ Persona = 'drifting'; Email = $DriftingEmail },
    @{ Persona = 'fresh';    Email = $FreshEmail }
)
foreach ($r in $runs) {
    Write-Host ("Seeding {0} ({1})" -f $r.Persona, $r.Email)
    Invoke-Step "seed $($r.Persona)" @('exec', '-i', $PostgresContainer, 'psql', '-U', 'fitsync', '-d', 'fitsync',
        '-v', 'ON_ERROR_STOP=1', '-v', "email=$($r.Email)", '-v', "persona=$($r.Persona)", '-q', '-f', $remote)
}
Invoke-Step 'remove seed copy' @('exec', $PostgresContainer, 'rm', '-f', $remote)

Write-Host 'Restarting analytics-service and recommendation-service'
Invoke-Step 'restart' @('compose', '-f', 'docker-compose.prod.yml', '-p', $AppProject, '--env-file', $EnvFile,
    '--profile', 'events', 'restart', 'analytics-service', 'recommendation-service')

$wait = @('fitsync-analytics-service', 'fitsync-recommendation-service')
if ($DryRun) {
    Write-Host ('Would wait for: ' + ($wait -join ', '))
} else {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $pending = @()
        foreach ($name in $wait) {
            $s = (& docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $name 2>$null)
            if ("$s".Trim() -ne 'healthy') { $pending += $name }
        }
        if ($pending.Count -eq 0) { break }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    if ($pending.Count -gt 0) { throw ("Not healthy after $TimeoutSeconds s: " + ($pending -join ', ')) }
}

Write-Host ''
Write-Host 'Reseed done. Eyeball, signed in as steady unless noted:'
@(
    'Home: the guide card has something to say for the morning (breakfast logged, one sip of water)',
    'Workout Today: the plan week shows Monday done, today as planned',
    'Nutrition Diary: last Sunday reads as a fasted day, yesterday is complete, today has breakfast only',
    'Recipes: Cook for tonight lists recipes, three cooks are in the history',
    'Progress: the week rule line, streak, energy and the weight trend',
    'Me: Member since about 60 days ago, footprint grid filled, badges earned',
    'Achievements: the paths show progress, the kitchen path counts three cooks',
    'drifting: Home and Progress read as slipping; fresh: every page shows its empty state',
    'Admin console: sign in as the admin account'
) | ForEach-Object { Write-Host ("  [ ] " + $_) }
