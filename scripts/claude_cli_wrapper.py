#!/usr/bin/env python3
"""
使用 Claude CLI 的包装脚本
"""
import sys
import json
import logging
import subprocess
import os

# 配置日志到 stderr
logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] %(levelname)s: %(message)s',
    stream=sys.stderr
)
logger = logging.getLogger(__name__)

class ClaudeCLIProcessor:
    """Claude CLI 处理器"""
    
    def __init__(self):
        self.is_initialized = False
        
    def handle_command(self, command_data):
        """处理命令"""
        command = command_data.get('command')
        
        if command == 'initialize':
            self.is_initialized = True
            return {
                'success': True,
                'message': 'Initialized with Claude CLI'
            }
        elif command == 'send_message':
            if not self.is_initialized:
                return {'success': False, 'error': 'Not initialized'}
            
            message = command_data.get('message', '')
            return self.query_claude(message)
        elif command == 'exit':
            return {'success': True, 'message': 'Exiting'}
        else:
            return {'success': False, 'error': f'Unknown command: {command}'}
    
    def query_claude(self, message):
        """使用 Claude CLI 查询"""
        try:
            logger.info(f"Querying Claude CLI with: {message}")
            
            # 使用 echo 管道方式调用 claude
            cmd = f'echo "{message}" | claude'
            
            result = subprocess.run(
                cmd,
                shell=True,
                capture_output=True,
                text=True,
                timeout=20,
                env={**os.environ, 'CLAUDE_NO_COLOR': '1'}  # 禁用颜色输出
            )
            
            if result.returncode == 0:
                response = result.stdout.strip()
                logger.info(f"Got response: {response[:100]}...")
                return {
                    'success': True,
                    'response': response
                }
            else:
                error = result.stderr.strip() or "Unknown error"
                logger.error(f"Claude CLI error: {error}")
                return {
                    'success': False,
                    'error': f'Claude CLI error: {error}'
                }
                
        except subprocess.TimeoutExpired:
            logger.error("Claude CLI timeout")
            return {'success': False, 'error': 'Claude CLI timeout'}
        except Exception as e:
            logger.error(f"Error calling Claude CLI: {e}")
            return {'success': False, 'error': str(e)}

def main():
    """主循环"""
    processor = ClaudeCLIProcessor()
    
    # 发送就绪信号
    print(json.dumps({'status': 'ready'}), flush=True)
    logger.info("Claude CLI wrapper ready")
    
    # 主循环
    while True:
        try:
            # 读取命令
            line = sys.stdin.readline()
            if not line:
                break
            
            line = line.strip()
            if not line:
                continue
                
            logger.info(f"Received command: {line}")
            
            # 解析命令
            command_data = json.loads(line)
            
            # 处理命令
            result = processor.handle_command(command_data)
            
            # 返回结果
            result_json = json.dumps(result, ensure_ascii=False)
            logger.info(f"Sending result: {result_json}")
            print(result_json, flush=True)
            
            # 检查退出
            if command_data.get('command') == 'exit':
                break
                
        except json.JSONDecodeError as e:
            error_response = {'success': False, 'error': f'Invalid JSON: {e}'}
            print(json.dumps(error_response), flush=True)
        except Exception as e:
            error_response = {'success': False, 'error': str(e)}
            print(json.dumps(error_response), flush=True)
            logger.error(f"Error: {e}")

if __name__ == '__main__':
    main()