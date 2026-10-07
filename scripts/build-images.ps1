# Build a runtime Docker image for each runnable FitSync service from the
# universal backend/Dockerfile. Run AFTER `mvn -DskipTests package`.
#
# Usage (PowerShell, from anywhere):
#   backend\scripts\build-images.ps1
#   $env:IMAGE_TAG = (git rev-parse --short HEAD); backend\scripts\build-images.ps1
#   $env:ECR_REGISTRY = "123456789012.dkr.ecr.ap-southeast-1.amazonaws.com"
#   $env:PUSH = "1"; backend\scripts\build-images.ps1
#
# notification-service is intentionally skipped: it is an empty placeholder module
# (pom.xml only, no source) and is not runnable.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')   # backend root (Docker build context)

$ImageTag    = if ($env:IMAGE_TAG) { $env:IMAGE_TAG } else { 'latest' }
$EcrRegistry = $env:ECR_REGISTRY
$Push        = ($env:PUSH -eq '1')

# service -> documented container port (must match application.yml SERVER_PORT default)
$services = [ordered]@{
  'api-gateway'       = 8088
  'auth-service'      = 8080
  'user-service'      = 8081
  'workout-service'   = 8082
  'nutrition-service' = 8083
  'analytics-service' = 8085
  'recommendation-service' = 8086
}

foreach ($svc in $services.Keys) {
  $port     = $services[$svc]
  $localTag = "fitsync-${svc}:${ImageTag}"

  Write-Host "==> building $localTag (port $port)"
  docker build -f Dockerfile --build-arg "SERVICE=$svc" --build-arg "SERVER_PORT=$port" -t $localTag .
  if ($LASTEXITCODE -ne 0) { throw "docker build failed for $svc" }

  if ($EcrRegistry) {
    $remoteTag = "${EcrRegistry}/fitsync-${svc}:${ImageTag}"
    docker tag $localTag $remoteTag
    if ($LASTEXITCODE -ne 0) { throw "docker tag failed for $svc" }
    if ($Push) {
      Write-Host "==> pushing $remoteTag"
      docker push $remoteTag
      if ($LASTEXITCODE -ne 0) { throw "docker push failed for $svc" }
    }
  }
}

Write-Host "done."
