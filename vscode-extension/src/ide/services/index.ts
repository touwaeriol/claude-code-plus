/**
 * Services Module Exports
 */

export {
    GitBranchService,
    VscodeGitBranchService,
    NoopGitBranchService,
    gitBranchService
} from './gitBranchService';

export {
    ClaudeSettingsService,
    claudeSettingsService,
    type ClaudeSettings
} from './claudeSettingsService';

export {
    NotificationService,
    notificationService
} from './notificationService';

export {
    FileHistoryService
} from './fileHistoryService';

export {
    VscodePlatformService
} from './vscodePlatformService';

// File Index Service
export {
    FileIndexService,
    IndexedFileInfo,
    IndexedSymbolInfo,
    IndexStats,
    SymbolType,
    IndexingInProgressError,
} from './fileIndexService';

export {
    SimpleFileIndexService,
    getFileIndexService,
} from './simpleFileIndexService';

// Background Service
export {
    ClaudeCodePlusBackgroundService,
    getBackgroundService,
    claudeCodePlusBackgroundService,
    type SessionState,
    type SessionUpdate,
    type ServiceStats,
} from './claudeCodePlusBackgroundService';

// Project Session State Service
export {
    ProjectSessionStateService,
    getProjectSessionStateService,
    projectSessionStateService,
} from './projectSessionStateService';

// Language Analysis Service
export {
    type LanguageAnalysisService,
    type SymbolInfo,
    type SearchScope,
    DEFAULT_SEARCH_SCOPE,
    getLanguageAnalysisService,
    setLanguageAnalysisService,
    LanguageAnalysisServiceFactory,
} from './languageAnalysisService';

export { VSCodeLanguageAnalysisService } from './vscodeLanguageAnalysisService';
export { NoopLanguageAnalysisService } from './noopLanguageAnalysisService';
