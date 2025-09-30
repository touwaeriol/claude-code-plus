"""验证 Claude Code Python SDK 在特定提示下的缓存控制错误。

运行方式：
    python3 python_tests/test_cache_control_error.py

脚本会调用官方 Python 版 Claude Code SDK，发送 “演示edit工具” 提示，
并打印/断言返回结果是否包含缓存控制限制错误信息。
"""

from __future__ import annotations

import asyncio
from typing import Any, Dict, List

import claude_code_sdk

PROMPT = "演示edit工具"
ERROR_SUBSTRING = "A maximum of 4 blocks with cache_control may be provided. Found 5."


async def _collect_parsed_messages() -> List[claude_code_sdk.Message]:
    """优先尝试高层 API，若失败再抛异常给调用方。"""

    options = claude_code_sdk.ClaudeCodeOptions(
        model=None,
        allowed_tools=[],
    )

    messages: List[claude_code_sdk.Message] = []

    async for message in claude_code_sdk.query(prompt=PROMPT, options=options):
        messages.append(message)
        if isinstance(message, claude_code_sdk.ResultMessage):
            break

    return messages


async def _collect_raw_messages() -> List[Dict[str, Any]]:
    """退回到官方 SDK 的 Subprocess 传输层，收集原始 JSON。"""

    from claude_code_sdk._internal.transport.subprocess_cli import (
        SubprocessCLITransport,
    )

    options = claude_code_sdk.ClaudeCodeOptions(
        model=None,
        allowed_tools=[],
    )

    transport = SubprocessCLITransport(prompt=PROMPT, options=options)
    messages: List[Dict[str, Any]] = []

    await transport.connect()
    try:
        async for payload in transport.receive_messages():
            messages.append(payload)
    finally:
        try:
            await transport.disconnect()
        except BaseException as exc:  # 忽略任意退出异常，避免阻断分析
            print("断开传输时出现异常：", repr(exc))

    return messages


def main() -> None:
    """执行验证流程并给出结论。"""

    try:
        parsed_messages = asyncio.run(_collect_parsed_messages())
    except BaseException as exc:  # noqa: BLE001 - 需要捕获任何 SDK 解析异常
        print("官方 Python SDK 解析失败：", repr(exc))
        parsed_messages = []

    if parsed_messages:
        result_messages = [m for m in parsed_messages if isinstance(m, claude_code_sdk.ResultMessage)]
        if not result_messages:
            raise SystemExit("未收到 ResultMessage，无法判断错误状态。")

        result = result_messages[-1]

        print("=== Claude Code Python SDK 响应摘要（解析后的数据） ===")
        for msg in parsed_messages:
            print(f"- {msg}")

        if not result.is_error:
            raise SystemExit("ResultMessage 未标记为错误，未复现问题。")

        if not result.result or ERROR_SUBSTRING not in result.result:
            raise SystemExit(
                "ResultMessage 中未包含缓存控制限制错误信息，未复现问题。"
            )

        print("检测到缓存控制数量限制错误：", ERROR_SUBSTRING)
        return

    # 如果高层解析失败，则使用原始 JSON 数据进行判断
    raw_messages = asyncio.run(_collect_raw_messages())
    print("=== Claude Code Python SDK 传输层原始消息 ===")
    for payload in raw_messages:
        print(payload)

    result_payloads = [p for p in raw_messages if p.get("type") == "result"]
    if not result_payloads:
        raise SystemExit("未捕获 result 类型消息，无法判断错误。")

    result_payload = result_payloads[-1]
    if not result_payload.get("is_error"):
        raise SystemExit("result 消息未标记为错误，未复现问题。")

    result_text = result_payload.get("result", "") or ""
    if ERROR_SUBSTRING not in result_text:
        raise SystemExit("result 消息中未包含缓存控制错误提示。")

    print("检测到缓存控制数量限制错误：", ERROR_SUBSTRING)


if __name__ == "__main__":
    main()
