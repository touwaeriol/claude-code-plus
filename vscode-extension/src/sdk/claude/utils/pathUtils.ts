/**
 * Project Path Utilities
 * 
 * Provides conversion between project paths and Claude CLI directory names.
 * Translated from: claude-agent-sdk/.../utils/ProjectPathUtils.kt
 */

import * as path from 'path';
import * as crypto from 'crypto';

/**
 * Convert project path to Claude CLI directory name.
 * 
 * Claude CLI naming rules:
 * 1. Windows drive colon removed, add - after drive letter
 * 2. Replace path separators with -
 * 3. Replace dots (.) with -
 * 4. Replace underscores (_) with -
 * 5. Preserve leading - (Unix paths)
 * 
 * Examples:
 * - /home/username/codes/claude-code-plus → -home-username-codes-claude-code-plus
 * - /Users/username/.claude-code-router → -Users-username--claude-code-router
 * - C:\Users\user\project → C--Users-user-project
 * 
 * @param projectPath Absolute path of the project
 * @returns Directory name used by Claude CLI
 */
export function projectPathToDirectoryName(projectPath: string): string {
  // Normalize path (handle different OS path separators)
  let normalizedPath = path.normalize(projectPath);
  
  let dirName = normalizedPath;
  
  // Handle Windows drive letter (e.g., "C:" → "C-")
  if (dirName.length >= 2 && dirName[1] === ':') {
    dirName = dirName[0] + '-' + dirName.substring(2);
  }
  
  // Replace path separators, dots, and underscores
  dirName = dirName
    .replace(/\\/g, '-')  // Windows path separator
    .replace(/\//g, '-')  // Unix path separator
    .replace(/\./g, '-')  // Dots
    .replace(/_/g, '-');  // Underscores
  
  // Claude CLI preserves leading -, only removes trailing -
  return dirName.replace(/-+$/, '');
}

/**
 * Get project's short name (for display).
 * 
 * @param projectPath Project path
 * @returns Project name (last directory component)
 */
export function getProjectName(projectPath: string): string {
  return path.basename(projectPath) || 'Unknown';
}

/**
 * Generate project's unique identifier.
 * Used for scenarios requiring shorter identifiers.
 * 
 * @param projectPath Project path
 * @returns 8-character unique identifier
 */
export function generateProjectId(projectPath: string): string {
  const hash = crypto.createHash('md5').update(projectPath).digest('hex');
  return hash.substring(0, 8);
}

/**
 * Validate if project path is valid.
 */
export function isValidProjectPath(projectPath: string): boolean {
  try {
    return path.isAbsolute(projectPath);
  } catch {
    return false;
  }
}

/**
 * Get Claude configuration directory.
 */
export function getClaudeDir(): string {
  const homeDir = process.env.HOME || process.env.USERPROFILE || '.';
  return path.join(homeDir, '.claude');
}

/**
 * Get project's session directory in Claude config.
 */
export function getProjectSessionDir(projectPath: string): string {
  const claudeDir = getClaudeDir();
  const projectId = projectPathToDirectoryName(projectPath);
  return path.join(claudeDir, 'projects', projectId);
}
