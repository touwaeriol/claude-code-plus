#!/bin/bash

echo "=== 编译和运行 ClaudeCliWrapper 测试 ==="
echo

# 使用 Gradle 编译
echo "1. 编译项目..."
./gradlew compileKotlin compileTestKotlin

if [ $? -ne 0 ]; then
    echo "✗ 编译失败"
    exit 1
fi

echo
echo "2. 运行测试..."

# 获取类路径
CP=""
# 添加编译输出目录
CP="$CP:build/classes/kotlin/main"
CP="$CP:build/classes/kotlin/test"

# 添加依赖
for jar in build/dependencies/*.jar; do
    if [ -f "$jar" ]; then
        CP="$CP:$jar"
    fi
done

# 如果没有依赖目录，使用 gradle 获取
if [ ! -d "build/dependencies" ]; then
    echo "获取依赖..."
    mkdir -p build/dependencies
    ./gradlew copyDependencies 2>/dev/null || {
        # 如果没有 copyDependencies 任务，手动查找
        find ~/.gradle/caches/modules-2/files-2.1 -name "*.jar" | grep -E "(kotlin-stdlib|kotlinx-coroutines|jackson)" > deps.txt
        while read jar; do
            cp "$jar" build/dependencies/ 2>/dev/null
        done < deps.txt
        rm deps.txt
    }
fi

# 重新构建类路径
CP="build/classes/kotlin/main:build/classes/kotlin/test"
for jar in build/dependencies/*.jar; do
    if [ -f "$jar" ]; then
        CP="$CP:$jar"
    fi
done

# 运行测试
java -cp "$CP" com.claudecodeplus.test.TestCliWrapperSimpleKt

echo
echo "=== 测试完成 ==="