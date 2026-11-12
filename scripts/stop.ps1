. "$PSScriptRoot/tools.ps1"
$RuntimePath = Join-Path $LocalPath 'runtime.json'
if (-not (Test-Path -LiteralPath $RuntimePath)) { Write-Host 'No saved Notice Hub process.'; exit 0 }
$Runtime = Get-Content -LiteralPath $RuntimePath -Raw | ConvertFrom-Json
$Running = Get-CimInstance Win32_Process -Filter "ProcessId=$($Runtime.pid)" -ErrorAction SilentlyContinue
if ($Running) {
    if ($Running.Name -ne 'java.exe' -or $Running.CommandLine -notlike '*notice-hub-1.0.0.jar*') { throw 'Saved PID is not the Notice Hub backend; refusing to stop it.' }
    Stop-Process -Id $Runtime.pid -ErrorAction Stop
    Write-Host 'Notice Hub stopped. MySQL and Redis remain running.'
} else { Write-Host 'Notice Hub is already stopped.' }
