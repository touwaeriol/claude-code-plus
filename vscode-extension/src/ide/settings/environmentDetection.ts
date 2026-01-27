/**
 * Environment Detection
 * 
 * Utility functions for detecting Node.js and Codex installation paths and versions.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/EnvironmentDetection.kt
 */

import * as os from 'os';
import * as path from 'path';
import * as fs from 'fs';
import { execSync } from 'child_process';

/**
 * Node.js detection result
 */
export interface NodeInfo {
    path: string;
    version?: string;
}

/**
 * Codex detection result
 */
export interface CodexInfo {
    path: string;
    version?: string;
}

/**
 * Environment detection utilities
 */
export const EnvironmentDetection = {
    /**
     * Detect Node.js path and version
     * @returns NodeInfo containing path and version, or undefined if not found
     */
    detectNodeInfo(): NodeInfo | undefined {
        const nodePath = this.detectNodePath();
        if (!nodePath) return undefined;

        const version = this.detectNodeVersion(nodePath);
        return { path: nodePath, version: version || undefined };
    },

    /**
     * Detect Codex path and version
     * @returns CodexInfo containing path and version, or undefined if not found
     */
    detectCodexInfo(): CodexInfo | undefined {
        const codexPath = this.detectCodexPath();
        if (!codexPath) return undefined;

        const version = this.detectCodexVersion(codexPath);
        return { path: codexPath, version: version || undefined };
    },

    /**
     * Auto-detect system Node.js path
     * Uses login shell execution to correctly load user's environment variables (PATH, etc.)
     * @returns Node.js executable path, empty string if not found
     */
    detectNodePath(): string {
        const isWindows = os.platform() === 'win32';

        // 1. Try to find via shell command (consistent with runtime logic)
        try {
            const command = isWindows ? 'where node' : 'which node';
            const result = execSync(command, { 
                encoding: 'utf8',
                stdio: ['pipe', 'pipe', 'pipe']
            }).trim().split('\n')[0];

            if (result && fs.existsSync(result)) {
                return result;
            }
        } catch {
            // Ignore error, continue trying other methods
        }

        // 2. Check common installation paths
        const commonPaths = isWindows
            ? [
                'C:\\Program Files\\nodejs\\node.exe',
                'C:\\Program Files (x86)\\nodejs\\node.exe',
                process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Programs', 'node', 'node.exe') : null,
                process.env.APPDATA ? path.join(process.env.APPDATA, 'nvm', 'current', 'node.exe') : null,
                process.env.NVM_HOME ? path.join(process.env.NVM_HOME, 'current', 'node.exe') : null
            ]
            : [
                '/usr/local/bin/node',
                '/usr/bin/node',
                '/opt/homebrew/bin/node',
                process.env.HOME ? path.join(process.env.HOME, '.nvm', 'current', 'bin', 'node') : null,
                process.env.HOME ? path.join(process.env.HOME, '.local', 'bin', 'node') : null
            ];

        for (const nodePath of commonPaths) {
            if (nodePath && fs.existsSync(nodePath)) {
                return nodePath;
            }
        }

        return '';
    },

    /**
     * Auto-detect system Codex path
     * @returns Codex executable path, empty string if not found
     */
    detectCodexPath(): string {
        const isWindows = os.platform() === 'win32';
        const isMac = os.platform() === 'darwin';

        // Search PATH directly
        const pathEnv = process.env.PATH || '';
        const pathEntries = pathEnv.split(path.delimiter);
        const fileNames = isWindows
            ? ['codex.exe', 'codex.cmd', 'codex.bat']
            : ['codex'];

        for (const dir of pathEntries) {
            if (!dir) continue;
            for (const name of fileNames) {
                const candidate = path.join(dir, name);
                try {
                    if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) {
                        return candidate;
                    }
                } catch {
                    // Ignore access errors
                }
            }
        }

        // Common install paths
        const commonPaths: (string | null)[] = isWindows
            ? [
                'C:\\Program Files\\Codex\\codex.exe',
                'C:\\Program Files (x86)\\Codex\\codex.exe',
                process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Codex', 'codex.exe') : null,
                process.env.USERPROFILE ? path.join(process.env.USERPROFILE, '.codex', 'codex.exe') : null
            ]
            : isMac
            ? [
                '/usr/local/bin/codex',
                '/opt/homebrew/bin/codex',
                os.homedir() ? path.join(os.homedir(), '.codex', 'codex') : null,
                '/Applications/Codex.app/Contents/MacOS/codex'
            ]
            : [
                '/usr/local/bin/codex',
                '/usr/bin/codex',
                os.homedir() ? path.join(os.homedir(), '.local', 'bin', 'codex') : null,
                os.homedir() ? path.join(os.homedir(), '.codex', 'codex') : null
            ];

        for (const codexPath of commonPaths) {
            if (codexPath) {
                try {
                    if (fs.existsSync(codexPath) && fs.statSync(codexPath).isFile()) {
                        return codexPath;
                    }
                } catch {
                    // Ignore access errors
                }
            }
        }

        // Vendor fallback (dev environment)
        const vendorPath = this.detectCodexVendorBinary();
        if (vendorPath) {
            return vendorPath;
        }

        return '';
    },

    /**
     * Detect Node.js version
     * @param nodePath Node.js executable path
     * @returns Version string (e.g., v24.2.0), null if not detected
     */
    detectNodeVersion(nodePath: string): string | null {
        try {
            const result = execSync(`"${nodePath}" --version`, {
                encoding: 'utf8',
                stdio: ['pipe', 'pipe', 'pipe']
            }).trim();

            if (result) {
                return result;
            }
        } catch {
            // Ignore error
        }

        return null;
    },

    /**
     * Detect Codex version
     * @param codexPath Codex executable path
     * @returns Version string, null if not detected
     */
    detectCodexVersion(codexPath: string): string | null {
        try {
            const result = execSync(`"${codexPath}" --version`, {
                encoding: 'utf8',
                stdio: ['pipe', 'pipe', 'pipe']
            }).trim();

            if (result) {
                return result;
            }
        } catch {
            // Ignore error
        }

        return null;
    },

    /**
     * Detect Codex vendor binary in development environment
     * @returns Vendor path, null if not found
     */
    detectCodexVendorBinary(): string | null {
        const platform = os.platform();
        const arch = os.arch();
        const isWindows = platform === 'win32';

        let triple: string | null = null;
        if (isWindows && arch.includes('64')) {
            triple = 'x86_64-pc-windows-msvc';
        } else if (platform === 'darwin' && arch === 'arm64') {
            triple = 'aarch64-apple-darwin';
        } else if (platform === 'darwin' && arch.includes('x86')) {
            triple = 'x86_64-apple-darwin';
        } else if (platform === 'linux' && arch === 'arm64') {
            triple = 'aarch64-unknown-linux-musl';
        } else if (platform === 'linux' && arch.includes('64')) {
            triple = 'x86_64-unknown-linux-musl';
        }

        if (!triple) return null;

        const binaryName = isWindows ? 'codex.exe' : 'codex';
        const candidate = path.join(
            'external',
            'openai-codex',
            'sdk',
            'vendor',
            triple,
            'codex',
            binaryName
        );

        try {
            if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) {
                return path.resolve(candidate);
            }
        } catch {
            // Ignore access errors
        }

        return null;
    },

    /**
     * Check if current platform is Windows
     */
    isWindows(): boolean {
        return os.platform() === 'win32';
    },

    /**
     * Check if current platform is macOS
     */
    isMac(): boolean {
        return os.platform() === 'darwin';
    },

    /**
     * Check if current platform is Linux
     */
    isLinux(): boolean {
        return os.platform() === 'linux';
    }
};

export default EnvironmentDetection;
