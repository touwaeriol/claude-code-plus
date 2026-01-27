/**
 * Common types shared across Claude Agent SDK
 */

/**
 * JSON value type - can be any valid JSON value.
 */
export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | JsonObject;

/**
 * JSON object type - a map of string keys to JSON values.
 */
export type JsonObject = { [key: string]: JsonValue };

/**
 * JSON array type.
 */
export type JsonArray = JsonValue[];
