$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

function Import-DotEnv {
    param([Parameter(Mandatory = $true)][string] $Path)

    if (!(Test-Path $Path)) {
        return
    }

    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (!$line -or $line.StartsWith("#") -or !$line.Contains("=")) {
            return
        }

        $key, $value = $line.Split("=", 2)
        $key = $key.Trim()
        $value = $value.Trim().Trim('"').Trim("'")
        if ($key) {
            Set-Item -Path "Env:$key" -Value $value
        }
    }
}

function Require-Env {
    param([Parameter(Mandatory = $true)][string] $Name)

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value) -or $value.StartsWith("replace-with-")) {
        throw "$Name is required. Add it to .env or set it in this PowerShell session."
    }
}

Import-DotEnv "$Root\.env"
if ([string]::IsNullOrWhiteSpace($env:LEXIFLOW_LLM_PROVIDER)) {
    $env:LEXIFLOW_LLM_PROVIDER = "deepseek"
}

if ($env:LEXIFLOW_LLM_PROVIDER -eq "deepseek") {
    Require-Env "DEEPSEEK_API_KEY"
    Require-Env "DASHSCOPE_API_KEY"
}

Write-Host "Checking Docker Desktop..." -ForegroundColor Cyan
docker version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Desktop is not running. Start Docker Desktop first, then run .\start-dev.ps1 again."
}

Write-Host "Starting LexiFlow dependencies..." -ForegroundColor Cyan
docker compose up -d postgres redis rabbitmq
if ($LASTEXITCODE -ne 0) {
    throw "Failed to start Docker dependencies."
}

Write-Host "Starting backend..." -ForegroundColor Cyan
$BackendProcess = Start-Process powershell -PassThru -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd '$Root\backend'; mvn spring-boot:run '-Dspring-boot.run.profiles=dev'"
)

Write-Host "Starting frontend..." -ForegroundColor Cyan
$FrontendProcess = Start-Process powershell -PassThru -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd '$Root\frontend'; npm.cmd install; npm.cmd run dev"
)

@"
backendShellPid=$($BackendProcess.Id)
frontendShellPid=$($FrontendProcess.Id)
"@ | Set-Content -Path "$Root\.lexiflow-dev.pids" -Encoding UTF8

Write-Host ""
Write-Host "LexiFlow is starting." -ForegroundColor Green
Write-Host "Frontend:  http://localhost:5173"
Write-Host "Backend:   http://localhost:8080/api"
Write-Host "Health:    http://localhost:8080/api/actuator/health"
Write-Host "RabbitMQ:  http://localhost:15672"
Write-Host "Account:   admin / admin123"
Write-Host "LLM:       $env:LEXIFLOW_LLM_PROVIDER"
