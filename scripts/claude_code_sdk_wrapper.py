#!/usr/bin/env python3
"""
使用官方 claude-code-sdk 的包装脚本
"""
import sys
import json
import logging
from typing import Dict, Any
import anyio

# 配置日志 - 写入stderr以避免干扰输出
logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] %(levelname)s: %(message)s',
    stream=sys.stderr,
    force=True  # 强制重新配置
)
logger = logging.getLogger(__name__)

# 尝试导入 claude-code-sdk
try:
    from claude_code_sdk import query, ClaudeCodeOptions
    CLAUDE_CODE_SDK_AVAILABLE = True
except ImportError as e:
    CLAUDE_CODE_SDK_AVAILABLE = False

class ClaudeCodeProcessor:
    """Claude Code SDK 处理器"""
    
    def __init__(self):
        self.options = None
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
            if not CLAUDE_CODE_SDK_AVAILABLE:
                # Mock 模式
                self.is_initialized = True
                return {
                    'success': True,
                    'message': 'Initialized in mock mode (claude-code-sdk not installed)'
                }
            
            # 创建选项
            self.options = ClaudeCodeOptions(
                system_prompt=config.get('system_prompt', 'You are Claude, a helpful AI assistant.'),
                max_turns=config.get('max_turns', 20)
            )
            
            self.is_initialized = True
            return {
                'success': True,
                'message': 'Initialized with Claude Code SDK'
            }
            
        except Exception as e:
            logger.error(f"Failed to initialize: {e}")
            return {'success': False, 'error': str(e)}
    
    def send_message(self, message: str) -> Dict[str, Any]:
        """发送消息"""
        if not self.is_initialized:
            return {'success': False, 'error': 'Not initialized'}
        
        try:
            if not CLAUDE_CODE_SDK_AVAILABLE:
                # Mock 模式
                return {
                    'success': True,
                    'response': f'[Mock] Response to: {message}\n\nThis is a mock response. To use real Claude, install claude-code-sdk and Claude Code CLI.'
                }
            
            logger.info(f"Processing message: {message}")
            
            # 使用 anyio 运行异步代码
            try:
                response_text = anyio.run(self._query_claude, message)
                logger.info(f"Got response: {response_text[:100] if response_text else 'None'}...")
                
                return {
                    'success': True,
                    'response': response_text or '没有收到响应'
                }
            except Exception as e:
                logger.error(f"Error in anyio.run: {e}")
                raise
            
        except Exception as e:
            logger.error(f"Error sending message: {e}")
            import traceback
            traceback.print_exc()
            return {'success': False, 'error': str(e)}
    
    async def _query_claude(self, prompt: str) -> str:
        """异步查询 Claude"""
        response_parts = []
        
        try:
            async for message in query(prompt=prompt, options=self.options):
                logger.info(f"Received message type: {type(message).__name__}")
                
                # 根据消息类型处理
                message_type = type(message).__name__
                
                if message_type == 'AssistantMessage':
                    # 处理助手消息
                    if hasattr(message, 'content'):
                        for block in message.content:
                            if hasattr(block, 'text'):
                                response_parts.append(block.text)
                            else:
                                block_text = str(block)
                                if block_text and not block_text.startswith('<'):
                                    response_parts.append(block_text)
                    elif hasattr(message, 'text'):
                        response_parts.append(message.text)
                elif message_type in ['SystemMessage', 'ResultMessage']:
                    # 跳过系统消息和结果消息
                    logger.debug(f"Skipping {message_type}")
                else:
                    # 其他消息类型，尝试提取文本
                    if hasattr(message, 'text'):
                        response_parts.append(message.text)
                    elif hasattr(message, 'content'):
                        response_parts.append(str(message.content))
            
            # 只返回有意义的响应文本
            final_response = '\n'.join(response_parts).strip()
            return final_response if final_response else '没有收到响应内容'
            
        except Exception as e:
            logger.error(f"Error in query: {e}")
            raise

def main():
    """主循环"""
    processor = ClaudeCodeProcessor()
    
    # 发送就绪信号 - 这必须是第一个输出
    print(json.dumps({'status': 'ready'}), flush=True)
    
    # 记录 SDK 状态到 stderr
    if CLAUDE_CODE_SDK_AVAILABLE:
        logger.info("Claude Code SDK is available and ready")
    else:
        logger.warning("Claude Code SDK not available, using mock mode")
        logger.warning("Install with: pip install claude-code-sdk")
    
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
            
            # 返回结果 - 确保是单行 JSON
            result_json = json.dumps(result, ensure_ascii=False, separators=(',', ':'))
            logger.info(f"Sending result: {result_json}")
            print(result_json, flush=True)  # 使用 flush=True 确保立即输出
            
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