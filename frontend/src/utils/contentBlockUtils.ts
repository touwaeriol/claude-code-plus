import type {
  ContentBlock,
  ToolUseContent,
  ToolResultContent,
  TextContent,
  ThinkingContent
} from '@/types/message'

export function isToolUseBlock(block: ContentBlock | unknown): block is ToolUseContent {
  return !!block && (block as ContentBlock).type === 'tool_use' && typeof (block as ToolUseContent).id === 'string'
}

export function isToolResultBlock(block: ContentBlock | unknown): block is ToolResultContent {
  return !!block && (block as ContentBlock).type === 'tool_result' && typeof (block as ToolResultContent).tool_use_id === 'string'
}

export function isTextBlock(block: ContentBlock | unknown): block is TextContent {
  return !!block && (block as ContentBlock).type === 'text' && typeof (block as TextContent).text === 'string'
}

export function isThinkingBlock(block: ContentBlock | unknown): block is ThinkingContent {
  return !!block && (block as ContentBlock).type === 'thinking'
}

export function extractToolUseBlocks(content: ContentBlock[]): ToolUseContent[] {
  return content.filter(isToolUseBlock)
}
