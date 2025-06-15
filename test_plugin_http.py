#!/usr/bin/env python3
"""
测试插件与 Claude SDK 服务器的通信
确保 server.py 已经在端口 18080 上运行
"""

import requests
import json
import time

BASE_URL = "http://127.0.0.1:18080"

def test_health():
    """测试健康检查"""
    print("测试健康检查...")
    response = requests.get(f"{BASE_URL}/health")
    if response.status_code == 200:
        data = response.json()
        print(f"✓ 健康检查成功: {data}")
        return True
    else:
        print(f"✗ 健康检查失败: {response.status_code}")
        return False

def test_create_session():
    """测试创建会话"""
    print("\n测试创建会话...")
    response = requests.post(f"{BASE_URL}/session/create", json={})
    if response.status_code == 200:
        data = response.json()
        session_id = data.get('session_id')
        print(f"✓ 创建会话成功: {session_id}")
        return session_id
    else:
        print(f"✗ 创建会话失败: {response.status_code}")
        return None

def test_send_message(session_id=None):
    """测试发送消息"""
    print("\n测试发送消息...")
    payload = {
        "message": "你好，请用中文回答：1+1等于几？",
        "session_id": session_id
    }
    
    response = requests.post(f"{BASE_URL}/message", json=payload)
    if response.status_code == 200:
        data = response.json()
        if data.get('success'):
            print(f"✓ 发送消息成功: {data.get('response')[:100]}...")
        else:
            print(f"✗ 发送消息失败: {data.get('error')}")
    else:
        print(f"✗ 请求失败: {response.status_code}")

def test_stream_message(session_id=None):
    """测试流式消息"""
    print("\n测试流式消息...")
    payload = {
        "message": "请用中文简短回答：什么是Python？",
        "session_id": session_id
    }
    
    try:
        # 发送流式请求
        response = requests.post(f"{BASE_URL}/stream", json=payload, stream=True)
        
        if response.status_code == 200:
            print("✓ 开始接收流式响应:")
            
            # 处理 SSE 流
            for line in response.iter_lines():
                if line:
                    line_str = line.decode('utf-8')
                    if line_str.startswith('data: '):
                        data_str = line_str[6:]
                        
                        if data_str == '[DONE]':
                            print("\n✓ 流式响应完成")
                            break
                        
                        try:
                            data = json.loads(data_str)
                            if data.get('type') == 'text':
                                print(data.get('content', ''), end='', flush=True)
                            elif data.get('type') == 'error':
                                print(f"\n✗ 错误: {data.get('error')}")
                        except json.JSONDecodeError:
                            print(f"\n警告: 无法解析数据: {data_str}")
        else:
            print(f"✗ 流式请求失败: {response.status_code}")
            
    except Exception as e:
        print(f"✗ 流式请求异常: {e}")

def main():
    """主测试函数"""
    print("=" * 50)
    print("Claude SDK HTTP 服务器测试")
    print("=" * 50)
    
    # 1. 健康检查
    if not test_health():
        print("\n服务器未运行，请先启动 server.py")
        return
    
    # 2. 创建会话
    session_id = test_create_session()
    
    # 3. 测试普通消息
    test_send_message(session_id)
    
    # 4. 测试流式消息
    test_stream_message(session_id)
    
    print("\n" + "=" * 50)
    print("测试完成！")

if __name__ == "__main__":
    main()