/**
 * Tool Details Types
 * 
 * Tool detail type definitions
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/types/ToolDetails.kt
 */

import { EditOperation } from './displayItem';

// ============ Tool Detail Types ============

/**
 * Edit tool detail
 */
export interface EditToolDetail {
    kind: 'EditToolDetail';
    filePath: string;
    oldString: string;
    newString: string;
    replaceAll: boolean;
}

/**
 * MultiEdit edit operation (nested type)
 */
export interface MultiEditOperation {
    oldString: string;
    newString: string;
    replaceAll: boolean;
}

/**
 * MultiEdit tool detail
 */
export interface MultiEditToolDetail {
    kind: 'MultiEditToolDetail';
    filePath: string;
    edits: MultiEditOperation[];
}

/**
 * Read tool detail
 */
export interface ReadToolDetail {
    kind: 'ReadToolDetail';
    filePath: string;
    offset?: number;
    limit?: number;
}

/**
 * Write tool detail
 */
export interface WriteToolDetail {
    kind: 'WriteToolDetail';
    filePath: string;
}

/**
 * Tool detail union type
 */
export type ToolDetail =
    | EditToolDetail
    | MultiEditToolDetail
    | ReadToolDetail
    | WriteToolDetail;

/**
 * Tool call view model (simplified version)
 */
export interface ToolCallViewModel {
    toolDetail?: ToolDetail;
}

// ============ Factory Functions ============

/**
 * Create an edit tool detail
 */
export function createEditToolDetail(
    filePath: string,
    oldString: string,
    newString: string,
    replaceAll: boolean = false
): EditToolDetail {
    return {
        kind: 'EditToolDetail',
        filePath,
        oldString,
        newString,
        replaceAll
    };
}

/**
 * Create a multi-edit tool detail
 */
export function createMultiEditToolDetail(
    filePath: string,
    edits: MultiEditOperation[]
): MultiEditToolDetail {
    return {
        kind: 'MultiEditToolDetail',
        filePath,
        edits
    };
}

/**
 * Create a read tool detail
 */
export function createReadToolDetail(
    filePath: string,
    offset?: number,
    limit?: number
): ReadToolDetail {
    return {
        kind: 'ReadToolDetail',
        filePath,
        offset,
        limit
    };
}

/**
 * Create a write tool detail
 */
export function createWriteToolDetail(
    filePath: string
): WriteToolDetail {
    return {
        kind: 'WriteToolDetail',
        filePath
    };
}

/**
 * Create a tool call view model
 */
export function createToolCallViewModel(
    toolDetail?: ToolDetail
): ToolCallViewModel {
    return {
        toolDetail
    };
}

// ============ Type Guards ============

/**
 * Check if tool detail is edit type
 */
export function isEditToolDetail(detail: ToolDetail): detail is EditToolDetail {
    return detail.kind === 'EditToolDetail';
}

/**
 * Check if tool detail is multi-edit type
 */
export function isMultiEditToolDetail(detail: ToolDetail): detail is MultiEditToolDetail {
    return detail.kind === 'MultiEditToolDetail';
}

/**
 * Check if tool detail is read type
 */
export function isReadToolDetail(detail: ToolDetail): detail is ReadToolDetail {
    return detail.kind === 'ReadToolDetail';
}

/**
 * Check if tool detail is write type
 */
export function isWriteToolDetail(detail: ToolDetail): detail is WriteToolDetail {
    return detail.kind === 'WriteToolDetail';
}

// ============ Conversion Utilities ============

/**
 * Convert EditOperation to MultiEditOperation
 */
export function editOperationToMultiEditOperation(edit: EditOperation): MultiEditOperation {
    return {
        oldString: edit.oldString,
        newString: edit.newString,
        replaceAll: edit.replaceAll
    };
}

/**
 * Convert MultiEditOperation to EditOperation
 */
export function multiEditOperationToEditOperation(edit: MultiEditOperation): EditOperation {
    return {
        oldString: edit.oldString,
        newString: edit.newString,
        replaceAll: edit.replaceAll
    };
}
