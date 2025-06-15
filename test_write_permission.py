#!/usr/bin/env python3
"""
测试日志目录的写入权限
"""

import os
import datetime

logs_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs")
print(f"日志目录: {logs_dir}")
print(f"目录存在: {os.path.exists(logs_dir)}")
print(f"是目录: {os.path.isdir(logs_dir)}")
print(f"可写: {os.access(logs_dir, os.W_OK) if os.path.exists(logs_dir) else 'N/A'}")
print()

# 尝试创建一个测试文件
test_file = os.path.join(logs_dir, f"test_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.txt")
try:
    with open(test_file, 'w') as f:
        f.write("测试写入权限\n")
        f.write(f"时间: {datetime.datetime.now()}\n")
    print(f"成功创建测试文件: {test_file}")
    
    # 读取并显示内容
    with open(test_file, 'r') as f:
        print("文件内容:")
        print(f.read())
    
    # 删除测试文件
    os.remove(test_file)
    print("测试文件已删除")
    
except Exception as e:
    print(f"写入测试失败: {e}")