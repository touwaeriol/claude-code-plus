/**
 * Shell Resolver
 * 
 * Resolves shell names to executable paths.
 * Uses VS Code's available shells when possible.
 * Translated from JetBrains plugin's ShellResolver.kt
 */

import * as os from 'os';
import * as path from 'path';
import * as fs from 'fs';

/**
 * Detected shell information
 */
export interface DetectedShell {
    name: string;
    path: string;
}

/**
 * Shell resolver for finding available shells on the system
 */
export class ShellResolver {
    private static cachedShells: DetectedShell[] | null = null;

    /**
     * Get shell path by name
     * @param shellName Shell name (e.g., "git-bash", "powershell", "bash")
     * @returns Shell path or undefined if not found
     */
    static getShellPath(shellName: string): string | undefined {
        const detectedShells = this.detectInstalledShells();

        // Try exact match
        const matched = detectedShells.find(shell => 
            this.normalizeShellName(shell.name).toLowerCase() === shellName.toLowerCase() ||
            shell.name.toLowerCase() === shellName.toLowerCase()
        );

        if (matched) {
            console.log(`[ShellResolver] Found shell '${shellName}' at path: ${matched.path}`);
            return matched.path;
        }

        console.warn(`[ShellResolver] Shell '${shellName}' not found in detected shells: ${detectedShells.map(s => s.name).join(', ')}`);
        return undefined;
    }

    /**
     * Get shell command list (for terminal creation)
     */
    static getShellCommand(shellName: string): string[] | undefined {
        const shellPath = this.getShellPath(shellName);
        if (!shellPath) return undefined;
        return [shellPath];
    }

    /**
     * Detect installed shells on the system
     */
    static detectInstalledShells(): DetectedShell[] {
        if (this.cachedShells) {
            return this.cachedShells;
        }

        const shells: DetectedShell[] = [];
        const platform = os.platform();

        if (platform === 'win32') {
            // Windows shells
            const windowsShells = [
                { name: 'PowerShell', paths: ['C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe'] },
                { name: 'Command Prompt', paths: ['C:\\Windows\\System32\\cmd.exe'] },
                { name: 'Git Bash', paths: [
                    'C:\\Program Files\\Git\\bin\\bash.exe',
                    'C:\\Program Files (x86)\\Git\\bin\\bash.exe',
                    path.join(os.homedir(), 'AppData', 'Local', 'Programs', 'Git', 'bin', 'bash.exe')
                ]},
                { name: 'WSL', paths: ['C:\\Windows\\System32\\wsl.exe'] }
            ];

            // Check for PowerShell Core
            const pwshPath = this.findInPath('pwsh.exe') || this.findInPath('pwsh');
            if (pwshPath) {
                shells.push({ name: 'PowerShell Core', path: pwshPath });
            }

            for (const shell of windowsShells) {
                for (const shellPath of shell.paths) {
                    if (fs.existsSync(shellPath)) {
                        shells.push({ name: shell.name, path: shellPath });
                        break;
                    }
                }
            }
        } else {
            // Unix-like shells
            const unixShells = [
                { name: 'bash', paths: ['/bin/bash', '/usr/bin/bash', '/usr/local/bin/bash'] },
                { name: 'zsh', paths: ['/bin/zsh', '/usr/bin/zsh', '/usr/local/bin/zsh'] },
                { name: 'fish', paths: ['/usr/bin/fish', '/usr/local/bin/fish'] },
                { name: 'sh', paths: ['/bin/sh', '/usr/bin/sh'] }
            ];

            for (const shell of unixShells) {
                for (const shellPath of shell.paths) {
                    if (fs.existsSync(shellPath)) {
                        shells.push({ name: shell.name, path: shellPath });
                        break;
                    }
                }
            }
        }

        this.cachedShells = shells;
        return shells;
    }

    /**
     * Find executable in PATH
     */
    private static findInPath(executable: string): string | undefined {
        const pathEnv = process.env.PATH || '';
        const pathSeparator = os.platform() === 'win32' ? ';' : ':';
        const paths = pathEnv.split(pathSeparator);

        for (const dir of paths) {
            const fullPath = path.join(dir, executable);
            if (fs.existsSync(fullPath)) {
                return fullPath;
            }
        }
        return undefined;
    }

    /**
     * Normalize shell name to standard format
     */
    static normalizeShellName(name: string): string {
        const lowerName = name.toLowerCase();
        
        if (lowerName.includes('git bash')) return 'git-bash';
        if (lowerName.includes('powershell')) return 'powershell';
        if (lowerName.includes('command prompt') || lowerName === 'cmd') return 'cmd';
        if (lowerName.includes('wsl') || lowerName.includes('ubuntu') || lowerName.includes('debian')) return 'wsl';
        if (lowerName.includes('zsh')) return 'zsh';
        if (lowerName.includes('fish')) return 'fish';
        if (lowerName.includes('bash')) return 'bash';
        
        return lowerName.replace(/ /g, '-');
    }

    /**
     * Clear cached shells (for testing or refresh)
     */
    static clearCache(): void {
        this.cachedShells = null;
    }
}
