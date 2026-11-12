param([switch]$NoBrowser)
. "$PSScriptRoot/tools.ps1"
$Url = 'http://127.0.0.1:8088'
try {
    $Existing = Invoke-RestMethod "$Url/api/health" -TimeoutSec 2
    if ($Existing.data.application -eq 'notice-hub') {
        Write-Host "Notice Hub is already running: $Url"
        if (-not $NoBrowser) { Start-Process $Url }
        exit 0
    }
} catch {}
if (Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue) { throw 'Port 8088 is used by another service.' }
if (-not (Test-Path -LiteralPath (Join-Path $LocalPath 'backend.properties'))) { throw 'Missing .local/backend.properties. See docs/SETUP.md.' }
try {
    # Keep a WSL client open: systemd services alone do not keep WSL alive.
    $KeepalivePath = Join-Path $LocalPath 'wsl-keepalive.json'
    $KeepaliveRunning = $false
    if (Test-Path -LiteralPath $KeepalivePath) {
        $SavedKeepalive = Get-Content -LiteralPath $KeepalivePath -Raw | ConvertFrom-Json
        $ExistingKeepalive = Get-CimInstance Win32_Process -Filter "ProcessId=$($SavedKeepalive.pid)" -ErrorAction SilentlyContinue
        $KeepaliveRunning = $ExistingKeepalive -and $ExistingKeepalive.Name -eq 'wsl.exe' -and $ExistingKeepalive.CommandLine -like '*sleep infinity*'
    }
    if (-not $KeepaliveRunning) {
        $Keepalive = Start-Process -FilePath 'wsl.exe' -ArgumentList @('-e','sleep','infinity') -WindowStyle Hidden -PassThru
        @{pid=$Keepalive.Id} | ConvertTo-Json | Set-Content -LiteralPath $KeepalivePath -Encoding UTF8
    }
    $RedisReply = & wsl.exe -e sh -lc 'redis-cli ping 2>/dev/null'
    if ($RedisReply -notmatch 'PONG') {
        & wsl.exe -e sh -lc 'redis-server --daemonize yes --bind 127.0.0.1'
        if ($LASTEXITCODE -ne 0) { throw 'Redis could not be started' }
    }
} catch { throw 'WSL Redis is unavailable. Start Redis before launching Notice Hub.' }
$RedisReady = $false
for ($Check=0; $Check -lt 30; $Check++) {
    $Client = New-Object Net.Sockets.TcpClient
    try { $Client.Connect('127.0.0.1',6379); $RedisReady=$true; break } catch { Start-Sleep -Milliseconds 200 } finally { $Client.Dispose() }
}
if (-not $RedisReady) { throw 'Windows cannot reach WSL Redis on localhost:6379. See docs/SETUP.md.' }
if (-not (Test-Path -LiteralPath $JarPath)) { & "$PSScriptRoot/build.ps1" }
$Process = Start-Process -FilePath (Join-Path $JavaHomePath 'bin/java.exe') -ArgumentList @('-Dfile.encoding=UTF-8','-Duser.timezone=Asia/Shanghai','-jar',$JarPath) -WorkingDirectory (Join-Path $ProjectRoot 'backend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $LocalPath 'backend.out.log') -RedirectStandardError (Join-Path $LocalPath 'backend.err.log') -PassThru
@{pid=$Process.Id;jar=$JarPath;startedAt=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $LocalPath 'runtime.json') -Encoding UTF8
for ($Attempt=0; $Attempt -lt 90; $Attempt++) {
    if ($Process.HasExited) { throw 'Backend exited during startup. Check .local/backend.out.log and backend.err.log.' }
    try {
        $Health = Invoke-RestMethod "$Url/api/health" -TimeoutSec 2
        if ($Health.data.application -eq 'notice-hub') {
            Write-Host "Notice Hub is ready: $Url"
            Write-Host 'Demo accounts: .local/demo-accounts.json or .local/演示账号.md'
            if (-not $NoBrowser) { Start-Process $Url }
            exit 0
        }
    } catch {}
    Start-Sleep -Seconds 1
}
throw 'Startup timed out. Check the backend logs.'
