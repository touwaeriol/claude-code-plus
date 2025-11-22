#!/bin/bash
# Claude Code Plus - 启动独立服务器脚本 (Linux/macOS)

# 设置项目根目录
export CLAUDE_PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "🚀 Starting Claude Code Plus Server..."
echo "📂 Project Root: $CLAUDE_PROJECT_ROOT"

# 启动服务器
./gradlew :claude-code-server:run

