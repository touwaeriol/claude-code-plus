#!/usr/bin/env python3
"""
Claude SDK 流式 HTTP 服务器
只提供流式接口，简化实现

特性：
- 单一接口：/stream
- 支持完整的 ClaudeCodeOptions 配置
- 默认连续会话，支持 new_session 参数
- Server-Sent Events 格式响应
"""
import json
import logging
import asyncio
from aiohttp import web
from typing import Dict, Any, Optional
import uuid
from datetime import datetime, timedelta

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# 尝试导入 claude-code-sdk
try:
    from claude_code_sdk import query, ClaudeCodeOptions
    CLAUDE_SDK_AVAILABLE = True
    logger.info("Claude Code SDK is available")
except ImportError:
    CLAUDE_SDK_AVAILABLE = False
    logger.warning("Claude Code SDK not available, will use mock mode")


class ClaudeService:
    """Claude 服务处理类"""
    
    def __init__(self):
        self.is_initialized = False
        self.options: Optional[ClaudeCodeOptions] = None
        self.sessions: Dict[str, Dict[str, Any]] = {}  # 存储会话信息
        self.session_timeout = timedelta(hours=2)  # 会话超时时间
        self.default_session_id = None  # 默认会话 ID
        
    def initialize(self, config: Dict[str, Any]) -> Dict[str, Any]:
        """初始化服务"""
        try:
            if CLAUDE_SDK_AVAILABLE:
                # 支持所有 ClaudeCodeOptions 参数
                # 只使用 ClaudeCodeOptions 支持的参数
                options_kwargs = {}
                
                # 支持的参数列表（基于实际的 ClaudeCodeOptions 签名）
                if config.get('system_prompt'):
                    options_kwargs['system_prompt'] = config['system_prompt']
                else:
                    options_kwargs['system_prompt'] = 'You are a helpful assistant.'
                    
                if config.get('max_turns'):
                    options_kwargs['max_turns'] = config['max_turns']
                else:
                    options_kwargs['max_turns'] = 20
                    
                if config.get('allowed_tools'):
                    options_kwargs['allowed_tools'] = config['allowed_tools']
                if config.get('permission_mode'):
                    options_kwargs['permission_mode'] = config['permission_mode']
                if config.get('cwd'):
                    options_kwargs['cwd'] = config['cwd']
                if config.get('max_thinking_tokens'):
                    options_kwargs['max_thinking_tokens'] = config['max_thinking_tokens']
                if config.get('model'):
                    options_kwargs['model'] = config['model']
                
                self.options = ClaudeCodeOptions(**options_kwargs)
                self.is_initialized = True
                logger.info(f"Service initialized with config: {config}")
                return {'success': True, 'message': 'Service initialized with Claude SDK'}
            else:
                self.is_initialized = True
                return {'success': True, 'message': 'Service initialized in mock mode'}
        except Exception as e:
            logger.error(f"Failed to initialize: {e}")
            return {'success': False, 'error': str(e)}
    
    def create_session(self) -> str:
        """创建新会话"""
        session_id = str(uuid.uuid4())
        self.sessions[session_id] = {
            'created_at': datetime.now(),
            'last_activity': datetime.now(),
            'message_count': 0,
            'is_first_message': True
        }
        logger.info(f"Created new session: {session_id}")
        return session_id
    
    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        """获取会话信息"""
        session = self.sessions.get(session_id)
        if session:
            # 检查会话是否超时
            if datetime.now() - session['last_activity'] > self.session_timeout:
                logger.info(f"Session {session_id} expired")
                del self.sessions[session_id]
                if self.default_session_id == session_id:
                    self.default_session_id = None
                return None
            return session
        return None
    
    def get_or_create_default_session(self) -> str:
        """获取或创建默认会话"""
        # 如果有默认会话且未过期，返回它
        if self.default_session_id:
            session = self.get_session(self.default_session_id)
            if session:
                return self.default_session_id
        
        # 创建新的默认会话
        self.default_session_id = self.create_session()
        logger.info(f"Created new default session: {self.default_session_id}")
        return self.default_session_id
    
    def clean_expired_sessions(self):
        """清理过期会话"""
        now = datetime.now()
        expired = []
        for sid, session in self.sessions.items():
            if now - session['last_activity'] > self.session_timeout:
                expired.append(sid)
        
        for sid in expired:
            logger.info(f"Cleaning expired session: {sid}")
            del self.sessions[sid]
            if self.default_session_id == sid:
                self.default_session_id = None
    
    async def stream_message(self, message: str, session_id: str, custom_options: Optional[Dict[str, Any]] = None):
        """发送消息并流式返回响应"""
        # 如果未初始化，尝试自动初始化
        if not self.is_initialized:
            logger.info("Service not initialized, attempting auto-initialization...")
            # 使用自定义选项中的 cwd（如果有）
            init_config = {}
            if custom_options and 'cwd' in custom_options:
                init_config['cwd'] = custom_options['cwd']
            
            init_result = self.initialize(init_config)
            if not init_result.get('success'):
                yield {'type': 'error', 'error': f'Failed to auto-initialize: {init_result.get("error")}'}
                return
            logger.info("Auto-initialization successful")
        
        logger.info(f"Stream processing message (session: {session_id}): {message[:100]}{'...' if len(message) > 100 else ''}")
        
        # 获取会话
        session = self.get_session(session_id)
        if not session:
            yield {'type': 'error', 'error': 'Session expired or not found'}
            return
        
        response_content = []  # 收集响应内容用于日志
        
        try:
            if CLAUDE_SDK_AVAILABLE:
                # 准备选项 - 合并默认选项和自定义选项
                options_dict = {
                    'continue_conversation': not session['is_first_message']
                }
                
                # 从初始化的选项中复制参数
                if self.options:
                    if hasattr(self.options, 'system_prompt'):
                        options_dict['system_prompt'] = self.options.system_prompt
                    if hasattr(self.options, 'max_turns'):
                        options_dict['max_turns'] = self.options.max_turns
                
                # 如果提供了自定义选项，更新选项字典
                if custom_options:
                    # 保护 continue_conversation 不被覆盖（除非明确指定）
                    if 'continue_conversation' not in custom_options:
                        custom_options['continue_conversation'] = not session['is_first_message']
                    options_dict.update(custom_options)
                    logger.info(f"Using custom options: {custom_options}")
                
                options = ClaudeCodeOptions(**options_dict)
                
                # 使用真实的 Claude SDK
                async for msg in query(prompt=message, options=options):
                    # 获取消息类型
                    msg_type = type(msg).__name__
                    
                    # 处理不同类型的消息
                    if hasattr(msg, 'content'):
                        for block in msg.content:
                            if hasattr(block, 'text'):
                                response_content.append(block.text)  # 收集响应内容
                                yield {
                                    'type': 'text',
                                    'message_type': msg_type,
                                    'content': block.text,
                                    'session_id': session_id
                                }
                            elif hasattr(block, 'type'):
                                # 处理其他类型的内容块
                                yield {
                                    'type': block.type,
                                    'message_type': msg_type,
                                    'content': str(block),
                                    'session_id': session_id
                                }
                    else:
                        # 处理没有 content 属性的消息
                        yield {
                            'type': 'message',
                            'message_type': msg_type,
                            'content': str(msg),
                            'session_id': session_id
                        }
                
                # 更新会话状态
                session['is_first_message'] = False
                session['message_count'] += 1
                session['last_activity'] = datetime.now()
                
            else:
                # Mock 模式
                mock_response = f'[Mock] 这是对 "{message}" 的模拟响应。\n\n请安装 claude-code-sdk 以使用真实响应。'
                response_content.append(mock_response)
                yield {
                    'type': 'text',
                    'message_type': 'MockMessage',
                    'content': mock_response,
                    'session_id': session_id
                }
                
                # 更新会话状态
                session['message_count'] += 1
                session['last_activity'] = datetime.now()
                
        except Exception as e:
            logger.error(f"Error sending message: {e}")
            yield {'type': 'error', 'error': str(e)}
        finally:
            # 记录完整的响应
            if response_content:
                full_response = ''.join(response_content)
                logger.info(f"Stream response completed: {full_response[:200]}{'...' if len(full_response) > 200 else ''}")


# 全局服务实例
claude_service = ClaudeService()


# HTTP 路由处理
async def handle_stream(request):
    """处理流式消息请求"""
    try:
        data = await request.json()
        message = data.get('message', '')
        session_id = data.get('session_id')  # 可选的会话 ID
        new_session = data.get('new_session', False)  # 是否强制新会话
        options = data.get('options', {})  # 自定义 ClaudeCodeOptions
        
        if not message:
            return web.json_response({'success': False, 'error': 'Message is required'}, status=400)
        
        # 如果服务未初始化，先自动初始化
        if not claude_service.is_initialized:
            init_config = options.copy() if options else {}
            init_result = claude_service.initialize(init_config)
            if not init_result.get('success'):
                return web.json_response(init_result, status=500)
        
        # 处理会话逻辑
        if new_session:
            # 强制创建新会话
            session_id = claude_service.create_session()
            logger.info(f"[/stream] Creating new session as requested: {session_id}")
        elif not session_id:
            # 没有指定会话ID，使用默认会话
            session_id = claude_service.get_or_create_default_session()
            logger.info(f"[/stream] Using default session: {session_id}")
        
        logger.info(f"[/stream] Request (session: {session_id}, new_session: {new_session}): {message[:100]}{'...' if len(message) > 100 else ''}")
        
        # 创建流式响应
        response = web.StreamResponse()
        response.headers['Content-Type'] = 'text/event-stream'
        response.headers['Cache-Control'] = 'no-cache'
        response.headers['Connection'] = 'keep-alive'
        await response.prepare(request)
        
        # 发送消息并流式返回
        chunk_count = 0
        async for chunk in claude_service.stream_message(message, session_id, options):
            # 使用 Server-Sent Events 格式
            data = json.dumps(chunk)
            await response.write(f"data: {data}\n\n".encode('utf-8'))
            chunk_count += 1
        
        logger.info(f"[/stream] Completed with {chunk_count} chunks")
        
        # 发送结束信号
        await response.write(b"data: [DONE]\n\n")
        await response.write_eof()
        
        # 记录完整响应信息
        logger.info(f"[/stream] Response sent: {chunk_count} chunks, session_id: {session_id}")
        
        return response
        
    except Exception as e:
        logger.error(f"Error in stream: {e}")
        return web.json_response({'success': False, 'error': str(e)}, status=400)


async def handle_health(request):
    """健康检查"""
    # 清理过期会话
    claude_service.clean_expired_sessions()
    
    response = {
        'status': 'ok',
        'initialized': claude_service.is_initialized,
        'sdk_available': CLAUDE_SDK_AVAILABLE,
        'active_sessions': len(claude_service.sessions)
    }
    
    # 记录响应
    logger.info(f"[/health] Response: {json.dumps(response)}")
    
    return web.json_response(response)


async def handle_options(request):
    """处理 CORS preflight 请求"""
    return web.Response(headers={
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
    })


# 中间件：添加 CORS 头
@web.middleware
async def cors_middleware(request, handler):
    response = await handler(request)
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS'
    response.headers['Access-Control-Allow-Headers'] = 'Content-Type'
    return response


def create_app():
    """创建应用"""
    app = web.Application(middlewares=[cors_middleware])
    
    # 添加路由 - 只有流式接口和健康检查
    app.router.add_post('/stream', handle_stream)  # 主要接口
    app.router.add_get('/health', handle_health)
    app.router.add_options('/{path:.*}', handle_options)
    
    # 启动时尝试自动初始化服务（可选）
    # 如果失败也不影响服务器启动，会在第一次请求时再次尝试
    try:
        init_result = claude_service.initialize({})
        if init_result.get('success'):
            logger.info("Service auto-initialized on startup")
        else:
            logger.warning(f"Failed to auto-initialize service on startup: {init_result.get('error')}")
            logger.info("Service will be initialized on first request")
    except Exception as e:
        logger.warning(f"Exception during startup initialization: {e}")
        logger.info("Service will be initialized on first request")
    
    return app


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Claude SDK Stream Server')
    parser.add_argument('--host', default='127.0.0.1', help='Host to bind to')
    parser.add_argument('--port', type=int, default=18080, help='Port to bind to')
    args = parser.parse_args()
    
    app = create_app()
    
    logger.info(f"Starting stream server on {args.host}:{args.port}")
    logger.info(f"Claude SDK available: {CLAUDE_SDK_AVAILABLE}")
    logger.info("API: POST /stream - Stream messages with Server-Sent Events")
    
    web.run_app(app, host=args.host, port=args.port)


if __name__ == '__main__':
    main()