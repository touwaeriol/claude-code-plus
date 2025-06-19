#!/bin/bash

# Claude SDK Wrapper 启动脚本
# 自动检测 Node.js 环境并启动服务

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 默认端口
PORT=${1:-18080}

echo "🚀 启动 Claude SDK Wrapper 服务..."

# 检查 Node.js 是否可用
if ! command -v node &> /dev/null; then
    echo -e "${RED}❌ 错误：未找到 Node.js${NC}"
    echo -e "${YELLOW}请安装 Node.js 18.0.0 或更高版本 (Claude SDK 要求)${NC}"
    echo -e "${YELLOW}下载地址: https://nodejs.org/${NC}"
    exit 1
fi

# 获取 Node.js 版本
NODE_VERSION=$(node --version)
echo -e "${GREEN}✅ 找到 Node.js: $NODE_VERSION${NC}"

# 检查版本是否满足最低要求
MAJOR_VERSION=$(echo $NODE_VERSION | sed 's/v\([0-9]*\).*/\1/')
if [ "$MAJOR_VERSION" -lt 18 ]; then
    echo -e "${RED}❌ Node.js 版本过低: $NODE_VERSION${NC}"
    echo -e "${YELLOW}最低要求: Node.js 18.0.0 (Claude SDK 要求)${NC}"
    exit 1
fi

# 检查环境兼容性
echo "🔍 检查 JavaScript 特性支持..."
if ! npm run check-node; then
    echo -e "${RED}❌ 环境兼容性检查失败${NC}"
    exit 1
fi

echo "🔨 安装 TypeScript..."
npm install

echo "🔨 编译 TypeScript..."
npm run build


echo -e "${GREEN}✅ 环境检查通过${NC}"
echo "🌐 启动服务，端口: $PORT"

# 启动服务
exec node dist/server.js --port "$PORT" --host "127.0.0.1"


