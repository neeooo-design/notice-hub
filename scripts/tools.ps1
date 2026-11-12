$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$JavaHomePath = Join-Path $env:USERPROFILE '.jdks/temurin-17.0.20.1'
if (-not (Test-Path -LiteralPath (Join-Path $JavaHomePath 'bin/java.exe'))) {
    $JavaHomePath = $env:JAVA_HOME
}
if (-not $JavaHomePath -or -not (Test-Path -LiteralPath (Join-Path $JavaHomePath 'bin/java.exe'))) {
    throw 'JDK 17 not found. Set JAVA_HOME to your JDK 17 installation.'
}
$env:JAVA_HOME = $JavaHomePath
$env:PATH = (Join-Path $JavaHomePath 'bin') + ';' + $env:PATH
$MavenPath = Join-Path $env:USERPROFILE '.m2/wrapper/dists/apache-maven-3.10.0/dae9a06d/bin/mvn.cmd'
if (-not (Test-Path -LiteralPath $MavenPath)) {
    $MavenPath = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
}
if (-not $MavenPath) { throw 'Maven not found. Install Maven or adjust scripts/tools.ps1.' }
$JarPath = Join-Path $ProjectRoot 'backend/target/notice-hub-1.0.0.jar'
$LocalPath = Join-Path $ProjectRoot '.local'
