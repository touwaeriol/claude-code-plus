#!/bin/bash

# Claude Code Plus 桌面应用启动脚本
# 使用方法：
#   ./scripts/start-desktop.sh          # 运行增强版
#   ./scripts/start-desktop.sh basic    # 运行基础版
#   ./scripts/start-desktop.sh release  # 运行 Release 版本

set -e

# 获取项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 检查参数
MODE=${1:-enhanced}

echo "🚀 启动 Claude Code Plus 桌面应用..."
echo "📁 项目目录: $PROJECT_ROOT"
echo "🎯 启动模式: $MODE"

case $MODE in
    "basic")
        echo "🔧 切换到基础版本..."
        # 临时修改 build.gradle.kts 使用 MainKt
        sed -i.bak 's/EnhancedMainKt/MainKt/g' desktop/build.gradle.kts
        echo "▶️  启动基础版桌面应用..."
        ./gradlew :desktop:run
        # 恢复原配置
        mv desktop/build.gradle.kts.bak desktop/build.gradle.kts 2>/dev/null || true
        ;;
    "release")
        echo "🏗️  构建并运行 Release 版本..."
        ./gradlew :desktop:runRelease
        ;;
    "enhanced"|*)
        echo "▶️  启动增强版桌面应用..."
        ./gradlew :desktop:run
        ;;
esac

echo "✅ 应用已启动！"