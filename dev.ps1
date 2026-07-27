# AIMS 本地开发环境一键脚本
# 用法: ./dev.ps1 up | down | logs | ps | reset
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'down', 'logs', 'ps', 'reset')]
    [string]$Command = 'up'
)

$ErrorActionPreference = 'Stop'
$ComposeDir  = Join-Path $PSScriptRoot 'docker'
$ComposeFile = Join-Path $ComposeDir 'docker-compose.yml'
$EnvFile     = Join-Path $ComposeDir '.env'

if (-not (Test-Path $EnvFile)) {
    Copy-Item (Join-Path $ComposeDir '.env.example') $EnvFile
    Write-Host "[dev] 已从 .env.example 生成 docker/.env，如需修改密码请编辑后重跑。" -ForegroundColor Yellow
}

switch ($Command) {
    'up' {
        docker compose --env-file $EnvFile -f $ComposeFile up -d
        docker compose --env-file $EnvFile -f $ComposeFile ps
    }
    'down'  { docker compose --env-file $EnvFile -f $ComposeFile down }
    'logs'  { docker compose --env-file $EnvFile -f $ComposeFile logs -f }
    'ps'    { docker compose --env-file $EnvFile -f $ComposeFile ps }
    'reset' {
        Write-Host "[dev] 将删除容器与数据卷（PG/Redis/Kafka/MinIO 数据全部清空）" -ForegroundColor Red
        docker compose --env-file $EnvFile -f $ComposeFile down -v --remove-orphans
    }
}
