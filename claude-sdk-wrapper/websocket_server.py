#!/usr/bin/env python3
"""
WebSocket 服务器 - 更适合实时流式通信
"""

import asyncio
import json
import logging
from aiohttp import web
import aiohttp
import weakref
from typing import Dict, Set
from stream_server import ClaudeService, CLAUDE_SDK_AVAILABLE

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 全局服务实例
claude_service = ClaudeService()

# WebSocket 连接管理
websockets: Set[web.WebSocketResponse] = weakref.WeakSet()


async def websocket_handler(request):
    """处理 WebSocket 连接"""
    ws = web.WebSocketResponse()
    await ws.prepare(request)
    websockets.add(ws)
    
    logger.info("New WebSocket connection established")
    
    # 发送欢迎消息
    await ws.send_json({
        'type': 'welcome',
        'message': 'Connected to Claude WebSocket server',
        'initialized': claude_service.is_initialized
    })
    
    try:
        async for msg in ws:
            if msg.type == aiohttp.WSMsgType.TEXT:
                try:
                    data = json.loads(msg.data)
                    command = data.get('command')
                    
                    if command == 'ping':
                        await ws.send_json({'type': 'pong'})
                        
                    elif command == 'message':
                        # 处理消息
                        message = data.get('message', '')
                        session_id = data.get('session_id')
                        new_session = data.get('new_session', False)
                        options = data.get('options', {})
                        
                        if not message:
                            await ws.send_json({
                                'type': 'error',
                                'error': 'Message is required'
                            })
                            continue
                        
                        # 处理会话
                        if new_session:
                            session_id = claude_service.create_session()
                        elif not session_id:
                            session_id = claude_service.get_or_create_default_session()
                        
                        # 流式发送响应
                        async for chunk in claude_service.stream_message(message, session_id, options):
                            await ws.send_json(chunk)
                        
                        # 发送结束标记
                        await ws.send_json({'type': 'done', 'session_id': session_id})
                        
                    elif command == 'health':
                        await ws.send_json({
                            'type': 'health',
                            'status': 'ok',
                            'initialized': claude_service.is_initialized,
                            'sdk_available': CLAUDE_SDK_AVAILABLE
                        })
                        
                    else:
                        await ws.send_json({
                            'type': 'error',
                            'error': f'Unknown command: {command}'
                        })
                        
                except json.JSONDecodeError:
                    await ws.send_json({
                        'type': 'error',
                        'error': 'Invalid JSON'
                    })
                except Exception as e:
                    logger.error(f"Error handling message: {e}")
                    await ws.send_json({
                        'type': 'error',
                        'error': str(e)
                    })
                    
            elif msg.type == aiohttp.WSMsgType.ERROR:
                logger.error(f'WebSocket error: {ws.exception()}')
                
    except Exception as e:
        logger.error(f"WebSocket handler error: {e}")
    finally:
        logger.info("WebSocket connection closed")
        
    return ws


async def handle_info(request):
    """提供 WebSocket 连接信息"""
    return web.json_response({
        'websocket_url': 'ws://localhost:18081/ws',
        'commands': {
            'ping': 'Check connection',
            'message': 'Send a message to Claude',
            'health': 'Check service health'
        },
        'message_format': {
            'command': 'message',
            'message': 'Your message here',
            'session_id': 'optional',
            'new_session': False,
            'options': {
                'cwd': '/path/to/project'
            }
        }
    })


def create_app():
    """创建应用"""
    app = web.Application()
    
    # 路由
    app.router.add_get('/ws', websocket_handler)
    app.router.add_get('/', handle_info)
    
    # 启动时初始化
    try:
        init_result = claude_service.initialize({})
        if init_result.get('success'):
            logger.info("Service auto-initialized on startup")
        else:
            logger.warning(f"Failed to auto-initialize: {init_result.get('error')}")
    except Exception as e:
        logger.warning(f"Startup initialization error: {e}")
    
    return app


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Claude WebSocket Server')
    parser.add_argument('--host', default='127.0.0.1', help='Host to bind to')
    parser.add_argument('--port', type=int, default=18081, help='Port to bind to')
    args = parser.parse_args()
    
    app = create_app()
    
    logger.info(f"Starting WebSocket server on {args.host}:{args.port}")
    web.run_app(app, host=args.host, port=args.port)


if __name__ == '__main__':
    main()