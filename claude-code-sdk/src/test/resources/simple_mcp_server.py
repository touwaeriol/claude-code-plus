#!/usr/bin/env python3
"""
简单的 MCP 服务器 - 用于测试
提供基本的文件操作和数学计算工具
"""

import json
import sys
import os
from datetime import datetime
from typing import Dict, Any, List

class SimpleMCPServer:
    def __init__(self):
        self.tools = {
            "read_file": {
                "name": "read_file",
                "description": "读取指定路径的文件内容",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "要读取的文件路径"
                        }
                    },
                    "required": ["path"]
                }
            },
            "write_file": {
                "name": "write_file", 
                "description": "向指定路径写入文件内容",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "path": {
                            "type": "string",
                            "description": "要写入的文件路径"
                        },
                        "content": {
                            "type": "string",
                            "description": "要写入的内容"
                        }
                    },
                    "required": ["path", "content"]
                }
            },
            "calculate": {
                "name": "calculate",
                "description": "执行简单的数学计算",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "expression": {
                            "type": "string",
                            "description": "数学表达式，如 '2 + 3' 或 '10 * 5'"
                        }
                    },
                    "required": ["expression"]
                }
            },
            "get_time": {
                "name": "get_time",
                "description": "获取当前时间",
                "inputSchema": {
                    "type": "object",
                    "properties": {}
                }
            }
        }
        
    def handle_initialize(self, request: Dict[str, Any]) -> Dict[str, Any]:
        """处理初始化请求"""
        return {
            "jsonrpc": "2.0",
            "id": request.get("id"),
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {}
                },
                "serverInfo": {
                    "name": "simple-test-mcp-server",
                    "version": "1.0.0"
                }
            }
        }
    
    def handle_tools_list(self, request: Dict[str, Any]) -> Dict[str, Any]:
        """处理工具列表请求"""
        return {
            "jsonrpc": "2.0",
            "id": request.get("id"),
            "result": {
                "tools": list(self.tools.values())
            }
        }
    
    def handle_tools_call(self, request: Dict[str, Any]) -> Dict[str, Any]:
        """处理工具调用请求"""
        params = request.get("params", {})
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        
        try:
            if tool_name == "read_file":
                result = self._read_file(arguments)
            elif tool_name == "write_file":
                result = self._write_file(arguments)
            elif tool_name == "calculate":
                result = self._calculate(arguments)
            elif tool_name == "get_time":
                result = self._get_time(arguments)
            else:
                raise ValueError(f"Unknown tool: {tool_name}")
            
            return {
                "jsonrpc": "2.0",
                "id": request.get("id"),
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": result
                        }
                    ]
                }
            }
        except Exception as e:
            return {
                "jsonrpc": "2.0",
                "id": request.get("id"),
                "error": {
                    "code": -32603,
                    "message": str(e)
                }
            }
    
    def _read_file(self, arguments: Dict[str, Any]) -> str:
        """读取文件"""
        path = arguments.get("path", "")
        if not path:
            raise ValueError("文件路径不能为空")
        
        # 安全检查：只允许读取 /tmp 目录下的文件
        if not path.startswith("/tmp/"):
            raise ValueError("为安全起见，只能读取 /tmp/ 目录下的文件")
        
        try:
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            return f"文件 {path} 的内容:\n{content}"
        except FileNotFoundError:
            return f"文件 {path} 不存在"
        except Exception as e:
            raise ValueError(f"读取文件失败: {str(e)}")
    
    def _write_file(self, arguments: Dict[str, Any]) -> str:
        """写入文件"""
        path = arguments.get("path", "")
        content = arguments.get("content", "")
        
        if not path:
            raise ValueError("文件路径不能为空")
        
        # 安全检查：只允许写入 /tmp 目录下的文件
        if not path.startswith("/tmp/"):
            raise ValueError("为安全起见，只能写入 /tmp/ 目录下的文件")
        
        try:
            # 确保目录存在
            os.makedirs(os.path.dirname(path), exist_ok=True)
            
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            return f"成功写入文件 {path}，内容长度: {len(content)} 字符"
        except Exception as e:
            raise ValueError(f"写入文件失败: {str(e)}")
    
    def _calculate(self, arguments: Dict[str, Any]) -> str:
        """执行计算"""
        expression = arguments.get("expression", "")
        if not expression:
            raise ValueError("表达式不能为空")
        
        # 安全检查：只允许基本的数学运算
        allowed_chars = set("0123456789+-*/()., ")
        if not all(c in allowed_chars for c in expression):
            raise ValueError("表达式只能包含数字、运算符和括号")
        
        try:
            # 使用 eval 计算（在生产环境中应该使用更安全的方法）
            result = eval(expression)
            return f"计算结果: {expression} = {result}"
        except Exception as e:
            raise ValueError(f"计算失败: {str(e)}")
    
    def _get_time(self, arguments: Dict[str, Any]) -> str:
        """获取当前时间"""
        now = datetime.now()
        return f"当前时间: {now.strftime('%Y-%m-%d %H:%M:%S')}"
    
    def run(self):
        """运行服务器"""
        print("Simple MCP Server 启动...", file=sys.stderr)
        
        while True:
            try:
                line = sys.stdin.readline()
                if not line:
                    break
                
                line = line.strip()
                if not line:
                    continue
                
                print(f"收到请求: {line}", file=sys.stderr)
                
                try:
                    request = json.loads(line)
                except json.JSONDecodeError as e:
                    print(f"JSON 解析错误: {e}", file=sys.stderr)
                    continue
                
                method = request.get("method")
                
                if method == "initialize":
                    response = self.handle_initialize(request)
                elif method == "tools/list":
                    response = self.handle_tools_list(request)
                elif method == "tools/call":
                    response = self.handle_tools_call(request)
                else:
                    response = {
                        "jsonrpc": "2.0",
                        "id": request.get("id"),
                        "error": {
                            "code": -32601,
                            "message": f"Method not found: {method}"
                        }
                    }
                
                response_json = json.dumps(response)
                print(response_json)
                sys.stdout.flush()
                print(f"发送响应: {response_json}", file=sys.stderr)
                
            except KeyboardInterrupt:
                print("服务器停止", file=sys.stderr)
                break
            except Exception as e:
                print(f"处理请求时出错: {e}", file=sys.stderr)
                continue

if __name__ == "__main__":
    server = SimpleMCPServer()
    server.run()