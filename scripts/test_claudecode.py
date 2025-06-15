#!/usr/bin/env python3
"""
测试 claudecode SDK 安装
"""
import sys

print("Testing claudecode installation...")

try:
    import claudecode
    print(f"✓ claudecode is installed")
    print(f"  Version: {getattr(claudecode, '__version__', 'unknown')}")
    print(f"  Location: {claudecode.__file__}")
    
    # 尝试导入具体的类
    from claudecode import query, ClaudeCodeOptions, PermissionMode
    print("✓ Core classes imported successfully")
    
except ImportError as e:
    print(f"✗ claudecode is NOT installed")
    print(f"  Error: {e}")
    print("\nTo install claudecode, run:")
    print("  pip install claudecode")
    
    # 检查是否有类似的包
    print("\nChecking for similar packages...")
    try:
        import claude
        print("  Found 'claude' package (different from claudecode)")
    except:
        pass
        
    try:
        import anthropic
        print("  Found 'anthropic' package (official Anthropic SDK)")
    except:
        pass

# 检查环境变量
import os
api_key = os.getenv('CLAUDE_API_KEY', os.getenv('ANTHROPIC_API_KEY'))
if api_key:
    print(f"\n✓ API key found (length: {len(api_key)})")
else:
    print("\n✗ No API key found in environment variables")
    print("  Set CLAUDE_API_KEY or ANTHROPIC_API_KEY")