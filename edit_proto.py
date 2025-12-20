#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Script to edit proto file to add ModelInfoProto"""

with open('ai-agent-proto/src/main/proto/ai_agent_rpc.proto', 'r', encoding='utf-8') as f:
    content = f.read()

old_text = '  bool default_chrome_enabled = 12;      // 默认启用 Chrome 扩展\n}\n\n// 思考级别配置'

new_text = '''  bool default_chrome_enabled = 12;      // 默认启用 Chrome 扩展
  repeated ModelInfoProto available_models = 13;  // 所有可用模型（内置 + 自定义）
}

// 模型信息（内置模型和自定义模型通用）
message ModelInfoProto {
  string id = 1;            // 唯一标识：内置模型用枚举名（如 "OPUS_45"），自定义用 "custom_xxx"
  string display_name = 2;  // 显示名称（如 "Opus 4.5", "My Custom Model"）
  string model_id = 3;      // 实际模型 ID（如 "claude-opus-4-5-20251101"）
  bool is_built_in = 4;     // 是否为内置模型
}

// 思考级别配置'''

if old_text in content:
    content = content.replace(old_text, new_text)
    with open('ai-agent-proto/src/main/proto/ai_agent_rpc.proto', 'w', encoding='utf-8') as f:
        f.write(content)
    print('✅ Proto file updated successfully!')
else:
    print('⚠️ Target text not found in proto file')
