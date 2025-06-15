#!/bin/bash

echo "查找 IntelliJ IDEA 日志文件..."

# 查找可能的日志位置
POSSIBLE_PATHS=(
    "$HOME/Library/Logs/JetBrains/IdeaIC*"
    "$HOME/.cache/JetBrains/IdeaIC*/log"
    "$HOME/codes/idea/claude-code-plus/build/idea-sandbox/*/system/log"
)

for path in "${POSSIBLE_PATHS[@]}"; do
    if ls $path 2>/dev/null | grep -E "idea\.log$" > /dev/null; then
        echo "找到日志目录: $path"
        LATEST_LOG=$(ls -t $path/idea.log* 2>/dev/null | head -1)
        if [ -f "$LATEST_LOG" ]; then
            echo "最新日志文件: $LATEST_LOG"
            echo ""
            echo "=== 查找 Claude Code Plus 相关错误 ==="
            grep -n -A 5 -B 5 -E "(claudecodeplus|ResponseLogger|ERROR.*claude)" "$LATEST_LOG" | tail -100
        fi
    fi
done