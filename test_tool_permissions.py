#!/usr/bin/env python3
"""
测试工具权限配置
验证是否可以正确传递 allowed_tools 参数
"""

import requests
import json
import time

BASE_URL = "http://127.0.0.1:18080"

def test_tool_permissions():
    # 1. 初始化服务，允许所有工具
    print("=== 测试工具权限配置 ===")
    
    all_tools = [
        "Read", "Write", "Edit", "MultiEdit",
        "Bash", "Grep", "Glob", "LS",
        "WebSearch", "WebFetch",
        "TodoRead", "TodoWrite",
        "NotebookRead", "NotebookEdit",
        "Task", "exit_plan_mode"
    ]
    
    # 初始化配置
    init_config = {
        "config": {
            "system_prompt": "You are a helpful coding assistant",
            "allowed_tools": all_tools,
            "cwd": "/Users/erio/codes/idea/claude-code-plus",
            "skip_update_check": True
        }
    }
    
    print(f"初始化配置: {json.dumps(init_config, indent=2)}")
    
    response = requests.post(f"{BASE_URL}/initialize", json=init_config)
    print(f"初始化响应: {response.json()}")
    
    # 2. 发送测试消息，使用工具
    test_message = {
        "message": "列出当前目录下的所有文件",
        "options": {
            "allowed_tools": all_tools
        }
    }
    
    print(f"\n发送测试消息: {test_message['message']}")
    response = requests.post(f"{BASE_URL}/message", json=test_message)
    result = response.json()
    
    if result.get('success'):
        print(f"响应成功: {result.get('response', '')[:500]}...")
    else:
        print(f"响应失败: {result.get('error')}")
    
    # 3. 测试限制工具
    print("\n=== 测试限制工具 ===")
    limited_tools = ["Read", "LS"]  # 只允许读取和列表
    
    limited_message = {
        "message": "读取 README.md 文件",
        "options": {
            "allowed_tools": limited_tools
        }
    }
    
    print(f"只允许工具: {limited_tools}")
    print(f"发送消息: {limited_message['message']}")
    
    response = requests.post(f"{BASE_URL}/message", json=limited_message)
    result = response.json()
    
    if result.get('success'):
        print(f"响应成功: {result.get('response', '')[:500]}...")
    else:
        print(f"响应失败: {result.get('error')}")

if __name__ == "__main__":
    # 检查服务器健康状态
    try:
        response = requests.get(f"{BASE_URL}/health")
        if response.status_code == 200:
            print("服务器运行正常")
            test_tool_permissions()
        else:
            print("服务器未响应")
    except requests.ConnectionError:
        print("无法连接到服务器，请确保 unified_server.py 正在运行")