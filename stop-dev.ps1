$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

function Stop-ProcessTree {
    param([Parameter(Mandatory = $true)][int] $ProcessId)

    if (!(Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
        return
    }

    Write-Host "Stopping process tree PID $ProcessId..." -ForegroundColor Cyan
    taskkill /PID $ProcessId /T /F | Out-Null
}

function Stop-PortOwner {
    param([Parameter(Mandatory = $true)][int] $Port)

    $owners = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
        Where-Object { $_.State -eq "Listen" } |
        Select-Object -ExpandProperty OwningProcess -Unique

    foreach ($owner in $owners) {
        if ($owner) {
            Write-Host "Stopping process listening on port $Port (PID $owner)..." -ForegroundColor Cyan
            taskkill /PID $owner /T /F | Out-Null
        }
    }
}

Write-Host "Stopping LexiFlow backend/frontend..." -ForegroundColor Cyan
$PidFile = "$Root\.lexiflow-dev.pids"
if (Test-Path $PidFile) {
    Get-Content $PidFile | ForEach-Object {
        $line = $_.Trim()
        if (!$line -or !$line.Contains("=")) {
            return
        }
        $name, $value = $line.Split("=", 2)
        if ($value -match "^\d+$") {
            Stop-ProcessTree ([int] $value)
        }
    }
    Remove-Item $PidFile -Force
}

Stop-PortOwner 8080
Stop-PortOwner 5173

Write-Host "Stopping LexiFlow Docker dependencies..." -ForegroundColor Cyan
docker compose down

Write-Host "LexiFlow stopped." -ForegroundColor Green
