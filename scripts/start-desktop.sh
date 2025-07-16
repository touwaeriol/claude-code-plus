#!/bin/bash

# Claude Code Plus 桌面应用启动脚本
# 使用方法：
#   ./scripts/start-desktop.sh          # 运行桌面应用
#   ./scripts/start-desktop.sh release  # 运行 Release 版本

set -e

# 获取项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 检查参数
MODE=${1:-normal}

echo "🚀 启动 Claude Code Plus 桌面应用..."
echo "📁 项目目录: $PROJECT_ROOT"
echo "🎯 启动模式: $MODE"

case $MODE in
    "release")
        echo "🏗️  构建并运行 Release 版本..."
        ./gradlew :desktop:runRelease
        ;;
    *)
        echo "▶️  启动桌面应用..."
        ./gradlew :desktop:run
        ;;
esac

echo "✅ 应用已启动！"