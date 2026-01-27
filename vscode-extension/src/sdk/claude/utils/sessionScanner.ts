/**
 * Claude Session Scanner
 * 
 * Scans ~/.claude/projects/ directory for JSONL session files
 * and extracts session metadata.
 * 
 * Translated from: claude-agent-sdk/.../utils/ClaudeSessionScanner.kt
 */

import * as fs from 'fs';
import * as path from 'path';
import * as readline from 'readline';
import { getClaudeDir, projectPathToDirectoryName } from './pathUtils';

/**
 * Historical session metadata.
 */
export interface SessionMetadata {
  /** Session ID (used for --resume) */
  sessionId: string;
  /** Preview of first user message */
  firstUserMessage: string;
  /** Last update timestamp (milliseconds) */
  timestamp: number;
  /** Message count */
  messageCount: number;
  /** Project path */
  projectPath: string;
  /** Custom title (set via /rename command) */
  customTitle?: string;
}

/**
 * Scan options.
 */
export interface ScanOptions {
  /** Maximum results to return */
  maxResults?: number;
  /** Number of results to skip */
  offset?: number;
  /** Batch size for scanning */
  batchSize?: number;
}

/**
 * Extract session metadata from a JSONL file.
 */
async function extractSessionMetadata(
  filePath: string,
  projectPath: string
): Promise<SessionMetadata | null> {
  try {
    const stat = fs.statSync(filePath);
    const sessionId = path.basename(filePath, '.jsonl');
    
    let firstUserMessage = '';
    let messageCount = 0;
    let customTitle: string | undefined;
    
    const fileStream = fs.createReadStream(filePath, { encoding: 'utf-8' });
    const rl = readline.createInterface({
      input: fileStream,
      crlfDelay: Infinity,
    });
    
    for await (const line of rl) {
      if (!line.trim()) continue;
      
      try {
        const obj = JSON.parse(line);
        messageCount++;
        
        // Extract first user message
        if (!firstUserMessage && obj.type === 'user') {
          const content = obj.message?.content;
          if (typeof content === 'string') {
            firstUserMessage = content.substring(0, 200);
          } else if (Array.isArray(content)) {
            // Handle content array format
            const textBlock = content.find((b: any) => b.type === 'text');
            if (textBlock?.text) {
              firstUserMessage = textBlock.text.substring(0, 200);
            }
          }
        }
        
        // Check for custom title (from /rename command)
        if (obj.type === 'system' && obj.message?.content) {
          const content = obj.message.content;
          if (typeof content === 'string' && content.includes('renamed to')) {
            const match = content.match(/renamed to ["'](.+?)["']/);
            if (match) {
              customTitle = match[1];
            }
          }
        }
        
        // Only read first 50 messages to find metadata
        if (messageCount >= 50) break;
      } catch {
        // Skip malformed lines
      }
    }
    
    fileStream.destroy();
    
    if (!firstUserMessage) {
      return null;
    }
    
    return {
      sessionId,
      firstUserMessage,
      timestamp: stat.mtimeMs,
      messageCount,
      projectPath,
      customTitle,
    };
  } catch {
    return null;
  }
}

/**
 * Scan project's historical sessions.
 * 
 * @param projectPath Project path
 * @param options Scan options
 * @returns Session metadata list, sorted by timestamp descending
 */
export async function scanHistorySessions(
  projectPath: string,
  options: ScanOptions = {}
): Promise<SessionMetadata[]> {
  const {
    maxResults = 20,
    offset = 0,
    batchSize = 10,
  } = options;
  
  const claudeDir = getClaudeDir();
  const projectId = projectPathToDirectoryName(projectPath);
  const projectDir = path.join(claudeDir, 'projects', projectId);
  
  if (!fs.existsSync(projectDir) || !fs.statSync(projectDir).isDirectory()) {
    return [];
  }
  
  // Get JSONL files sorted by modification time (newest first)
  let jsonlFiles: string[];
  try {
    const entries = fs.readdirSync(projectDir)
      .filter(f => f.endsWith('.jsonl'))
      .map(f => ({
        name: f,
        path: path.join(projectDir, f),
        mtime: fs.statSync(path.join(projectDir, f)).mtimeMs,
      }))
      .sort((a, b) => b.mtime - a.mtime)
      .map(f => f.path);
    
    jsonlFiles = entries;
  } catch {
    return [];
  }
  
  const targetCount = maxResults + offset;
  const collectedMetadata: SessionMetadata[] = [];
  
  // Batch scan, stop early when enough valid sessions are found
  for (let i = 0; i < jsonlFiles.length; i += batchSize) {
    const batch = jsonlFiles.slice(i, i + batchSize);
    
    const batchResults = await Promise.all(
      batch.map(file => extractSessionMetadata(file, projectPath))
    );
    
    const validResults = batchResults.filter((r): r is SessionMetadata => r !== null);
    collectedMetadata.push(...validResults);
    
    // Found enough valid sessions, stop scanning
    if (collectedMetadata.length >= targetCount) {
      break;
    }
  }
  
  // Sort by timestamp descending
  const sorted = collectedMetadata.sort((a, b) => b.timestamp - a.timestamp);
  
  // Apply offset and limit
  return sorted.slice(Math.max(0, offset), offset + maxResults);
}

/**
 * Get all session IDs for a project.
 */
export function getSessionIds(projectPath: string): string[] {
  const claudeDir = getClaudeDir();
  const projectId = projectPathToDirectoryName(projectPath);
  const projectDir = path.join(claudeDir, 'projects', projectId);
  
  if (!fs.existsSync(projectDir) || !fs.statSync(projectDir).isDirectory()) {
    return [];
  }
  
  try {
    return fs.readdirSync(projectDir)
      .filter(f => f.endsWith('.jsonl'))
      .map(f => path.basename(f, '.jsonl'));
  } catch {
    return [];
  }
}

/**
 * Check if a session exists.
 */
export function sessionExists(projectPath: string, sessionId: string): boolean {
  const claudeDir = getClaudeDir();
  const projectId = projectPathToDirectoryName(projectPath);
  const sessionFile = path.join(claudeDir, 'projects', projectId, `${sessionId}.jsonl`);
  
  return fs.existsSync(sessionFile);
}

/**
 * Get session file path.
 */
export function getSessionFilePath(projectPath: string, sessionId: string): string {
  const claudeDir = getClaudeDir();
  const projectId = projectPathToDirectoryName(projectPath);
  return path.join(claudeDir, 'projects', projectId, `${sessionId}.jsonl`);
}

/**
 * Delete a session.
 */
export function deleteSession(projectPath: string, sessionId: string): boolean {
  const filePath = getSessionFilePath(projectPath, sessionId);
  
  try {
    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}
