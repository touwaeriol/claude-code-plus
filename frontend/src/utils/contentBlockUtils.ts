import type {
  ContentBlock,
  ToolUseContent,
  ToolResultContent,
  TextContent,
  ThinkingContent
} from '@/types/message'

export function isToolUseBlock(block: ContentBlock | any): block is ToolUseContent {
  return block && block.type === 'tool_use' && typeof block.id === 'string'
}

export function isToolResultBlock(block: ContentBlock | any): block is ToolResultContent {
  return block && block.type === 'tool_result' && typeof block.tool_use_id === 'string'
}

export function isTextBlock(block: ContentBlock | any): block is TextContent {
  return block && block.type === 'text' && typeof (block as any).text === 'string'
}

export function isThinkingBlock(block: ContentBlock | any): block is ThinkingContent {
  return block && block.type === 'thinking'
}

export function extractToolUseBlocks(content: ContentBlock[]): ToolUseContent[] {
  return content.filter(isToolUseBlock)
}
