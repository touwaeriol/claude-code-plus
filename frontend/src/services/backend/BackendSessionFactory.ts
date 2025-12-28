/**
 * Backend Session Factory
 *
 * This module provides a factory for creating backend sessions.
 * It handles:
 * - Session instantiation based on backend type
 * - Backend availability checking
 * - Default backend selection
 * - Available backend enumeration
 */

import type {
  BackendType,
  BackendConfig,
} from '@/types/backend'
import type { BackendSession, SessionConnectOptions } from './BackendSession'
import { getDefaultConfig } from '@/types/backend'
import { resolveServerHttpUrl } from '@/utils/serverUrl'

// Import session implementations
import { ClaudeSession } from './ClaudeSession'
import { CodexSession } from './CodexSession'

// ============================================================================
// Session Registry
// ============================================================================

/**
 * Session class constructor type
 */
type SessionConstructor = new (config: BackendConfig) => BackendSession

/**
 * Registry mapping backend types to their session classes
 *
 * This allows easy extension to support new backend types.
 */
const SESSION_REGISTRY: Record<BackendType, SessionConstructor | null> = {
  claude: ClaudeSession,
  codex: CodexSession,
}

/**
 * Register a session implementation for a backend type
 *
 * This is used internally and can be called when session implementations
 * are loaded dynamically.
 *
 * @param type Backend type
 * @param sessionClass Session class constructor
 */
export function registerSessionImplementation(
  type: BackendType,
  sessionClass: SessionConstructor
): void {
  SESSION_REGISTRY[type] = sessionClass
}

// ============================================================================
// Backend Availability Checking
// ============================================================================

/**
 * Check if a backend type is available
 *
 * This performs runtime checks to determine if a backend can be used:
 * - For Claude: Check if backend server is reachable
 * - For Codex: Check if Codex SDK is available and configured
 *
 * @param type Backend type to check
 * @returns Promise resolving to true if backend is available
 */
export async function isBackendAvailable(type: BackendType): Promise<boolean> {
  // Check if session implementation is registered
  if (!SESSION_REGISTRY[type]) {
    console.warn(`[BackendFactory] No session implementation registered for ${type}`)
    return false
  }

  try {
    if (type === 'claude') {
      // Check if backend server is reachable
      const serverUrl = resolveServerHttpUrl()
      const response = await fetch(`${serverUrl}/api/health`, {
        method: 'GET',
        signal: AbortSignal.timeout(3000), // 3 second timeout
      })

      if (!response.ok) {
        console.warn('[BackendFactory] Claude backend health check failed:', response.status)
        return false
      }

      const health = await response.json()
      return health.status === 'ok'

    } else if (type === 'codex') {
      // Check if Codex backend is available
      const serverUrl = resolveServerHttpUrl()
      const response = await fetch(`${serverUrl}/api/codex/health`, {
        method: 'GET',
        signal: AbortSignal.timeout(3000),
      })

      if (!response.ok) {
        console.warn('[BackendFactory] Codex backend health check failed:', response.status)
        return false
      }

      const health = await response.json()
      return health.status === 'ok'
    }
  } catch (error) {
    console.warn(`[BackendFactory] Backend availability check failed for ${type}:`, error)
    return false
  }

  return false
}

/**
 * Get all backend types that are available
 *
 * This performs availability checks for all registered backends
 * and returns only those that are currently usable.
 *
 * @returns Promise resolving to array of available backend types
 */
export async function getAvailableBackendTypes(): Promise<BackendType[]> {
  const types: BackendType[] = ['claude', 'codex']
  const availabilityChecks = await Promise.all(
    types.map(async (type) => ({
      type,
      available: await isBackendAvailable(type),
    }))
  )

  return availabilityChecks
    .filter((result) => result.available)
    .map((result) => result.type)
}

/**
 * Get all registered backend types (regardless of availability)
 *
 * @returns Array of all backend types that have implementations
 */
export function getRegisteredBackendTypes(): BackendType[] {
  return (Object.keys(SESSION_REGISTRY) as BackendType[]).filter(
    (type) => SESSION_REGISTRY[type] !== null
  )
}

// ============================================================================
// Default Backend Selection
// ============================================================================

/**
 * Default backend preference order
 *
 * The factory will try backends in this order when determining
 * the default backend to use.
 */
const DEFAULT_BACKEND_PREFERENCE: BackendType[] = ['claude', 'codex']

/**
 * Get the default backend type to use
 *
 * This selects the first available backend from the preference list.
 * If no backends are available, it returns 'claude' as a fallback
 * (even though it may not be available).
 *
 * @param checkAvailability Whether to check actual availability (async)
 * @returns Backend type to use as default
 */
export async function getDefaultBackendType(
  checkAvailability = true
): Promise<BackendType> {
  if (!checkAvailability) {
    // Just return first registered backend
    const registered = getRegisteredBackendTypes()
    if (registered.length > 0) {
      return registered[0]
    }
    return 'claude' // Ultimate fallback
  }

  // Check availability in preference order
  for (const type of DEFAULT_BACKEND_PREFERENCE) {
    if (await isBackendAvailable(type)) {
      return type
    }
  }

  // No backends available, return preference fallback
  console.warn('[BackendFactory] No backends available, using fallback')
  return DEFAULT_BACKEND_PREFERENCE[0]
}

/**
 * Synchronous version that just returns the first preference
 *
 * Use this when you need a default immediately without async checks.
 *
 * @returns First preferred backend type
 */
export function getDefaultBackendTypeSync(): BackendType {
  const registered = getRegisteredBackendTypes()
  if (registered.length > 0) {
    // Return first registered that's in preference list
    for (const preferred of DEFAULT_BACKEND_PREFERENCE) {
      if (registered.includes(preferred)) {
        return preferred
      }
    }
    // Or just first registered
    return registered[0]
  }
  return DEFAULT_BACKEND_PREFERENCE[0]
}

// ============================================================================
// Session Factory
// ============================================================================

/**
 * Factory error class
 */
export class BackendFactoryError extends Error {
  constructor(
    message: string,
    public readonly backendType: BackendType,
    public readonly cause?: unknown
  ) {
    super(message)
    this.name = 'BackendFactoryError'
  }
}

/**
 * Create a backend session
 *
 * This is the main factory function that instantiates the appropriate
 * session class based on the backend type.
 *
 * @param type Backend type to create session for
 * @param options Optional connection options (if you want to connect immediately)
 * @returns BackendSession instance
 * @throws BackendFactoryError if backend is not available or instantiation fails
 *
 * @example
 * ```typescript
 * // Create and connect later
 * const session = createSession('claude')
 * await session.connect({ config: myConfig })
 *
 * // Create with config and connect later
 * const session = createSession('claude', { config: myConfig })
 * await session.connect({ config: myConfig })
 * ```
 */
export function createSession(
  type: BackendType,
  config?: BackendConfig
): BackendSession {
  // Get session class from registry
  const SessionClass = SESSION_REGISTRY[type]

  if (!SessionClass) {
    throw new BackendFactoryError(
      `Backend type "${type}" is not registered. ` +
        `Available backends: ${getRegisteredBackendTypes().join(', ')}`,
      type
    )
  }

  try {
    // Use provided config or get default for this backend type
    const sessionConfig = config || getDefaultConfig(type)

    // Instantiate session
    const session = new SessionClass(sessionConfig)

    console.log(`[BackendFactory] Created ${type} session`)
    return session
  } catch (error) {
    throw new BackendFactoryError(
      `Failed to create ${type} session: ${error instanceof Error ? error.message : String(error)}`,
      type,
      error
    )
  }
}

/**
 * Create a session and connect it immediately
 *
 * This is a convenience function that combines session creation
 * and connection in one step.
 *
 * @param type Backend type
 * @param options Connection options
 * @returns Promise resolving to connected session
 * @throws BackendFactoryError if creation or connection fails
 *
 * @example
 * ```typescript
 * const session = await createAndConnectSession('claude', {
 *   config: myConfig,
 *   projectPath: '/path/to/project'
 * })
 *
 * // Session is now connected and ready to use
 * session.sendMessage({ contents: [{ type: 'text', text: 'Hello!' }] })
 * ```
 */
export async function createAndConnectSession(
  type: BackendType,
  options: SessionConnectOptions
): Promise<BackendSession> {
  const session = createSession(type, options.config)

  try {
    await session.connect(options)
    return session
  } catch (error) {
    // Clean up session on connection failure
    session.disconnect()

    throw new BackendFactoryError(
      `Failed to connect ${type} session: ${error instanceof Error ? error.message : String(error)}`,
      type,
      error
    )
  }
}

/**
 * Create a session using the default backend type
 *
 * This automatically selects the best available backend.
 *
 * @param config Optional config (defaults will be used for selected backend)
 * @param checkAvailability Whether to check backend availability
 * @returns Promise resolving to session
 *
 * @example
 * ```typescript
 * const session = await createDefaultSession()
 * await session.connect({ config: session.getState().config })
 * ```
 */
export async function createDefaultSession(
  config?: BackendConfig,
  checkAvailability = true
): Promise<BackendSession> {
  const defaultType = await getDefaultBackendType(checkAvailability)
  return createSession(defaultType, config)
}

// ============================================================================
// Exports
// ============================================================================

/**
 * Main factory object for convenient access
 */
export const BackendSessionFactory = {
  // Creation
  createSession,
  createAndConnectSession,
  createDefaultSession,

  // Registry
  registerSessionImplementation,
  getRegisteredBackendTypes,

  // Availability
  isBackendAvailable,
  getAvailableBackendTypes,

  // Defaults
  getDefaultBackendType,
  getDefaultBackendTypeSync,
} as const

// Re-export for convenience
export default BackendSessionFactory
