/**
 * Chrome Extension Detector
 * 
 * Detects Chrome extension installation status.
 * Translated from: claude-agent-sdk/.../util/ChromeExtensionDetector.kt
 * 
 * This mirrors the official CLI's a4A() function logic:
 * 1. Find Chrome profile directories (Default, Profile 1, Profile 2, etc.)
 * 2. Check if the Claude extension ID exists in any profile's Extensions folder
 */

import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

// Claude Chrome extension ID
const CLAUDE_EXTENSION_ID = 'fcoeoabgfenejglbffodgkkbkcdhcgfn';

type OsType = 'darwin' | 'linux' | 'win32' | 'unknown';

/**
 * Get the current operating system type.
 */
function getOsType(): OsType {
  const platform = os.platform();
  switch (platform) {
    case 'darwin':
      return 'darwin';
    case 'linux':
      return 'linux';
    case 'win32':
      return 'win32';
    default:
      return 'unknown';
  }
}

/**
 * Get Chrome configuration base path for the current OS.
 */
function getChromeBasePath(): string | null {
  const homeDir = os.homedir();
  
  switch (getOsType()) {
    case 'darwin':
      return path.join(homeDir, 'Library', 'Application Support', 'Google', 'Chrome');
    case 'linux':
      return path.join(homeDir, '.config', 'google-chrome');
    case 'win32': {
      const appData = process.env.LOCALAPPDATA || path.join(homeDir, 'AppData', 'Local');
      return path.join(appData, 'Google', 'Chrome', 'User Data');
    }
    default:
      return null;
  }
}

/**
 * Check if Claude Chrome extension is installed.
 * 
 * @returns true if extension is found in any Chrome profile
 */
export function isExtensionInstalled(): boolean {
  const chromeBasePath = getChromeBasePath();
  if (!chromeBasePath) {
    return false;
  }
  
  if (!fs.existsSync(chromeBasePath) || !fs.statSync(chromeBasePath).isDirectory()) {
    return false;
  }
  
  // Find Chrome profiles: Default, Profile 1, Profile 2, etc.
  let profiles: string[];
  try {
    const entries = fs.readdirSync(chromeBasePath, { withFileTypes: true });
    profiles = entries
      .filter(entry => 
        entry.isDirectory() && 
        (entry.name === 'Default' || entry.name.startsWith('Profile '))
      )
      .map(entry => entry.name);
  } catch {
    return false;
  }
  
  // Check each profile for the extension
  for (const profile of profiles) {
    const extensionDir = path.join(chromeBasePath, profile, 'Extensions', CLAUDE_EXTENSION_ID);
    try {
      if (fs.existsSync(extensionDir) && fs.statSync(extensionDir).isDirectory()) {
        return true;
      }
    } catch {
      // Continue checking other profiles
    }
  }
  
  return false;
}

/**
 * Get extension installation info.
 */
export interface ExtensionInfo {
  installed: boolean;
  chromeBasePath: string | null;
  profilesChecked: string[];
  foundInProfile?: string;
}

/**
 * Get detailed extension installation info.
 */
export function getExtensionInfo(): ExtensionInfo {
  const chromeBasePath = getChromeBasePath();
  const result: ExtensionInfo = {
    installed: false,
    chromeBasePath,
    profilesChecked: [],
  };
  
  if (!chromeBasePath) {
    return result;
  }
  
  if (!fs.existsSync(chromeBasePath) || !fs.statSync(chromeBasePath).isDirectory()) {
    return result;
  }
  
  let profiles: string[];
  try {
    const entries = fs.readdirSync(chromeBasePath, { withFileTypes: true });
    profiles = entries
      .filter(entry => 
        entry.isDirectory() && 
        (entry.name === 'Default' || entry.name.startsWith('Profile '))
      )
      .map(entry => entry.name);
  } catch {
    return result;
  }
  
  result.profilesChecked = profiles;
  
  for (const profile of profiles) {
    const extensionDir = path.join(chromeBasePath, profile, 'Extensions', CLAUDE_EXTENSION_ID);
    try {
      if (fs.existsSync(extensionDir) && fs.statSync(extensionDir).isDirectory()) {
        result.installed = true;
        result.foundInProfile = profile;
        return result;
      }
    } catch {
      // Continue
    }
  }
  
  return result;
}

/**
 * Claude Chrome Extension ID constant.
 */
export const EXTENSION_ID = CLAUDE_EXTENSION_ID;
