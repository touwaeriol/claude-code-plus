/**
 * SDK Utils Module
 * 
 * Exports utility functions for path handling, session scanning, and Chrome detection.
 */

// Path utilities
export {
  projectPathToDirectoryName,
  getProjectName,
  generateProjectId,
  isValidProjectPath,
  getClaudeDir,
  getProjectSessionDir,
} from './pathUtils';

// Session scanner
export {
  type SessionMetadata,
  type ScanOptions,
  scanHistorySessions,
  getSessionIds,
  sessionExists,
  getSessionFilePath,
  deleteSession,
} from './sessionScanner';

// Chrome extension detector
export {
  isExtensionInstalled,
  getExtensionInfo,
  type ExtensionInfo,
  EXTENSION_ID,
} from './chromeDetector';
