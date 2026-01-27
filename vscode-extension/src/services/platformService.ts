/**
 * Platform Service
 * Provides platform and environment information
 */

import * as vscode from 'vscode';
import * as os from 'os';

export interface PlatformInfo {
    os: 'windows' | 'macos' | 'linux';
    osVersion: string;
    arch: string;
    vscodeVersion: string;
    appName: string;
    shell: string;
    homeDir: string;
    tempDir: string;
}

export interface WorkspaceInfo {
    name: string | undefined;
    rootPath: string | undefined;
    workspaceFolders: string[];
}

export interface ExtensionInfo {
    id: string;
    version: string;
    isActive: boolean;
}

export class PlatformService {
    private static instance: PlatformService | null = null;

    private constructor() {}

    static getInstance(): PlatformService {
        if (!PlatformService.instance) {
            PlatformService.instance = new PlatformService();
        }
        return PlatformService.instance;
    }

    /**
     * Get platform information
     */
    getPlatformInfo(): PlatformInfo {
        const platform = process.platform;
        let osType: 'windows' | 'macos' | 'linux';
        
        switch (platform) {
            case 'win32':
                osType = 'windows';
                break;
            case 'darwin':
                osType = 'macos';
                break;
            default:
                osType = 'linux';
        }

        return {
            os: osType,
            osVersion: os.release(),
            arch: process.arch,
            vscodeVersion: vscode.version,
            appName: vscode.env.appName,
            shell: vscode.env.shell,
            homeDir: os.homedir(),
            tempDir: os.tmpdir()
        };
    }

    /**
     * Get workspace information
     */
    getWorkspaceInfo(): WorkspaceInfo {
        const folders = vscode.workspace.workspaceFolders || [];
        
        return {
            name: vscode.workspace.name,
            rootPath: folders[0]?.uri.fsPath,
            workspaceFolders: folders.map(f => f.uri.fsPath)
        };
    }

    /**
     * Get extension information
     */
    getExtensionInfo(extensionId: string): ExtensionInfo | undefined {
        const extension = vscode.extensions.getExtension(extensionId);
        
        if (!extension) {
            return undefined;
        }

        return {
            id: extension.id,
            version: extension.packageJSON.version,
            isActive: extension.isActive
        };
    }

    /**
     * Check if running on Windows
     */
    isWindows(): boolean {
        return process.platform === 'win32';
    }

    /**
     * Check if running on macOS
     */
    isMacOS(): boolean {
        return process.platform === 'darwin';
    }

    /**
     * Check if running on Linux
     */
    isLinux(): boolean {
        return process.platform === 'linux';
    }

    /**
     * Get default shell for current platform
     */
    getDefaultShell(): string {
        if (this.isWindows()) {
            return 'powershell.exe';
        }
        return vscode.env.shell || '/bin/bash';
    }
}

export const platformService = PlatformService.getInstance();
