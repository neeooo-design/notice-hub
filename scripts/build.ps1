. "$PSScriptRoot/tools.ps1"
Push-Location (Join-Path $ProjectRoot 'frontend')
try {
    if (-not (Test-Path -LiteralPath 'node_modules')) {
        & npm.cmd ci --no-fund --no-audit
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
    $StaticPath = Join-Path $ProjectRoot 'backend/src/main/resources/static'
    New-Item -ItemType Directory -Path $StaticPath -Force | Out-Null
    # Only remove the generated assets directory belonging to this project.
    $AssetsPath = [IO.Path]::GetFullPath((Join-Path $StaticPath 'assets'))
    $ExpectedAssets = [IO.Path]::GetFullPath((Join-Path $ProjectRoot 'backend/src/main/resources/static/assets'))
    if ($AssetsPath -ne $ExpectedAssets) { throw 'Unexpected assets path' }
    if (Test-Path -LiteralPath $AssetsPath) { Remove-Item -LiteralPath $AssetsPath -Recurse -Force }
    Copy-Item -Path 'dist/*' -Destination $StaticPath -Recurse -Force
} finally { Pop-Location }
Push-Location (Join-Path $ProjectRoot 'backend')
try {
    & $MavenPath -B -q package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
} finally { Pop-Location }
Write-Host "Build complete: $JarPath"
