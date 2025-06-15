#!/usr/bin/env python3
"""
简化的 Claude 包装脚本，用于测试
"""
import sys
import json
import logging

# 配置日志到 stderr
logging.basicConfig(
    level=logging.INFO,
    format='%(message)s',
    stream=sys.stderr
)
logger = logging.getLogger(__name__)

class SimpleMockProcessor:
    """简单的 Mock 处理器"""
    
    def __init__(self):
        self.is_initialized = False
        
    def handle_command(self, command_data):
        """处理命令"""
        command = command_data.get('command')
        
        if command == 'initialize':
            self.is_initialized = True
            return {
                'success': True,
                'message': 'Initialized in mock mode'
            }
        elif command == 'send_message':
            if not self.is_initialized:
                return {'success': False, 'error': 'Not initialized'}
            
            message = command_data.get('message', '')
            # 简单的 Mock 响应
            if '1+1' in message:
                response = '1+1 等于 2'
            elif '2+2' in message:
                response = '2+2 等于 4'
            else:
                response = f'这是对 "{message}" 的模拟响应'
                
            return {
                'success': True,
                'response': response
            }
        elif command == 'exit':
            return {'success': True, 'message': 'Exiting'}
        else:
            return {'success': False, 'error': f'Unknown command: {command}'}

def main():
    """主循环"""
    processor = SimpleMockProcessor()
    
    # 立即发送就绪信号
    ready_signal = json.dumps({'status': 'ready'})
    print(ready_signal, flush=True)
    logger.info(f"Sent ready signal: {ready_signal}")
    
    # 主循环
    while True:
        try:
            # 读取命令
            line = sys.stdin.readline()
            if not line:
                logger.info("EOF received, exiting")
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
                logger.info("Exit command received")
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