/**
 * VCS Integration Module
 * 
 * Provides Git/VCS integration features including AI-powered commit message generation.
 * 
 * Components:
 * - CommitPanelAccessor: Access to VS Code SCM panel state
 * - ScmInputHandler: SCM input box event handling (replaces IDEA CheckinHandlerFactory)
 * - GenerateCommitMessageService: AI-powered commit message generation
 * - GenerateCommitMessageCommand: VS Code command for the feature
 */

// Commit Panel Accessor (CommitPanelAccessor.kt equivalent)
export {
    CommitPanelAccessor,
    getCommitPanelAccessor,
    type ChangeInfo,
    type ChangeStatus
} from './commitPanelAccessor';

// SCM Input Handler (ClaudeCheckinHandlerFactory.kt equivalent)
export {
    ScmInputHandler,
    getScmInputHandler,
    initializeScmInputHandler,
    type CommitState,
    type CommitLifecycleListener
} from './scmInputHandler';

// Generate Commit Message Service
export {
    GenerateCommitMessageService,
    executeGenerateCommitMessage,
    type GenerateCommitMessageOptions,
    type GenerateCommitMessageResult
} from './generateCommitMessageService';

// Generate Commit Message Command (GenerateCommitMessageAction.kt equivalent)
export {
    GENERATE_COMMIT_MESSAGE_COMMAND,
    executeGenerateCommitMessageCommand,
    registerGenerateCommitMessageCommand,
    canExecuteGenerateCommitMessage,
    createScmTitleAction,
    GenerateCommitMessageCommandHandler,
    getGenerateCommitMessageHandler,
    type GenerateBackend,
    type GenerateCommitMessageConfig
} from './generateCommitMessageCommand';
