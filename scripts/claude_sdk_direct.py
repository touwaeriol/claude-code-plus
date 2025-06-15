#!/usr/bin/env python3
"""
直接调用 Claude Code SDK 的包装脚本
"""
import sys
import json
import asyncio
import logging
from typing import Dict, Any

# 配置日志 - 写入stderr以避免干扰输出
logging.basicConfig(level=logging.INFO, format='%(message)s', stream=sys.stderr)
logger = logging.getLogger(__name__)

# 尝试导入 claude-code-sdk
try:
    from claude_code_sdk import ClaudeCode
    CLAUDE_SDK_AVAILABLE = True
    logger.info("Claude Code SDK is available")
except ImportError as e:
    logger.warning(f"Failed to import claude_code_sdk: {e}")
    logger.warning("Please install: pip install claude-code-sdk")
    CLAUDE_SDK_AVAILABLE = False

class ClaudeSDKProcessor:
    """Claude SDK 处理器"""
    
    def __init__(self):
        self.claude_client = None
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
            if not CLAUDE_SDK_AVAILABLE:
                # Mock 模式
                self.is_initialized = True
                return {
                    'success': True,
                    'message': 'Initialized in mock mode (claudecode not installed)'
                }
            
            # 创建 Claude 选项
            self.options = ClaudeCodeOptions(
                max_turns=config.get('max_turns', 20),
                permission_mode=PermissionMode.ask,
                system_prompt=config.get('system_prompt', 'You are Claude, a helpful AI assistant.')
            )
            
            self.is_initialized = True
            return {
                'success': True,
                'message': 'Initialized with Claude SDK'
            }
            
        except Exception as e:
            logger.error(f"Failed to initialize: {e}")
            return {'success': False, 'error': str(e)}
    
    def send_message(self, message: str) -> Dict[str, Any]:
        """发送消息"""
        if not self.is_initialized:
            return {'success': False, 'error': 'Not initialized'}
        
        try:
            if not CLAUDE_SDK_AVAILABLE:
                # Mock 模式
                return {
                    'success': True,
                    'response': f'[Mock] Response to: {message}'
                }
            
            # 使用异步运行查询
            response_text = asyncio.run(self._query_claude(message))
            
            return {
                'success': True,
                'response': response_text
            }
            
        except Exception as e:
            logger.error(f"Error sending message: {e}")
            return {'success': False, 'error': str(e)}
    
    async def _query_claude(self, prompt: str) -> str:
        """异步查询 Claude"""
        response_parts = []
        
        try:
            async for message in query(prompt=prompt, options=self.options):
                # 处理不同类型的消息
                if hasattr(message, 'content'):
                    for block in message.content:
                        if hasattr(block, 'text'):
                            response_parts.append(block.text)
                elif hasattr(message, 'text'):
                    response_parts.append(message.text)
                else:
                    # 尝试将消息转换为字符串
                    response_parts.append(str(message))
            
            return ' '.join(response_parts) if response_parts else 'No response'
            
        except Exception as e:
            logger.error(f"Error in query: {e}")
            raise

def main():
    """主循环"""
    processor = ClaudeSDKProcessor()
    
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