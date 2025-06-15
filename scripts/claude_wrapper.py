#!/usr/bin/env python3
"""
Claude SDK 包装脚本
用于外部进程调用
"""
import sys
import json
import logging
from typing import Dict, Any

# 配置日志 - 写入stderr以避免干扰输出
logging.basicConfig(level=logging.INFO, format='%(message)s', stream=sys.stderr)
logger = logging.getLogger(__name__)

# 导入 claude-sdk-wrapper
try:
    # 添加项目路径
    import os
    if '__file__' in globals():
        script_dir = os.path.dirname(os.path.abspath(__file__))
        parent_dir = os.path.dirname(script_dir)
        wrapper_dir = os.path.join(parent_dir, 'claude-sdk-wrapper')
        if wrapper_dir not in sys.path:
            sys.path.insert(0, wrapper_dir)
    
    from claude_sdk_wrapper import ClaudeSDKWrapper as RealClaudeSDKWrapper
    # 检查真正的claudecode是否可用
    from claude_sdk_wrapper.wrapper import ClaudeCodeOptions
    CLAUDE_AVAILABLE = ClaudeCodeOptions is not None
except ImportError as e:
    # 静默处理导入错误，使用mock
    CLAUDE_AVAILABLE = False
    RealClaudeSDKWrapper = None
# 如果没有真实的SDK，创建mock
if not CLAUDE_AVAILABLE or RealClaudeSDKWrapper is None:
    class ClaudeSDKWrapper:
        def __init__(self):
            self.initialized = False
            
        def initialize(self, config):
            self.initialized = True
            return True
            
        def send_message(self, message):
            return {
                'success': True,
                'messages': [{
                    'type': 'assistant',
                    'content': [{
                        'type': 'text',
                        'text': f'Mock response to: {message}'
                    }]
                }]
            }

else:
    # 使用真实的SDK
    ClaudeSDKWrapper = RealClaudeSDKWrapper

class ClaudeProcessWrapper:
    """进程包装器"""
    
    def __init__(self):
        self.wrapper = ClaudeSDKWrapper()
        self.is_initialized = False
    
    def handle_command(self, command_data: Dict[str, Any]) -> Dict[str, Any]:
        """处理命令"""
        command = command_data.get('command')
        
        if command == 'initialize':
            return self.initialize(command_data.get('config', {}))
        elif command == 'send_message':
            return self.send_message(command_data.get('message', ''))
        elif command == 'exit':
            return {'success': True, 'message': 'Exiting'}
        else:
            return {'success': False, 'error': f'Unknown command: {command}'}
    
    def initialize(self, config: Dict[str, Any]) -> Dict[str, Any]:
        """初始化 SDK"""
        try:
            # 转换配置格式
            sdk_config = {
                'maxTurns': config.get('max_turns', 20),
                'permissionMode': 'ask',
                'systemPrompt': config.get('system_prompt'),
                'allowedDirectories': [],
                'blockedDirectories': [],
                'tools': []
            }
            
            result = self.wrapper.initialize(sdk_config)
            self.is_initialized = result if isinstance(result, bool) else result.get('success', False)
            
            # 如果真实SDK初始化失败，使用mock模式
            if not self.is_initialized and not CLAUDE_AVAILABLE:
                self.is_initialized = True  # Mock模式总是成功
            
            return {
                'success': self.is_initialized,
                'message': 'Initialized successfully' if self.is_initialized else 'Failed to initialize'
            }
        except Exception as e:
            return {'success': False, 'error': str(e)}
    
    def send_message(self, message: str) -> Dict[str, Any]:
        """发送消息"""
        if not self.is_initialized:
            return {'success': False, 'error': 'Not initialized'}
        
        try:
            result = self.wrapper.send_message(message)
            
            if result['success']:
                # 提取文本内容
                text_content = []
                for msg in result.get('messages', []):
                    if msg['type'] == 'assistant':
                        for content in msg.get('content', []):
                            if content['type'] == 'text':
                                text_content.append(content['text'])
                
                return {
                    'success': True,
                    'response': ' '.join(text_content)
                }
            else:
                return {
                    'success': False,
                    'error': result.get('error', 'Unknown error')
                }
                
        except Exception as e:
            return {'success': False, 'error': str(e)}

def main():
    """主循环"""
    wrapper = ClaudeProcessWrapper()
    
    # 发送就绪信号
    print(json.dumps({'status': 'ready'}))
    sys.stdout.flush()
    
    # 主循环
    while True:
        try:
            # 读取命令
            line = sys.stdin.readline()
            if not line:
                break
            
            # 解析命令
            command_data = json.loads(line.strip())
            
            # 处理命令
            result = wrapper.handle_command(command_data)
            
            # 返回结果
            print(json.dumps(result))
            sys.stdout.flush()
            
            # 检查退出
            if command_data.get('command') == 'exit':
                break
                
        except json.JSONDecodeError as e:
            error_response = {'success': False, 'error': f'Invalid JSON: {e}'}
            print(json.dumps(error_response))
            sys.stdout.flush()
        except Exception as e:
            error_response = {'success': False, 'error': str(e)}
            print(json.dumps(error_response))
            sys.stdout.flush()

if __name__ == '__main__':
    main()