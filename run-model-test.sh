#!/bin/bash

echo "🧪 运行模型切换测试"
echo "========================"
echo ""

cd /Users/erio/codes/idea/claude-code-plus

# 确保已编译
echo "📦 确保 SDK 已编译..."
./gradlew :claude-code-sdk:compileKotlin -q

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"
echo ""

# 设置 classpath
SDK_BUILD="/Users/erio/codes/idea/claude-code-plus/claude-code-sdk/build/classes/kotlin/main"
GRADLE_CACHE="/Users/erio/.gradle/caches/modules-2/files-2.1"

# 查找必要的 JAR
COROUTINES_JAR=$(find $GRADLE_CACHE -name "kotlinx-coroutines-core-jvm-*.jar" | grep -v sources | head -1)
SERIALIZATION_JAR=$(find $GRADLE_CACHE -name "kotlinx-serialization-json-jvm-*.jar" | grep -v sources | head -1)
KOTLIN_STDLIB=$(find $GRADLE_CACHE -name "kotlin-stdlib-2.*.jar" | grep -v sources | head -1)

echo "📚 Classpath:"
echo "   SDK: $SDK_BUILD"
echo "   Coroutines: $COROUTINES_JAR"
echo "   Serialization: $SERIALIZATION_JAR"
echo "   Stdlib: $KOTLIN_STDLIB"
echo ""

# 运行测试
echo "🚀 运行 ModelIdentificationTest..."
echo "========================"
echo ""

java -cp "$SDK_BUILD:$COROUTINES_JAR:$SERIALIZATION_JAR:$KOTLIN_STDLIB" \
    com.claudecodeplus.sdk.examples.ModelIdentificationTestKt

EXIT_CODE=$?

echo ""
echo "========================"
if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ 测试完成"
else
    echo "❌ 测试失败，退出码: $EXIT_CODE"
fi

exit $EXIT_CODE