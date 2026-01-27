import * as vscode from 'vscode';
import {
    LanguageAnalysisService,
    SearchScope,
    SymbolInfo,
} from './languageAnalysisService';

/**
 * Noop (No-Operation) implementation of LanguageAnalysisService
 *
 * This is the default fallback implementation used when language analysis
 * features are not available or not needed. All methods return empty results.
 *
 * Use cases:
 * - When the VS Code language server is not active
 * - When running in a restricted environment
 * - For testing/mocking purposes
 *
 * This implementation mirrors JetBrains' NoopLanguageAnalysisService behavior,
 * ensuring the plugin works gracefully in environments without full LSP support.
 */
export class NoopLanguageAnalysisService implements LanguageAnalysisService {

    isAvailable(): boolean {
        return false;
    }

    async isClass(_uri: vscode.Uri, _position: vscode.Position): Promise<boolean> {
        return false;
    }

    async isMethod(_uri: vscode.Uri, _position: vscode.Position): Promise<boolean> {
        return false;
    }

    async findClassInheritors(
        _uri: vscode.Uri,
        _position: vscode.Position,
        _scope?: SearchScope,
        _deep?: boolean
    ): Promise<vscode.Location[]> {
        return [];
    }

    async findOverridingMethods(
        _uri: vscode.Uri,
        _position: vscode.Position,
        _scope?: SearchScope,
        _deep?: boolean
    ): Promise<vscode.Location[]> {
        return [];
    }

    async getAllClassNames(_scope?: SearchScope): Promise<string[]> {
        return [];
    }

    async getClassesByName(_name: string, _scope?: SearchScope): Promise<SymbolInfo[]> {
        return [];
    }

    async getAllMethodNames(_scope?: SearchScope): Promise<string[]> {
        return [];
    }

    async getMethodsByName(_name: string, _scope?: SearchScope): Promise<SymbolInfo[]> {
        return [];
    }

    async getAllFieldNames(_scope?: SearchScope): Promise<string[]> {
        return [];
    }

    async getFieldsByName(_name: string, _scope?: SearchScope): Promise<SymbolInfo[]> {
        return [];
    }

    async getDocumentSymbols(_uri: vscode.Uri): Promise<vscode.DocumentSymbol[]> {
        return [];
    }

    async findReferences(
        _uri: vscode.Uri,
        _position: vscode.Position,
        _includeDeclaration?: boolean
    ): Promise<vscode.Location[]> {
        return [];
    }

    async findDefinition(_uri: vscode.Uri, _position: vscode.Position): Promise<vscode.Location[]> {
        return [];
    }

    async findTypeDefinition(_uri: vscode.Uri, _position: vscode.Position): Promise<vscode.Location[]> {
        return [];
    }
}
