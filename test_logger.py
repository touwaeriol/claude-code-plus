#!/usr/bin/env python3
"""
测试日志记录功能
"""

import os
import sys

# 添加项目路径
project_dir = os.path.dirname(os.path.abspath(__file__))
logs_dir = os.path.join(project_dir, "logs")

print(f"项目目录: {project_dir}")
print(f"日志目录: {logs_dir}")
print(f"日志目录存在: {os.path.exists(logs_dir)}")
print()

if os.path.exists(logs_dir):
    files = os.listdir(logs_dir)
    print(f"日志目录中的文件数: {len(files)}")
    for f in files:
        if f.endswith('.log'):
            file_path = os.path.join(logs_dir, f)
            size = os.path.getsize(file_path)
            print(f"  - {f} ({size} bytes)")
else:
    print("日志目录不存在")

# 检查是否有旧的日志位置
old_log_dir = os.path.expanduser("~/.claude-code-plus/logs")
if os.path.exists(old_log_dir):
    print(f"\n旧日志目录存在: {old_log_dir}")
    old_files = [f for f in os.listdir(old_log_dir) if f.endswith('.log')]
    print(f"旧日志文件数: {len(old_files)}")
    for f in old_files[:5]:  # 只显示前5个
        print(f"  - {f}")