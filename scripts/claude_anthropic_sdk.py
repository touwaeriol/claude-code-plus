#!/usr/bin/env python3
"""
使用 Anthropic 官方 SDK 调用 Claude
"""
import sys
import json
import os
import logging
from typing import Dict, Any

# 配置日志 - 写入stderr以避免干扰输出
logging.basicConfig(level=logging.INFO, format='%(message)s', stream=sys.stderr)
logger = logging.getLogger(__name__)

# 尝试导入 anthropic SDK
try:
    from anthropic import Anthropic, HUMAN_PROMPT, AI_PROMPT
    ANTHROPIC_SDK_AVAILABLE = True
    logger.info("Anthropic SDK is available")
except ImportError as e:
    logger.warning(f"Failed to import anthropic: {e}")
    logger.warning("Please install: pip install anthropic")
    ANTHROPIC_SDK_AVAILABLE = False

class ClaudeProcessor:
    """Claude 处理器"""
    
    def __init__(self):
        self.client = None
        self.is_initialized = False
        self.model = "claude-3-sonnet-20240229"
        
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
            if not ANTHROPIC_SDK_AVAILABLE:
                # Mock 模式
                self.is_initialized = True
                return {
                    'success': True,
                    'message': 'Initialized in mock mode (anthropic SDK not installed)'
                }
            
            # 获取 API key
            api_key = config.get('api_key') or os.getenv('CLAUDE_API_KEY') or os.getenv('ANTHROPIC_API_KEY')
            if not api_key:
                return {
                    'success': False,
                    'error': 'No API key provided. Set CLAUDE_API_KEY or ANTHROPIC_API_KEY environment variable.'
                }
            
            # 创建客户端
            self.client = Anthropic(api_key=api_key)
            self.model = config.get('model', self.model)
            
            self.is_initialized = True
            return {
                'success': True,
                'message': f'Initialized with Anthropic SDK (model: {self.model})'
            }
            
        except Exception as e:
            logger.error(f"Failed to initialize: {e}")
            return {'success': False, 'error': str(e)}
    
    def send_message(self, message: str) -> Dict[str, Any]:
        """发送消息"""
        if not self.is_initialized:
            return {'success': False, 'error': 'Not initialized'}
        
        try:
            if not ANTHROPIC_SDK_AVAILABLE or not self.client:
                # Mock 模式
                return {
                    'success': True,
                    'response': f'[Mock] I received your message: "{message}"\n\nIn a real scenario, I would provide a helpful response using the Claude API.'
                }
            
            # 调用 Claude API
            response = self.client.messages.create(
                model=self.model,
                max_tokens=1024,
                messages=[
                    {"role": "user", "content": message}
                ]
            )
            
            # 提取响应文本
            response_text = response.content[0].text if response.content else "No response"
            
            return {
                'success': True,
                'response': response_text
            }
            
        except Exception as e:
            logger.error(f"Error sending message: {e}")
            return {'success': False, 'error': str(e)}

def main():
    """主循环"""
    processor = ClaudeProcessor()
    
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
            result = processor.handle_command(command_data)
            
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