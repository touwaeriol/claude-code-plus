#!/bin/bash
echo "检查 Claude 状态..."
echo "1. Claude 版本:"
claude --version

echo -e "\n2. 测试简单命令:"
echo "What is 1+1?" | timeout 10 claude api 2>&1 | head -5 || echo "命令超时或失败"

echo -e "\n3. Python SDK 测试:"
python3 -c "
import claude_code_sdk
print('Claude Code SDK 已安装')
"

echo -e "\n完成检查"