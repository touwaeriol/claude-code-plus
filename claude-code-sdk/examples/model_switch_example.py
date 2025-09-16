#!/usr/bin/env python3
"""
Claude Code SDK 模型切换测试示例

按照用户要求的测试流程：
1. connect
2. /model opus
3. 询问模型id
4. /modle sonnet (故意打错)
5. /model sonnet (正确)
6. 询问模型id
"""

import asyncio
import sys
import os

# 添加SDK路径 (假设Python SDK在同级目录)
sys.path.append(os.path.join(os.path.dirname(__file__), '..', '..', 'python-sdk'))

try:
    from claude_code_sdk import ClaudeSDKClient, ClaudeCodeOptions
except ImportError:
    print("❌ 无法导入Claude Code SDK")
    print("请确保Python SDK已正确安装")
    sys.exit(1)

async def test_model_switching():
    """测试模型切换功能的完整流程"""
    
    # 检查API密钥
    api_key = os.getenv("CLAUDE_API_KEY")
    if not api_key:
        print("❌ 未找到CLAUDE_API_KEY环境变量")
        print("请设置您的Claude API密钥：export CLAUDE_API_KEY='your-key-here'")
        return
    
    print("🚀 开始模型切换测试")
    print("=" * 50)
    
    # 配置选项
    options = ClaudeCodeOptions(
        model="claude-3-5-sonnet",
        allowed_tools=["Read", "Write"],
        permission_mode="acceptEdits"
    )
    
    async with ClaudeSDKClient(options=options) as client:
        
        # 1. Connect (已通过 async with 自动连接)
        print("✅ 1. 已连接到Claude Code CLI")
        
        # 2. 切换到Opus
        print("\n📝 2. 发送命令: /model opus")
        await client.query("/model opus")
        
        # 接收切换响应
        async for message in client.receive_response():
            if hasattr(message, 'content') and message.content:
                if hasattr(message.content[0], 'text'):
                    print(f"🤖 切换响应: {message.content[0].text}")
            if hasattr(message, 'subtype') and message.subtype:
                print(f"✅ 切换结果: {message.subtype}")
                break
        
        # 3. 询问模型ID
        print("\n❓ 3. 询问当前模型ID")
        await client.query("What is your exact model ID? Please be specific about which Claude model you are.")
        
        # 接收模型ID响应
        async for message in client.receive_response():
            if hasattr(message, 'content') and message.content:
                if hasattr(message.content[0], 'text'):
                    print(f"🤖 模型ID响应: {message.content[0].text}")
            if hasattr(message, 'model'):
                print(f"📋 消息中的模型字段: {message.model}")
            if hasattr(message, 'subtype') and message.subtype:
                print(f"✅ 查询结果: {message.subtype}")
                break
        
        # 4. 发送错误命令 (故意打错)
        print("\n❌ 4. 发送错误命令: /modle sonnet (故意打错)")
        await client.query("/modle sonnet")
        
        # 接收错误命令响应
        async for message in client.receive_response():
            if hasattr(message, 'content') and message.content:
                if hasattr(message.content[0], 'text'):
                    print(f"⚠️ 错误命令响应: {message.content[0].text}")
            if hasattr(message, 'subtype') and message.subtype:
                print(f"❌ 错误命令结果: {message.subtype}")
                break
        
        # 5. 发送正确命令
        print("\n📝 5. 发送正确命令: /model sonnet")
        await client.query("/model sonnet")
        
        # 接收正确切换响应
        async for message in client.receive_response():
            if hasattr(message, 'content') and message.content:
                if hasattr(message.content[0], 'text'):
                    print(f"🤖 正确切换响应: {message.content[0].text}")
            if hasattr(message, 'subtype') and message.subtype:
                print(f"✅ 正确切换结果: {message.subtype}")
                break
        
        # 6. 再次询问模型ID
        print("\n❓ 6. 再次询问模型ID")
        await client.query("What is your model ID now? Have you switched back to Sonnet?")
        
        # 接收最终模型ID响应
        async for message in client.receive_response():
            if hasattr(message, 'content') and message.content:
                if hasattr(message.content[0], 'text'):
                    print(f"🤖 最终模型ID响应: {message.content[0].text}")
            if hasattr(message, 'model'):
                print(f"📋 最终消息中的模型字段: {message.model}")
            if hasattr(message, 'subtype') and message.subtype:
                print(f"✅ 最终查询结果: {message.subtype}")
                break
        
        print("\n🎉 模型切换测试完成!")
        print("=" * 50)

def print_usage_summary():
    """打印使用说明"""
    print("\n📚 Claude Code SDK 模型切换命令说明")
    print("=" * 50)
    print("✅ 有效命令:")
    print("   /model opus    - 切换到Claude 3 Opus")
    print("   /model sonnet  - 切换到Claude 3.5 Sonnet")
    print("   /model haiku   - 切换到Claude 3 Haiku")
    print("\n❌ 无效命令:")
    print("   /modle sonnet  - 拼写错误 (应该是 model)")
    print("   /mode opus     - 命令名错误 (应该是 model)")
    print("\n🔄 使用流程:")
    print("   1. 连接 Claude Code CLI")
    print("   2. 发送切换命令: client.query('/model <模型名>')")
    print("   3. 处理响应消息")
    print("   4. 可选：询问当前模型ID进行验证")

if __name__ == "__main__":
    print("🧪 Claude Code SDK 模型切换测试")
    print_usage_summary()
    
    try:
        asyncio.run(test_model_switching())
    except KeyboardInterrupt:
        print("\n⏹️ 测试被用户中断")
    except Exception as e:
        print(f"\n❌ 测试过程中出现错误: {e}")
        import traceback
        traceback.print_exc()