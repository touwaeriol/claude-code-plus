# Claude Code Plus - 启动独立服务器脚本 (Windows PowerShell)

# 设置项目根目录
$env:CLAUDE_PROJECT_ROOT = $PSScriptRoot

Write-Host "🚀 Starting Claude Code Plus Server..." -ForegroundColor Green
Write-Host "📂 Project Root: $env:CLAUDE_PROJECT_ROOT" -ForegroundColor Cyan

# 启动服务器
.\gradlew :claude-code-server:run

