import * as vscode from 'vscode';
import {
    LanguageAnalysisService,
    SearchScope,
    SymbolInfo,
    DEFAULT_SEARCH_SCOPE
} from './languageAnalysisService';

/**
 * VS Code implementation of LanguageAnalysisService
 *
 * Uses VS Code Language Server Protocol (LSP) APIs to provide code analysis functionality:
 * - vscode.executeDefinitionProvider: Find symbol definitions
 * - vscode.executeReferenceProvider: Find symbol references
 * - vscode.executeImplementationProvider: Find implementations (inheritors/overrides)
 * - vscode.executeDocumentSymbolProvider: Get document symbols
 * - vscode.executeWorkspaceSymbolProvider: Search workspace symbols
 * - vscode.executeTypeDefinitionProvider: Find type definitions
 *
 * This implementation provides full functionality when language servers are active
 * (e.g., TypeScript Language Server, Java Language Server, etc.)
 */
export class VSCodeLanguageAnalysisService implements LanguageAnalysisService {

    /**
     * Symbol kinds that represent classes, interfaces, or types
     */
    private static readonly CLASS_KINDS = new Set([
        vscode.SymbolKind.Class,
        vscode.SymbolKind.Interface,
        vscode.SymbolKind.Struct,
        vscode.SymbolKind.Enum,
    ]);

    /**
     * Symbol kinds that represent methods or functions
     */
    private static readonly METHOD_KINDS = new Set([
        vscode.SymbolKind.Method,
        vscode.SymbolKind.Function,
        vscode.SymbolKind.Constructor,
    ]);

    /**
     * Symbol kinds that represent fields or properties
     */
    private static readonly FIELD_KINDS = new Set([
        vscode.SymbolKind.Field,
        vscode.SymbolKind.Property,
        vscode.SymbolKind.Variable,
        vscode.SymbolKind.Constant,
    ]);

    isAvailable(): boolean {
        // VS Code LSP is generally always available, but actual functionality
        // depends on whether a language server is active for the current file type
        return true;
    }

    async isClass(uri: vscode.Uri, position: vscode.Position): Promise<boolean> {
        try {
            const symbols = await this.getDocumentSymbols(uri);
            const symbolAtPosition = this.findSymbolAtPosition(symbols, position);
            if (symbolAtPosition) {
                return VSCodeLanguageAnalysisService.CLASS_KINDS.has(symbolAtPosition.kind);
            }
            return false;
        } catch (e) {
            console.error('Error checking if symbol is class:', e);
            return false;
        }
    }

    async isMethod(uri: vscode.Uri, position: vscode.Position): Promise<boolean> {
        try {
            const symbols = await this.getDocumentSymbols(uri);
            const symbolAtPosition = this.findSymbolAtPosition(symbols, position);
            if (symbolAtPosition) {
                return VSCodeLanguageAnalysisService.METHOD_KINDS.has(symbolAtPosition.kind);
            }
            return false;
        } catch (e) {
            console.error('Error checking if symbol is method:', e);
            return false;
        }
    }

    async findClassInheritors(
        uri: vscode.Uri,
        position: vscode.Position,
        scope: SearchScope = DEFAULT_SEARCH_SCOPE,
        deep: boolean = true
    ): Promise<vscode.Location[]> {
        try {
            // Use implementation provider to find implementations of the class
            const implementations = await vscode.commands.executeCommand<vscode.Location[] | vscode.LocationLink[]>(
                'vscode.executeImplementationProvider',
                uri,
                position
            );

            if (!implementations || implementations.length === 0) {
                return [];
            }

            const locations = this.normalizeLocations(implementations);
            const filteredLocations = this.filterByScope(locations, scope);

            // If deep search is requested and we have results, recursively find inheritors
            if (deep && filteredLocations.length > 0) {
                const allInheritors = new Set<string>();
                const result: vscode.Location[] = [];

                for (const loc of filteredLocations) {
                    const key = this.locationKey(loc);
                    if (!allInheritors.has(key)) {
                        allInheritors.add(key);
                        result.push(loc);

                        // Recursively find inheritors of this inheritor
                        const nestedInheritors = await this.findClassInheritors(
                            loc.uri,
                            loc.range.start,
                            scope,
                            false // Prevent infinite recursion by not going deeper
                        );

                        for (const nested of nestedInheritors) {
                            const nestedKey = this.locationKey(nested);
                            if (!allInheritors.has(nestedKey)) {
                                allInheritors.add(nestedKey);
                                result.push(nested);
                            }
                        }
                    }
                }

                return result;
            }

            return filteredLocations;
        } catch (e) {
            console.error('Error finding class inheritors:', e);
            return [];
        }
    }

    async findOverridingMethods(
        uri: vscode.Uri,
        position: vscode.Position,
        scope: SearchScope = DEFAULT_SEARCH_SCOPE,
        deep: boolean = true
    ): Promise<vscode.Location[]> {
        try {
            // Use implementation provider to find method implementations
            const implementations = await vscode.commands.executeCommand<vscode.Location[] | vscode.LocationLink[]>(
                'vscode.executeImplementationProvider',
                uri,
                position
            );

            if (!implementations || implementations.length === 0) {
                return [];
            }

            const locations = this.normalizeLocations(implementations);
            return this.filterByScope(locations, scope);
        } catch (e) {
            console.error('Error finding overriding methods:', e);
            return [];
        }
    }

    async getAllClassNames(scope: SearchScope = DEFAULT_SEARCH_SCOPE): Promise<string[]> {
        try {
            // Search for all class-like symbols
            const symbols = await vscode.commands.executeCommand<vscode.SymbolInformation[]>(
                'vscode.executeWorkspaceSymbolProvider',
                ''
            );

            if (!symbols) {
                return [];
            }

            const classNames = new Set<string>();
            for (const symbol of symbols) {
                if (VSCodeLanguageAnalysisService.CLASS_KINDS.has(symbol.kind)) {
                    if (this.isInScope(symbol.location, scope)) {
                        classNames.add(symbol.name);
                    }
                }
            }

            return Array.from(classNames);
        } catch (e) {
            console.error('Error getting all class names:', e);
            return [];
        }
    }

    async getClassesByName(name: string, scope: SearchScope = DEFAULT_SEARCH_SCOPE): Promise<SymbolInfo[]> {
        try {
            const symbols = await vscode.commands.executeCommand<vscode.SymbolInformation[]>(
                'vscode.executeWorkspaceSymbolProvider',
                name
            );

            if (!symbols) {
                return [];
            }

            return symbols
                .filter(s => VSCodeLanguageAnalysisService.CLASS_KINDS.has(s.kind))
                .filter(s => this.isInScope(s.location, scope))
                .map(s => this.toSymbolInfo(s));
        } catch (e) {
            console.error('Error getting classes by name:', e);
            return [];
        }
    }

    async getAllMethodNames(scope: SearchScope = DEFAULT_SEARCH_SCOPE): Promise<string[]> {
        try {
            const symbols = await vscode.commands.executeCommand<vscode.SymbolInformation[]>(
                'vscode.executeWorkspaceSymbolProvider',
                ''
            );

            if (!symbols) {
                return [];
            }

            const methodNames = new Set<string>();
            for (const symbol of symbols) {
                if (VSCodeLanguageAnalysisService.METHOD_KINDS.has(symbol.kind)) {
                    if (this.isInScope(symbol.location, scope)) {
                        methodNames.add(symbol.name);
                    }
                }
            }

            return Array.from(methodNames);
        } catch (e) {
            console.error('Error getting all method names:', e);
            return [];
        }
    }

    async getMethodsByName(name: string, scope: SearchScope = DEFAULT_SEARCH_SCOPE): Promise<SymbolInfo[]> {
        try {
            const symbols = await vscode.commands.executeCommand<vscode.SymbolInformation[]>(
                'vscode.executeWorkspaceSymbolProvider',
                name
            );

            if (!symbols) {
                return [];
            }

            return symbols
                .filter(s => VSCodeLanguageAnalysisService.METHOD_KINDS.has(s.kind))
                .filter(s => this.isInScope(s.location, scope))
                .map(s => this.toSymbolInfo(s));
        } catch (e) {
            console.error('Error getting methods by name:', e);
            return [];
        }
    }

    async getAllFieldNames(scope: SearchScope = DEFAULT_SEARCH_SCOPE): Promise<string[]> {
        try {
            const symbols = await vscode.commands.executeCommand<vscode.SymbolInformation[]>(
                'vscode.executeWorkspaceSymbolProvider',
                ''
            );

            if (!symbols) {
                return [];
            }

            const fieldNames = new Set<string>();
            for (const symbol of symbols) {
                if (VSCodeLanguageAnalysisService.FIELD_KINDS.has(symbol.kind)) {
                    if (this.isInScope(symbol.location, scope)) {
                        fieldNames.add(symbol.name);
                    }
                }
            }

            return Array.from(fieldNames);
        } catch (e) {
            console.error('Error getting all field names:', e);
            return [];
        }
    }

    async getFieldsByName(name: string, scope: SearchScope = DEFAULT_SEARCH_SCOPE): Promise<SymbolInfo[]> {
        try {
            const symbols = await vscode.commands.executeCommand<vscode.SymbolInformation[]>(
                'vscode.executeWorkspaceSymbolProvider',
                name
            );

            if (!symbols) {
                return [];
            }

            return symbols
                .filter(s => VSCodeLanguageAnalysisService.FIELD_KINDS.has(s.kind))
                .filter(s => this.isInScope(s.location, scope))
                .map(s => this.toSymbolInfo(s));
        } catch (e) {
            console.error('Error getting fields by name:', e);
            return [];
        }
    }

    async getDocumentSymbols(uri: vscode.Uri): Promise<vscode.DocumentSymbol[]> {
        try {
            const symbols = await vscode.commands.executeCommand<vscode.DocumentSymbol[] | vscode.SymbolInformation[]>(
                'vscode.executeDocumentSymbolProvider',
                uri
            );

            if (!symbols || symbols.length === 0) {
                return [];
            }

            // Handle both DocumentSymbol[] and SymbolInformation[] return types
            if (this.isDocumentSymbolArray(symbols)) {
                return symbols;
            }

            // Convert SymbolInformation[] to DocumentSymbol[]
            return symbols.map(s => this.symbolInfoToDocumentSymbol(s));
        } catch (e) {
            console.error('Error getting document symbols:', e);
            return [];
        }
    }

    async findReferences(
        uri: vscode.Uri,
        position: vscode.Position,
        includeDeclaration: boolean = true
    ): Promise<vscode.Location[]> {
        try {
            const references = await vscode.commands.executeCommand<vscode.Location[]>(
                'vscode.executeReferenceProvider',
                uri,
                position
            );

            if (!references) {
                return [];
            }

            if (!includeDeclaration) {
                // Filter out the declaration itself
                const definitions = await this.findDefinition(uri, position);
                const definitionKeys = new Set(definitions.map(d => this.locationKey(d)));
                return references.filter(r => !definitionKeys.has(this.locationKey(r)));
            }

            return references;
        } catch (e) {
            console.error('Error finding references:', e);
            return [];
        }
    }

    async findDefinition(uri: vscode.Uri, position: vscode.Position): Promise<vscode.Location[]> {
        try {
            const definitions = await vscode.commands.executeCommand<vscode.Location[] | vscode.LocationLink[]>(
                'vscode.executeDefinitionProvider',
                uri,
                position
            );

            if (!definitions) {
                return [];
            }

            return this.normalizeLocations(definitions);
        } catch (e) {
            console.error('Error finding definition:', e);
            return [];
        }
    }

    async findTypeDefinition(uri: vscode.Uri, position: vscode.Position): Promise<vscode.Location[]> {
        try {
            const typeDefinitions = await vscode.commands.executeCommand<vscode.Location[] | vscode.LocationLink[]>(
                'vscode.executeTypeDefinitionProvider',
                uri,
                position
            );

            if (!typeDefinitions) {
                return [];
            }

            return this.normalizeLocations(typeDefinitions);
        } catch (e) {
            console.error('Error finding type definition:', e);
            return [];
        }
    }

    // ========== Private Helper Methods ==========

    /**
     * Find symbol at the given position within a list of symbols
     */
    private findSymbolAtPosition(
        symbols: vscode.DocumentSymbol[],
        position: vscode.Position
    ): vscode.DocumentSymbol | undefined {
        for (const symbol of symbols) {
            if (symbol.range.contains(position)) {
                // Check children first for more specific match
                if (symbol.children && symbol.children.length > 0) {
                    const childMatch = this.findSymbolAtPosition(symbol.children, position);
                    if (childMatch) {
                        return childMatch;
                    }
                }
                return symbol;
            }
        }
        return undefined;
    }

    /**
     * Normalize LocationLink[] or Location[] to Location[]
     */
    private normalizeLocations(locations: vscode.Location[] | vscode.LocationLink[]): vscode.Location[] {
        if (locations.length === 0) {
            return [];
        }

        // Check if it's LocationLink[]
        if ('targetUri' in locations[0]) {
            return (locations as vscode.LocationLink[]).map(link => 
                new vscode.Location(link.targetUri, link.targetRange)
            );
        }

        return locations as vscode.Location[];
    }

    /**
     * Filter locations by search scope
     */
    private filterByScope(locations: vscode.Location[], scope: SearchScope): vscode.Location[] {
        return locations.filter(loc => this.isInScope(loc, scope));
    }

    /**
     * Check if a location is within the search scope
     */
    private isInScope(location: vscode.Location, scope: SearchScope): boolean {
        const path = location.uri.fsPath;

        // Check if in workspace
        if (scope.includeWorkspace) {
            const workspaceFolders = vscode.workspace.workspaceFolders;
            if (workspaceFolders) {
                const isInWorkspace = workspaceFolders.some(folder => 
                    path.startsWith(folder.uri.fsPath)
                );
                if (isInWorkspace) {
                    // Check if it's in node_modules
                    const isInNodeModules = path.includes('node_modules');
                    if (isInNodeModules && !scope.includeDependencies) {
                        return false;
                    }
                    return true;
                }
            }
        }

        // Check include/exclude patterns
        if (scope.includePatterns && scope.includePatterns.length > 0) {
            const matches = scope.includePatterns.some(pattern => {
                const glob = new vscode.RelativePattern(vscode.workspace.workspaceFolders?.[0] || '', pattern);
                return vscode.languages.match({ pattern: glob.pattern }, { uri: location.uri } as any);
            });
            if (!matches) {
                return false;
            }
        }

        if (scope.excludePatterns && scope.excludePatterns.length > 0) {
            const excluded = scope.excludePatterns.some(pattern => {
                return path.includes(pattern.replace(/\*/g, ''));
            });
            if (excluded) {
                return false;
            }
        }

        return scope.includeDependencies;
    }

    /**
     * Create a unique key for a location
     */
    private locationKey(location: vscode.Location): string {
        return `${location.uri.toString()}:${location.range.start.line}:${location.range.start.character}`;
    }

    /**
     * Convert SymbolInformation to SymbolInfo
     */
    private toSymbolInfo(symbol: vscode.SymbolInformation): SymbolInfo {
        return {
            name: symbol.name,
            kind: symbol.kind,
            location: symbol.location,
            containerName: symbol.containerName,
            fullName: symbol.containerName ? `${symbol.containerName}.${symbol.name}` : symbol.name,
        };
    }

    /**
     * Convert SymbolInformation to DocumentSymbol
     */
    private symbolInfoToDocumentSymbol(symbol: vscode.SymbolInformation): vscode.DocumentSymbol {
        return new vscode.DocumentSymbol(
            symbol.name,
            '',
            symbol.kind,
            symbol.location.range,
            symbol.location.range
        );
    }

    /**
     * Type guard to check if symbols are DocumentSymbol[]
     */
    private isDocumentSymbolArray(
        symbols: vscode.DocumentSymbol[] | vscode.SymbolInformation[]
    ): symbols is vscode.DocumentSymbol[] {
        if (symbols.length === 0) {
            return true;
        }
        return 'children' in symbols[0];
    }
}
