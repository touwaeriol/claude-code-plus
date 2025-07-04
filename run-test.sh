#!/bin/bash

# 运行测试应用的脚本
# 使用方法: ./run-test.sh [项目路径]

PROJECT_PATH="${1:-$(pwd)}"

echo "Starting Claude Code Plus Test App"
echo "Project Path: $PROJECT_PATH"
echo "-----------------------------------"

# 编译代码
echo "Compiling..."
./gradlew compileKotlin --quiet

# 运行测试应用
echo "Running test app..."
./gradlew :toolwindow-test:run --args="$PROJECT_PATH" --console=plain