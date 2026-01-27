import * as vscode from 'vscode';

/**
 * Symbol information representing a code element (class, method, field, etc.)
 */
export interface SymbolInfo {
    /** Symbol name */
    name: string;
    /** Symbol kind (class, method, field, etc.) */
    kind: vscode.SymbolKind;
    /** Location in the file */
    location: vscode.Location;
    /** Container name (e.g., class name for a method) */
    containerName?: string;
    /** Full qualified name */
    fullName?: string;
}

/**
 * Search scope for language analysis operations
 */
export interface SearchScope {
    /** Include workspace files */
    includeWorkspace: boolean;
    /** Include node_modules or dependencies */
    includeDependencies: boolean;
    /** Specific file patterns to include (glob patterns) */
    includePatterns?: string[];
    /** Specific file patterns to exclude (glob patterns) */
    excludePatterns?: string[];
}

/**
 * Default search scope - workspace only
 */
export const DEFAULT_SEARCH_SCOPE: SearchScope = {
    includeWorkspace: true,
    includeDependencies: false,
};

/**
 * Language Analysis Service Interface
 *
 * Provides cross-language code analysis functionality using VS Code LSP APIs.
 * This is the VS Code equivalent of JetBrains' LanguageAnalysisService.
 *
 * Implementation Strategy:
 * - Default implementation (NoopLanguageAnalysisService): Returns empty results when LSP is unavailable
 * - VSCode implementation (VSCodeLanguageAnalysisService): Uses VS Code Language Server Protocol APIs
 *
 * Usage:
 * ```typescript
 * const service = LanguageAnalysisService.getInstance();
 * if (service.isAvailable()) {
 *     const inheritors = await service.findClassInheritors(symbolUri, position);
 * }
 * ```
 */
export interface LanguageAnalysisService {
    /**
     * Check if the service is available
     * @returns true if language analysis features are available (LSP is active)
     */
    isAvailable(): boolean;

    /**
     * Check if a symbol at the given position is a class
     * @param uri Document URI
     * @param position Position in the document
     * @returns true if the symbol is a class/interface/type
     */
    isClass(uri: vscode.Uri, position: vscode.Position): Promise<boolean>;

    /**
     * Check if a symbol at the given position is a method/function
     * @param uri Document URI
     * @param position Position in the document
     * @returns true if the symbol is a method/function
     */
    isMethod(uri: vscode.Uri, position: vscode.Position): Promise<boolean>;

    /**
     * Find classes that inherit from the class at the given position
     * Uses vscode.executeImplementationProvider to find implementations
     *
     * @param uri Document URI containing the class
     * @param position Position of the class symbol
     * @param scope Search scope
     * @param deep Whether to include indirect inheritors (transitive)
     * @returns List of inheriting class locations
     */
    findClassInheritors(
        uri: vscode.Uri,
        position: vscode.Position,
        scope?: SearchScope,
        deep?: boolean
    ): Promise<vscode.Location[]>;

    /**
     * Find methods that override the method at the given position
     * Uses vscode.executeImplementationProvider to find implementations
     *
     * @param uri Document URI containing the method
     * @param position Position of the method symbol
     * @param scope Search scope
     * @param deep Whether to include indirect overrides (transitive)
     * @returns List of overriding method locations
     */
    findOverridingMethods(
        uri: vscode.Uri,
        position: vscode.Position,
        scope?: SearchScope,
        deep?: boolean
    ): Promise<vscode.Location[]>;

    /**
     * Get all class names in the workspace
     * Uses vscode.executeWorkspaceSymbolProvider
     *
     * @param scope Search scope
     * @returns Array of class names
     */
    getAllClassNames(scope?: SearchScope): Promise<string[]>;

    /**
     * Get classes by name
     * Uses vscode.executeWorkspaceSymbolProvider
     *
     * @param name Class name to search for
     * @param scope Search scope
     * @returns List of matching class symbols
     */
    getClassesByName(name: string, scope?: SearchScope): Promise<SymbolInfo[]>;

    /**
     * Get all method names in the workspace
     * Uses vscode.executeWorkspaceSymbolProvider
     *
     * @param scope Search scope
     * @returns Array of method names
     */
    getAllMethodNames(scope?: SearchScope): Promise<string[]>;

    /**
     * Get methods by name
     * Uses vscode.executeWorkspaceSymbolProvider
     *
     * @param name Method name to search for
     * @param scope Search scope
     * @returns List of matching method symbols
     */
    getMethodsByName(name: string, scope?: SearchScope): Promise<SymbolInfo[]>;

    /**
     * Get all field/property names in the workspace
     * Uses vscode.executeWorkspaceSymbolProvider
     *
     * @param scope Search scope
     * @returns Array of field names
     */
    getAllFieldNames(scope?: SearchScope): Promise<string[]>;

    /**
     * Get fields by name
     * Uses vscode.executeWorkspaceSymbolProvider
     *
     * @param name Field name to search for
     * @param scope Search scope
     * @returns List of matching field symbols
     */
    getFieldsByName(name: string, scope?: SearchScope): Promise<SymbolInfo[]>;

    /**
     * Get document symbols for a file
     * Uses vscode.executeDocumentSymbolProvider
     *
     * @param uri Document URI
     * @returns List of symbols in the document
     */
    getDocumentSymbols(uri: vscode.Uri): Promise<vscode.DocumentSymbol[]>;

    /**
     * Find all references to a symbol
     * Uses vscode.executeReferenceProvider
     *
     * @param uri Document URI
     * @param position Position of the symbol
     * @param includeDeclaration Whether to include the declaration itself
     * @returns List of reference locations
     */
    findReferences(
        uri: vscode.Uri,
        position: vscode.Position,
        includeDeclaration?: boolean
    ): Promise<vscode.Location[]>;

    /**
     * Go to definition of a symbol
     * Uses vscode.executeDefinitionProvider
     *
     * @param uri Document URI
     * @param position Position of the symbol
     * @returns Definition location(s)
     */
    findDefinition(
        uri: vscode.Uri,
        position: vscode.Position
    ): Promise<vscode.Location[]>;

    /**
     * Find type definition of a symbol
     * Uses vscode.executeTypeDefinitionProvider
     *
     * @param uri Document URI
     * @param position Position of the symbol
     * @returns Type definition location(s)
     */
    findTypeDefinition(
        uri: vscode.Uri,
        position: vscode.Position
    ): Promise<vscode.Location[]>;
}

/**
 * Singleton instance holder
 */
let instance: LanguageAnalysisService | null = null;

/**
 * Get the singleton instance of LanguageAnalysisService
 * Returns VSCodeLanguageAnalysisService if LSP is available, otherwise NoopLanguageAnalysisService
 */
export function getLanguageAnalysisService(): LanguageAnalysisService {
    if (!instance) {
        // Dynamic import to avoid circular dependencies
        // Will be set by the extension activation
        throw new Error('LanguageAnalysisService not initialized. Call setLanguageAnalysisService first.');
    }
    return instance;
}

/**
 * Set the singleton instance of LanguageAnalysisService
 * Called during extension activation
 */
export function setLanguageAnalysisService(service: LanguageAnalysisService): void {
    instance = service;
}

/**
 * Helper class with static getInstance method for compatibility with JetBrains API pattern
 */
export const LanguageAnalysisServiceFactory = {
    getInstance(): LanguageAnalysisService {
        return getLanguageAnalysisService();
    },

    setInstance(service: LanguageAnalysisService): void {
        setLanguageAnalysisService(service);
    }
};
