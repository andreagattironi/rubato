# Watchdog tunnel Rubato -> Pi. Riavvia ssh -L se morto o se le porte non rispondono.
$ErrorActionPreference = 'SilentlyContinue'
function Test-Port($port) {
    try {
        $c = New-Object Net.Sockets.TcpClient
        $r = $c.BeginConnect('127.0.0.1', $port, $null, $null)
        $ok = $r.AsyncWaitHandle.WaitOne(3000)
        $c.Close()
        return $ok
    } catch { return $false }
}
$alive4533 = Test-Port 4533
$alive8000 = Test-Port 8000
if (-not $alive4533 -or -not $alive8000) {
    Get-Process ssh -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 2
    Start-Process -FilePath 'ssh' -ArgumentList 'pi -L 0.0.0.0:4533:localhost:4533 -L 0.0.0.0:8000:localhost:8000 -N -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o ExitOnForwardFailure=yes' -WindowStyle Minimized
    exit 1
}
exit 0
