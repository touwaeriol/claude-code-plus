/**
 * Types Module Exports
 * 
 * Re-exports all type definitions
 */

export {
    ToolConstants,
    TOOL_NAME_TO_TYPE,
    isFileOperationTool,
    isCommandExecutionTool,
    isSearchTool,
    isWebTool,
    type ToolType
} from './toolConstants';

export {
    AiModel,
    UiPermissionMode,
    MessageRole,
    MessageStatus,
    defaultUiTokenUsage,
    createSessionObject,
    createEnhancedMessage,
    type UiTokenUsage,
    type SessionObject,
    type ContextReference as LegacyContextReference,
    type EnhancedMessage,
    type LegacyToolCall
} from './uiModels';

export {
    createDefaultSessionState,
    createSessionUpdate,
    type SessionState,
    type SessionUpdate
} from './sessionTypes';

// DisplayItem types
export {
    // Enums
    ToolCallStatus,
    ConnectionStatus,
    ContextType,
    ContextDisplayType,
    SystemMessageLevel,
    
    // Interfaces - Base
    type DisplayItemBase,
    type ToolCallItemBase,
    type RequestStats,
    type ImageBlock,
    type ContextReference,
    
    // Interfaces - Messages
    type UserMessageItem,
    type AssistantTextItem,
    type SystemMessageItem,
    
    // Interfaces - Tool Results
    type ToolResult,
    type ToolResultSuccess,
    type ToolResultError,
    
    // Interfaces - Tool Calls
    type ReadToolCall,
    type WriteToolCall,
    type EditToolCall,
    type MultiEditToolCall,
    type TodoWriteToolCall,
    type BashToolCall,
    type GrepToolCall,
    type GlobToolCall,
    type WebSearchToolCall,
    type WebFetchToolCall,
    type TaskToolCall,
    type NotebookEditToolCall,
    type BashOutputToolCall,
    type KillShellToolCall,
    type ExitPlanModeToolCall,
    type AskUserQuestionToolCall,
    type SkillToolCall,
    type SlashCommandToolCall,
    type ListMcpResourcesToolCall,
    type ReadMcpResourceToolCall,
    type GenericToolCall,
    
    // Interfaces - Helper types
    type EditOperation,
    type TodoItem,
    
    // Union types
    type ToolCallItem,
    type DisplayItem,
    
    // Field getter functions
    getReadToolFields,
    getWriteToolFields,
    getEditToolFields,
    getMultiEditToolFields,
    getTodoWriteToolFields,
    getBashToolFields,
    getGrepToolFields,
    getGlobToolFields,
    getWebSearchToolFields,
    getWebFetchToolFields,
    
    // Factory functions
    createToolCallItem,
    createUserMessageItem,
    createAssistantTextItem,
    createSystemMessageItem,
    createSuccessResult,
    createErrorResult,
    
    // Type guards
    isUserMessageItem,
    isAssistantTextItem,
    isSystemMessageItem,
    isToolCallItem,
    isSuccessResult,
    isErrorResult
} from './displayItem';

// ToolDetails types
export {
    // Interfaces
    type EditToolDetail,
    type MultiEditToolDetail,
    type MultiEditOperation,
    type ReadToolDetail,
    type WriteToolDetail,
    type ToolDetail,
    type ToolCallViewModel,
    
    // Factory functions
    createEditToolDetail,
    createMultiEditToolDetail,
    createReadToolDetail,
    createWriteToolDetail,
    createToolCallViewModel,
    
    // Type guards
    isEditToolDetail,
    isMultiEditToolDetail,
    isReadToolDetail,
    isWriteToolDetail,
    
    // Conversion utilities
    editOperationToMultiEditOperation,
    multiEditOperationToEditOperation
} from './toolDetails';
