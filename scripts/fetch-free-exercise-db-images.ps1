$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

$repoUrl = if ($env:FREE_EXERCISE_DB_REPO_URL) {
    $env:FREE_EXERCISE_DB_REPO_URL
} else {
    "https://github.com/yuhonas/free-exercise-db.git"
}
$ref = if ($env:FREE_EXERCISE_DB_REF) { $env:FREE_EXERCISE_DB_REF } else { "main" }
$cacheDir = if ($env:FREE_EXERCISE_DB_CACHE_DIR) {
    $env:FREE_EXERCISE_DB_CACHE_DIR
} else {
    ".local/free-exercise-db"
}

if ((Test-Path -LiteralPath $cacheDir) -and -not (Test-Path -LiteralPath (Join-Path $cacheDir ".git"))) {
    throw "Refusing to overwrite non-git path: $cacheDir"
}

$parent = Split-Path -Parent $cacheDir
if ($parent) {
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
}

if (Test-Path -LiteralPath (Join-Path $cacheDir ".git")) {
    git -C $cacheDir fetch --depth 1 origin $ref
    git -C $cacheDir checkout --detach FETCH_HEAD
} else {
    git clone --depth 1 --filter=blob:none --sparse --branch $ref $repoUrl $cacheDir
}

git -C $cacheDir sparse-checkout set exercises

Write-Output "free-exercise-db exercises available at $cacheDir/exercises"
Write-Output "docker compose mounts this path read-only into /opt/fitsync/free-exercise-db/exercises"
