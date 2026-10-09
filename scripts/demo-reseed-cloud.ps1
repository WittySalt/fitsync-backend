# Reseeds the demo accounts on the staging or prod AWS environment, the cloud twin of
# demo-reseed.ps1. Without -Apply it is a dry run: it checks the accounts and the system catalogues
# read-only, prints the rows each account has now and changes nothing.
#
#   powershell -NoProfile -File scripts\demo-reseed-cloud.ps1 -Env staging
#   powershell -NoProfile -File scripts\demo-reseed-cloud.ps1 -Env prod -Apply
#   powershell -NoProfile -File scripts\demo-reseed-cloud.ps1 -Env prod -Persona steady,drifting -Now 2026-10-09T09:30 -Apply
#
# Hosts, SSH keys and secret ids come from scripts\demo-cloud.env, the addresses from
# scripts\demo-accounts.env (both git-ignored). The remote work is done by demo-cloud-remote.sh,
# which this script uploads with scp into a private temp dir on the host; that dir is removed again
# whatever happens. The database password stays on the host and is never printed.
param(
    [Parameter(Mandatory = $true)][ValidateSet('staging', 'prod')][string]$Env,
    [string]$Now,
    [string[]]$Persona = @('steady', 'drifting', 'fresh'),
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'

function Read-KeyValues([string]$Path) {
    $map = @{}
    foreach ($line in (Get-Content -Path $Path)) {
        if ($line -match '^\s*([A-Z_]+)\s*=\s*(.+?)\s*$') { $map[$Matches[1]] = $Matches[2] }
    }
    return $map
}

function Require([hashtable]$Map, [string]$Key, [string]$Source) {
    $v = $Map[$Key]
    if (-not $v) { throw "Missing $Key in $Source" }
    return $v
}

# Settings for the chosen environment.
$cloudFile = Join-Path $PSScriptRoot 'demo-cloud.env'
if (-not (Test-Path $cloudFile)) { throw "Not found: $cloudFile. Copy demo-cloud.example.env to demo-cloud.env and fill it in." }
$cloud = Read-KeyValues $cloudFile
$prefix = $Env.ToUpperInvariant()
$sshHost = Require $cloud "${prefix}_HOST" $cloudFile
$keyPath = Require $cloud "${prefix}_SSH_KEY" $cloudFile
$secretId = Require $cloud "${prefix}_APP_SECRET_ID" $cloudFile
$baseUrl = $cloud["${prefix}_BASE_URL"]
if ($keyPath.StartsWith('~')) { $keyPath = Join-Path $HOME $keyPath.Substring(1).TrimStart('/', '\') }
if (-not (Test-Path $keyPath)) { throw "SSH key not found: $keyPath" }
if ($sshHost -notmatch '^[A-Za-z0-9.-]+$') { throw "Invalid ${prefix}_HOST '$sshHost'" }
if ($secretId -notmatch '^[A-Za-z0-9/_+=.@-]+$') { throw "Invalid ${prefix}_APP_SECRET_ID '$secretId'" }

# The accounts. The example addresses are never used here: the cloud run needs the real ones.
$accountsFile = Join-Path $PSScriptRoot 'demo-accounts.env'
if (-not (Test-Path $accountsFile)) { throw "Not found: $accountsFile. Copy demo-accounts.example.env to demo-accounts.env and fill it in." }
$accounts = Read-KeyValues $accountsFile
# powershell -File passes "steady,drifting" as one string, so split on commas here.
$personas = @($Persona | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim().ToLowerInvariant() } |
    Where-Object { $_ } | Select-Object -Unique)
foreach ($p in $personas) {
    if (@('steady', 'drifting', 'fresh') -notcontains $p) { throw "Unknown persona '$p'. Use steady, drifting or fresh." }
}
if ($personas.Count -eq 0) { throw 'No persona given.' }
$targets = @()
foreach ($p in $personas) {
    $email = Require $accounts ("DEMO_{0}_EMAIL" -f $p.ToUpperInvariant()) $accountsFile
    if ($email -notmatch '^[^\s@''"]+@[^\s@''"]+$') { throw "Invalid demo email for ${p}: '$email'" }
    $targets += ("{0} {1}" -f $p, $email)
}

# One run time for every account, Singapore wall clock, as the local script does.
if ($Now) {
    $parsed = [DateTime]::MinValue
    if ($Now -notmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$' -or
        -not [DateTime]::TryParseExact($Now, 'yyyy-MM-ddTHH:mm', [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::None, [ref]$parsed)) {
        throw "-Now must look like 2026-10-09T09:30 (Singapore time), got '$Now'"
    }
} else {
    $sgt = [System.TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, 'Singapore Standard Time')
    $Now = $sgt.ToString('yyyy-MM-ddTHH:mm')
}

$mode = if ($Apply) { 'apply' } else { 'preflight' }
Write-Host ("Environment: {0} (ec2-user@{1}), secret {2}" -f $Env, $sshHost, $secretId)
Write-Host "Seed run time (SGT): $Now"
Write-Host ("Personas: " + (($targets | ForEach-Object { $_.Split(' ')[0] }) -join ', '))
if (-not $Apply) { Write-Host 'Dry run: read-only checks only. Add -Apply to reseed.' }

# Stage the upload locally with LF line endings, so a CRLF checkout cannot break bash on the host.
$stage = Join-Path ([IO.Path]::GetTempPath()) ('fitsync-demo-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Write-Lf([string]$Source, [string]$Target) {
    $text = [IO.File]::ReadAllText($Source, $utf8)
    [IO.File]::WriteAllText($Target, $text.Replace("`r`n", "`n"), $utf8)
}
$uploads = @()
$remoteScript = Join-Path $stage 'demo-cloud-remote.sh'
Write-Lf (Join-Path $PSScriptRoot 'demo-cloud-remote.sh') $remoteScript
$uploads += $remoteScript
$targetsFile = Join-Path $stage 'targets.txt'
[IO.File]::WriteAllText($targetsFile, (($targets -join "`n") + "`n"), $utf8)
$uploads += $targetsFile
if ($Apply) {
    $seedFile = Join-Path $stage 'seed-demo-fitsync.sql'
    Write-Lf (Join-Path $PSScriptRoot 'seed-demo-fitsync.sql') $seedFile
    $uploads += $seedFile
}

$sshOpts = @('-i', $keyPath, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=15')
$login = "ec2-user@$sshHost"
$remoteDir = '/tmp/fitsync-demo-' + [Guid]::NewGuid().ToString('N')
$exit = 0
try {
    & ssh @sshOpts $login "mkdir -m 700 $remoteDir"
    if ($LASTEXITCODE -ne 0) { throw "ssh to $login failed (exit $LASTEXITCODE)" }
    & scp @sshOpts -q @uploads "${login}:$remoteDir/"
    if ($LASTEXITCODE -ne 0) { throw "scp to $login failed (exit $LASTEXITCODE)" }
    & ssh @sshOpts $login "bash $remoteDir/demo-cloud-remote.sh $mode $Env $secretId $Now"
    $exit = $LASTEXITCODE
} finally {
    # The remote script removes its dir itself; this covers a failure before it started.
    & ssh @sshOpts $login "rm -rf $remoteDir"
    Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
}
if ($exit -eq 3) { exit 3 }  # the host already said which account or catalogue blocks the run
if ($exit -ne 0) { throw "Remote $mode failed (exit $exit)" }
if (-not $Apply) { return }

$base = if ($baseUrl) { $baseUrl } else { "<the $Env site address>" }
Write-Host ''
Write-Host "Reseed on $Env done. Coach eval for this seed:"
Write-Host ("  python scripts\coach-eval\run_coach_eval.py --now {0} --base {1} --token-file .local\demo.jwt --out .local\coach-eval-{2}.md" -f $Now, $base, $Env)
Write-Host 'Eyeball, signed in as steady unless noted (scripts\README.md has the numbers per weekday):'
@(
    'Home: streak about two weeks, ending yesterday; weekly goal of six sessions (Friday: two to go); today has breakfast only if the seed ran after 07:30',
    'Workout Today: the plan days before today are done, today''s plan day is still to start; rest days of the last two weeks carry a short mobility session',
    'Nutrition Diary: every day logged, last Sunday a fasted day, yesterday complete',
    'Recipes: three cooks in the history, Leftovers offers two (Salmon Avocado Salad 1, Spanish Chicken 2)',
    'Progress: the week rule line, weight trend down with yesterday''s weigh-in, energy view',
    'Me: Member since the first day of the history (59 days back), footprint grid filled, badges earned (Four full weeks among them); month panel: last month 3 of 3 full weeks on goal',
    'drifting (Alex Tan): streak 0, plan days missed, last weigh-in 10 days ago, no meals for 4 days, goal pace behind',
    'fresh (Sam Lee): every page shows its empty state and the first tour starts',
    'Admin console: sign in as the admin account'
) | ForEach-Object { Write-Host ("  [ ] " + $_) }
