#!/usr/bin/env python3
"""
测试 ANSI 编码和 Markdown 格式的日志记录
"""

import os
import glob
import time
from datetime import datetime

# 查找最新的日志文件
# 使用项目目录下的 logs 目录
log_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs")
if not os.path.exists(log_dir):
    print(f"日志目录不存在: {log_dir}")
    print("日志文件将在使用插件时自动创建在项目的 logs 目录中")
    exit(1)

# 获取最新的日志文件
log_files = glob.glob(os.path.join(log_dir, "session_*.log"))
if not log_files:
    print("没有找到日志文件")
    exit(1)

latest_log = max(log_files, key=os.path.getmtime)
print(f"最新日志文件: {latest_log}")
print(f"文件大小: {os.path.getsize(latest_log)} bytes")
print(f"修改时间: {datetime.fromtimestamp(os.path.getmtime(latest_log))}")
print("\n" + "="*60 + "\n")

# 读取并显示日志内容
with open(latest_log, 'r', encoding='utf-8') as f:
    content = f.read()
    
# 检查 ANSI 编码
ansi_sequences = []
import re
ansi_pattern = re.compile(r'\033\[[0-9;]*m')
for match in ansi_pattern.finditer(content):
    ansi_sequences.append((match.start(), match.group()))

if ansi_sequences:
    print(f"发现 {len(ansi_sequences)} 个 ANSI 转义序列:")
    for pos, seq in ansi_sequences[:5]:  # 显示前5个
        print(f"  位置 {pos}: {repr(seq)}")
    if len(ansi_sequences) > 5:
        print(f"  ... 还有 {len(ansi_sequences) - 5} 个")
else:
    print("未发现 ANSI 转义序列")

print("\n" + "-"*60 + "\n")

# 检查 Markdown 格式
md_features = {
    "代码块": re.compile(r'```[\s\S]*?```'),
    "标题": re.compile(r'^#+\s+.*$', re.MULTILINE),
    "列表项": re.compile(r'^[\s]*[-*+]\s+.*$', re.MULTILINE),
    "链接": re.compile(r'\[([^\]]+)\]\(([^)]+)\)'),
    "粗体": re.compile(r'\*\*([^*]+)\*\*'),
    "斜体": re.compile(r'\*([^*]+)\*'),
    "代码": re.compile(r'`([^`]+)`')
}

for feature, pattern in md_features.items():
    matches = list(pattern.finditer(content))
    if matches:
        print(f"{feature}: 找到 {len(matches)} 处")
        for match in matches[:3]:  # 显示前3个
            preview = match.group()[:50]
            if len(match.group()) > 50:
                preview += "..."
            print(f"  - {repr(preview)}")
        if len(matches) > 3:
            print(f"  ... 还有 {len(matches) - 3} 处")

print("\n" + "="*60 + "\n")

# 显示日志片段
print("日志内容预览 (前1000字符):")
print("-"*60)
print(content[:1000])
if len(content) > 1000:
    print(f"\n... 文件还有 {len(content) - 1000} 个字符")

# 统计信息
print("\n" + "="*60)
print("日志统计:")
print(f"- 总行数: {content.count(chr(10))}")
print(f"- 请求数: {content.count('[REQUEST]')}")
print(f"- 响应块数: {content.count('[RESPONSE CHUNK]')}")
print(f"- WebSocket 消息数: {content.count('[WS ')}")
print(f"- 错误数: {content.count('Error:')}")