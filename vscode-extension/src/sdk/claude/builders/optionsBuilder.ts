/**
 * ClaudeAgentOptions Builder Extensions - Provides convenient MCP server configuration
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/builders/ClaudeCodeOptionsExtensions.kt
 *
 * @example
 * ```typescript
 * const options = optionsBuilder()
 *   .model('claude-3-5-sonnet-20241022')
 *   .allowedTools(['Bash', 'Read', 'mcp__calculator__*'])
 *   .addMcpServer('calculator', calculatorServer)
 *   .addSecurityHooks()
 *   .addStatisticsHooks()
 *   .build();
 * ```
 */

import type { ClaudeAgentOptions, McpServerSpec } from '../types/options';
import type { HookEvent, HookMatcher, HookCallback, HookJSONOutput } from '../types/hooks';
import { hookBuilder, securityHook, statisticsHook } from './hookBuilder';

/**
 * Options builder for fluent configuration.
 */
export class OptionsBuilder {
  private options: ClaudeAgentOptions = {};

  /**
   * Set the model.
   */
  model(model: string): this {
    this.options.model = model;
    return this;
  }

  /**
   * Set allowed tools.
   */
  allowedTools(tools: string[]): this {
    this.options.allowedTools = tools;
    return this;
  }

  /**
   * Add allowed tools to existing list.
   */
  addAllowedTools(...tools: string[]): this {
    const currentTools = this.options.allowedTools ?? [];
    this.options.allowedTools = [...currentTools, ...tools];
    return this;
  }

  /**
   * Set disallowed tools.
   */
  disallowedTools(tools: string[]): this {
    this.options.disallowedTools = tools;
    return this;
  }

  /**
   * Set the system prompt.
   */
  systemPrompt(prompt: string): this {
    this.options.systemPrompt = prompt;
    return this;
  }

  /**
   * Set system prompt with preset.
   */
  systemPromptPreset(preset: string, append?: string): this {
    this.options.systemPrompt = { type: 'preset', preset, append };
    return this;
  }

  /**
   * Set the working directory.
   */
  cwd(cwd: string): this {
    this.options.cwd = cwd;
    return this;
  }

  /**
   * Set max turns.
   */
  maxTurns(turns: number): this {
    this.options.maxTurns = turns;
    return this;
  }

  /**
   * Set permission mode.
   */
  permissionMode(mode: 'default' | 'plan' | 'bypassPermissions' | 'acceptEdits'): this {
    this.options.permissionMode = mode;
    return this;
  }

  /**
   * Set max thinking tokens.
   */
  maxThinkingTokens(tokens: number): this {
    this.options.maxThinkingTokens = tokens;
    return this;
  }

  /**
   * Enable/disable partial message streaming.
   */
  includePartialMessages(include: boolean): this {
    this.options.includePartialMessages = include;
    return this;
  }

  /**
   * Set verbose mode.
   */
  verbose(verbose: boolean): this {
    this.options.verbose = verbose;
    return this;
  }

  /**
   * Add an MCP server.
   */
  addMcpServer(name: string, server: McpServerSpec): this {
    if (!this.options.mcpServers) {
      this.options.mcpServers = {};
    }
    this.options.mcpServers[name] = server;
    return this;
  }

  /**
   * Add MCP server specific tools to allowed list.
   */
  addMcpServerTools(serverName: string, ...tools: string[]): this {
    const mcpTools = tools.map((t) => `mcp__${serverName}__${t}`);
    return this.addAllowedTools(...mcpTools);
  }

  /**
   * Add MCP server wildcard tools permission.
   */
  addMcpServerWildcardTools(serverName: string): this {
    return this.addAllowedTools(`mcp__${serverName}__*`);
  }

  /**
   * Add security hooks configuration.
   */
  addSecurityHooks(
    dangerousPatterns: string[] = ['rm -rf', 'sudo', 'format', 'delete'],
    allowedCommands: string[] = []
  ): this {
    const hooks = securityHook(dangerousPatterns, allowedCommands);
    return this.mergeHooks(hooks);
  }

  /**
   * Add statistics hooks configuration.
   */
  addStatisticsHooks(): this {
    const hooks = statisticsHook();
    return this.mergeHooks(hooks);
  }

  /**
   * Add a custom hook.
   */
  addHook(event: HookEvent, matcher: string | undefined, callback: HookCallback): this {
    const hookMatcher: HookMatcher = { matcher, hooks: [callback] };
    const hooks: Partial<Record<HookEvent, HookMatcher[]>> = {
      [event]: [hookMatcher],
    };
    return this.mergeHooks(hooks);
  }

  /**
   * Add hooks using DSL builder.
   */
  addHooksDsl(init: (builder: ReturnType<typeof hookBuilder>) => void): this {
    const builder = hookBuilder();
    init(builder);
    const hooks = builder.build();
    return this.mergeHooks(hooks);
  }

  /**
   * Set environment variables.
   */
  env(env: Record<string, string>): this {
    this.options.env = env;
    return this;
  }

  /**
   * Add environment variables.
   */
  addEnv(key: string, value: string): this {
    if (!this.options.env) {
      this.options.env = {};
    }
    this.options.env[key] = value;
    return this;
  }

  /**
   * Set extra CLI arguments.
   */
  extraArgs(args: Record<string, string | undefined>): this {
    this.options.extraArgs = args;
    return this;
  }

  /**
   * Set CLI path.
   */
  cliPath(path: string): this {
    this.options.cliPath = path;
    return this;
  }

  /**
   * Set Node.js path.
   */
  nodePath(path: string): this {
    this.options.nodePath = path;
    return this;
  }

  /**
   * Enable/disable Chrome integration.
   */
  chromeEnabled(enabled: boolean): this {
    this.options.chromeEnabled = enabled;
    return this;
  }

  /**
   * Set timeout.
   */
  timeout(ms: number): this {
    this.options.timeout = ms;
    return this;
  }

  /**
   * Configure for continue conversation.
   */
  continueConversation(sessionId?: string): this {
    if (sessionId) {
      this.options.resume = sessionId;
    } else {
      this.options.continueConversation = true;
    }
    return this;
  }

  /**
   * Set raw options (for advanced use cases).
   */
  raw(options: Partial<ClaudeAgentOptions>): this {
    this.options = { ...this.options, ...options };
    return this;
  }

  /**
   * Merge hooks into existing hooks configuration.
   */
  private mergeHooks(newHooks: Partial<Record<HookEvent, HookMatcher[]>>): this {
    if (!this.options.hooks) {
      this.options.hooks = {};
    }

    for (const [event, matchers] of Object.entries(newHooks)) {
      const hookEvent = event as HookEvent;
      const existingMatchers = this.options.hooks[hookEvent] ?? [];
      this.options.hooks[hookEvent] = [...existingMatchers, ...(matchers ?? [])];
    }

    return this;
  }

  /**
   * Build the final options object.
   */
  build(): ClaudeAgentOptions {
    return { ...this.options };
  }
}

/**
 * Create a new options builder.
 *
 * @example
 * ```typescript
 * const options = optionsBuilder()
 *   .model('claude-sonnet-4-20250514')
 *   .addAllowedTools('Bash', 'Read')
 *   .build();
 * ```
 */
export function optionsBuilder(): OptionsBuilder {
  return new OptionsBuilder();
}

// ============================================================================
// Extension Functions (Standalone helpers)
// ============================================================================

/**
 * Add an MCP server to options.
 */
export function addMcpServer(
  options: ClaudeAgentOptions,
  name: string,
  server: McpServerSpec
): ClaudeAgentOptions {
  return {
    ...options,
    mcpServers: {
      ...options.mcpServers,
      [name]: server,
    },
  };
}

/**
 * Add security hooks to options.
 */
export function addSecurityHooksToOptions(
  options: ClaudeAgentOptions,
  dangerousPatterns: string[] = ['rm -rf', 'sudo', 'format', 'delete'],
  allowedCommands: string[] = []
): ClaudeAgentOptions {
  const hooks = securityHook(dangerousPatterns, allowedCommands);
  return mergeHooksToOptions(options, hooks);
}

/**
 * Add statistics hooks to options.
 */
export function addStatisticsHooksToOptions(options: ClaudeAgentOptions): ClaudeAgentOptions {
  const hooks = statisticsHook();
  return mergeHooksToOptions(options, hooks);
}

/**
 * Add a hook to options.
 */
export function addHookToOptions(
  options: ClaudeAgentOptions,
  event: HookEvent,
  matcher: string | undefined,
  callback: HookCallback
): ClaudeAgentOptions {
  const hookMatcher: HookMatcher = { matcher, hooks: [callback] };
  const hooks: Partial<Record<HookEvent, HookMatcher[]>> = {
    [event]: [hookMatcher],
  };
  return mergeHooksToOptions(options, hooks);
}

/**
 * Add allowed tools to options.
 */
export function addAllowedToolsToOptions(
  options: ClaudeAgentOptions,
  ...tools: string[]
): ClaudeAgentOptions {
  const currentTools = options.allowedTools ?? [];
  return {
    ...options,
    allowedTools: [...currentTools, ...tools],
  };
}

/**
 * Add MCP server tools to options.
 */
export function addMcpServerToolsToOptions(
  options: ClaudeAgentOptions,
  serverName: string,
  ...tools: string[]
): ClaudeAgentOptions {
  const mcpTools = tools.map((t) => `mcp__${serverName}__${t}`);
  return addAllowedToolsToOptions(options, ...mcpTools);
}

/**
 * Add MCP server wildcard tools to options.
 */
export function addMcpServerWildcardToolsToOptions(
  options: ClaudeAgentOptions,
  serverName: string
): ClaudeAgentOptions {
  return addAllowedToolsToOptions(options, `mcp__${serverName}__*`);
}

/**
 * Merge hooks into options.
 */
function mergeHooksToOptions(
  options: ClaudeAgentOptions,
  newHooks: Partial<Record<HookEvent, HookMatcher[]>>
): ClaudeAgentOptions {
  const currentHooks = options.hooks ?? {};
  const mergedHooks: Partial<Record<HookEvent, HookMatcher[]>> = { ...currentHooks };

  for (const [event, matchers] of Object.entries(newHooks)) {
    const hookEvent = event as HookEvent;
    const existingMatchers = mergedHooks[hookEvent] ?? [];
    mergedHooks[hookEvent] = [...existingMatchers, ...(matchers ?? [])];
  }

  return {
    ...options,
    hooks: mergedHooks,
  };
}
