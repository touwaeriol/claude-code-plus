#!/bin/bash

# 代理管理脚本
PROXY_HOST="127.0.0.1"
PROXY_PORT="7890"
PROXY_URL="http://${PROXY_HOST}:${PROXY_PORT}"

function proxy_on() {
    export HTTP_PROXY=$PROXY_URL
    export HTTPS_PROXY=$PROXY_URL
    export http_proxy=$PROXY_URL
    export https_proxy=$PROXY_URL
    export NO_PROXY="localhost,127.0.0.1,*.local"
    export no_proxy="localhost,127.0.0.1,*.local"
    
    git config --global http.proxy $PROXY_URL
    git config --global https.proxy $PROXY_URL
    
    echo "✅ 代理已开启: $PROXY_URL"
}

function proxy_off() {
    unset HTTP_PROXY
    unset HTTPS_PROXY
    unset http_proxy
    unset https_proxy
    unset NO_PROXY
    unset no_proxy
    
    git config --global --unset http.proxy
    git config --global --unset https.proxy
    
    echo "❌ 代理已关闭"
}

function proxy_status() {
    echo "=== 代理状态 ==="
    echo "HTTP_PROXY: ${HTTP_PROXY:-未设置}"
    echo "HTTPS_PROXY: ${HTTPS_PROXY:-未设置}"
    echo "Git http.proxy: $(git config --global --get http.proxy || echo '未设置')"
    echo "Git https.proxy: $(git config --global --get https.proxy || echo '未设置')"
}

# 主逻辑
case "$1" in
    on)
        proxy_on
        ;;
    off)
        proxy_off
        ;;
    status)
        proxy_status
        ;;
    *)
        echo "使用方法: $0 {on|off|status}"
        echo "  on     - 开启代理"
        echo "  off    - 关闭代理"
        echo "  status - 查看代理状态"
        ;;
esac