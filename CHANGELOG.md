# Changelog

All notable changes to this project will be documented in this file.

## [1.2.2] - 2025-12-25

### Added

- Add `NodeNotFoundException` exception for better Node.js detection error handling
- Add `NODE_NOT_FOUND` and `CLI_NOT_FOUND` RSocket error codes for frontend error handling
- Auto-detect git-bash availability on Windows and set as default shell if installed

### Changed

- Improve Terminal MCP config dialog layout with larger instructions text area (10 rows)
- Improve logging configuration to auto-detect IDEA plugin environment
- Exclude Logback dependencies in jetbrains-plugin to use IDEA's built-in SLF4J implementation
- Logs now write to idea.log in production mode instead of separate log files

### Fixed

- Handle Node.js and CLI not found errors with user-friendly messages in frontend
- Improve RSocketSession error handling for environment configuration issues
- Fix Terminal MCP default shell type not working by using LocalTerminalCustomizer extension point
- Fix custom shell support in 242+ using TerminalTabState.myShellCommand
- Add version-specific compat layers for Terminal API
- Preserve browse scroll mode when switching chat tabs

---

## [1.2.1] - 2025-12-24

### Added

- Implement optimistic UI update for model selection (faster perceived response)

### Changed

#### Terminal MCP (IntelliJ 2025.3 Compatibility)
- Use public `TerminalToolWindowTabsManager` API for session management
- Activate Terminal ToolWindow when creating new sessions
- Use IntelliJ Logger instead of KotlinLogging in 253 TerminalCompat
- Unify TerminalCompat into main kotlin directory structure

### Fixed

- Fix nested `invokeAndWait` to prevent deadlock in IntelliJ 2025.3
- Sync custom models dynamically when IDE settings change

---

## [1.2.0] - 2025-12-24

### Added

#### Terminal MCP Enhancements
- Add `TerminalInterrupt` tool for stopping running commands (Ctrl+C signal)
- Support batch deletion in `TerminalKill` for closing multiple sessions at once
- Add foreground execution with output truncation support
- Display `session_id` for Terminal MCP tools in collapsed state
- Use SVG terminal icon for better visual consistency
- Add configurable read timeout with `timeout` parameter (default 30s, max 600s)
- Add interactive mode warnings in tool descriptions for potentially blocking commands

#### Chrome Extension Integration
- Add Chrome extension status UI and settings panel
- Add Chrome extension status indicator in bottom toolbar
- Add install button to Chrome Extension status popup
- Auto-reconnect when Chrome enabled state changes
- Sync Chrome setting to `~/.claude.json` for CLI compatibility

#### Git Integration
- Add Git MCP server with VCS tools and Chrome detector
- Add "Generate Commit Message" button with Claude AI integration
- Improve selected files support and switch to Markdown output
- Add Git Generate configuration with progress display
- Add session persistence control for Git Generate feature

#### Model Configuration
- Add custom model configuration support
- Make model configuration fully dynamic with custom model support

#### MCP Improvements
- Display MCP tool parameters in McpStatusPopup (matches CLI `/mcp` command)
- Add MCP reconnect, tools, disable/enable control endpoints
- Improve MCP settings and tool disabling functionality

#### Frontend Enhancements
- Add `ide.openUrl` API for opening URLs in system browser
- Add leafUuid and parentUuid support for message tree (conversation branching)

### Changed

#### Multi-Version Compatibility
- Eliminate reflection with multi-version compat layers and optional plugin deps
- Use `createLocalShellWidget` for 242/243 Terminal API compatibility
- Improve terminal tools and update compatibility layers for all supported versions
- Support universal plugin build for all platforms (242-253)

#### Terminal Session Management
- Simplify shell type parameter handling: replace `ShellType` enum with direct string shell names
- `createSession` accepts nullable `shellName`, uses `ShellResolver` for auto-detection
- 242 compat: temp modify shellPath with thread-safe locking
- Improve terminal session management with better state handling
- Remove output stability detection, add ApiUnavailable handling
- Simplify TerminalKill API to use session_ids only

#### Settings UI
- Improve MCP dialog UI scaling and simplify prompts

#### Frontend Refactoring
- Use self-closing attachment-start/end tags for better parsing
- Apply IDE default settings to new sessions automatically
- Align default thinking tokens with IDEA settings (8096)
- Save scroll position before tab switch for better UX

#### CLI Updates
- Upgrade CLI to 2.0.73 with terminal MCP and AST analysis tools
- Use `getAppState().mcp.tools` to match CLI internal `/mcp` command logic
- Simplify CLI patches and update enhanced bundle

### Fixed

- Fix 242/243 build compatibility by using correct Terminal API (`createLocalShellWidget`)
- Fix plugin-verifier: add `org.jetbrains.plugins.terminal` to externalPrefixes
- Fix plugin-verifier: add externalPrefixes to suppress optional dependency warnings
- Fix CI: pass platformMajor parameter to verify task for multi-version compatibility
- Fix git-generate event timing and use system-prompt-file correctly
- Fix missing `MODEL_CAPABILITIES` import in sessionStore
- Fix RSocket: remove timeout for permission and user question requests
- Fix missing `defaultChromeEnabled` field in decodeSettingsResponse
- Add generic font fallback to fix CSS warning
- Add `getChromeStatus` to UnifiedAgentClient interface to eliminate type check warning
- Fix UI display and SDK message content order separation
- Fix misc improvements to RSocket, MCP defaults, and session handling
- Properly load custom agents with enabled check and defaults
- Fix displayMessages watch timing after computed definition

### Build

- Support multi-version plugin distribution with dot separator (e.g., `1.2.0.242`)
- Use platform-suffixed version in pluginConfiguration for multi-version builds
- Support universal plugin build for all platforms (242-253)
- Add npm cache for faster frontend builds in CI
- Expand verification matrix to cover all 6 versions for each IDE (36 jobs total)
- Refactor verify jobs into groups with per-group IDE caching
- Limit max-parallel to 4 to prevent disk space exhaustion
- Enhance disk cleanup to free more space during CI builds
- Fix IDE types and versions to match build.gradle.kts

### Docs

- Add multi-version compat architecture documentation in CLAUDE.md
- Add best practices for terminal session reuse and cleanup
- Improve Terminal MCP tool descriptions for clarity

---

## [1.1.0] - 2025-12-19

### Added
- Add copy button to UserMessageBubble and CompactSummaryCard components
- Implement height-based overflow detection for auto-collapse in user messages
- Add gradient fade effect when message content is collapsed
- Upgrade CLI to 2.0.71 with run_in_background support for Bash commands
- Add Rename MCP tool for safe symbol refactoring across the project
- Add active file tracking with RSocket push notifications
- Add Node.js detection and fix CLI parameter passing
- Add session delete API and dynamic service config
- Add Chrome remote debugging for JCEF DevTools
- Add scroll boost for JCEF browser
- Add Context7 MCP server and improve custom agent loading
- Add file path bar to file search popup
- Implement configurable thinking levels with custom support
- Add streaming indicator with bouncing dots animation
- Add support for different editor types in active file detection
- Internationalization support for MCP and Agents config
- Full support for custom MCP server instructions
- Enforce JetBrains tools over Glob/Grep in system prompt
- AgentSettingsService for persistent plugin configuration
- IDE settings sync between backend and frontend
- Session list with delete button and better layout
- Tool window with refresh, settings buttons and JCEF fixes

### Changed
- Update Java version to 21
- Centralize file sync operations in IdeaPlatformService
- Migrate TextFieldWithBrowseButton to Kotlin UI DSL 2.0 API
- Move expand/collapse button to top-right corner for better UX
- Redesign MCP configuration with list-based UI (3-level to 2-level hierarchy)
- Remove dontAsk permission mode from frontend and backend
- Remove sequential-thinking MCP integration
- Compact tool cards and status indicators
- Use type-safe theme color APIs instead of hardcoded values
- Replace hand-written protobuf decoders with official library
- Use Element Plus tooltip for context tags
- Use CSS variables for editor font family across components
- Optimize MCP prompts and remove Chinese descriptions
- Improve streaming indicator and simplify session tabs
- Improve file tag display with compact design
- Enable dynamic plugin support

### Fixed
- Resolve IDEA 2024.2-2025.2 compatibility issues (CefBrowser.openDevTools NoSuchMethodError)
- Resolve WebStorm compatibility by using reflection for Java PSI classes
- Improve IntelliJ API compatibility for Diff editor and file chooser
- Use reflection for cross-version IntelliJ Diff API compatibility
- Suppress deprecated API warning for IDEA 2024.2 compatibility
- Wait for smart mode before MCP searching to ensure index freshness
- Resolve JSON parsing and BOM issues in control requests
- Trigger IDEA index refresh after file modifications
- Wrap HighlightVisitor callback in ReadAction for thread safety
- RSocket disconnect non-blocking for faster tab close
- RSocket timeout and force reconnect for interrupt request
- Resolve macOS CLI startup timeout issue
- Use JDialog for DevTools to ensure Windows compatibility
- Use login shell and file references for Claude CLI execution
- Resolve dynamic/static import mixing warnings in Vite build
- Sync skipPermissions setting to current session tab
- Improve scroll behavior detection for user interaction
- Improve message collapse behavior and fix DynamicScroller overlap
- Handle thinking block completion via syncThinkingSignatures
- Thinking auto-collapse and user message expand logic
- Display compact summary card after compaction
- Parse file references and current-open-file in replay messages
- Isolate streaming timer and active file dismiss state per tab
- Handle token usage for both streaming and non-streaming modes
- Include cacheCreationTokens in input token calculation
- Preserve original order of content items in pending message queue
- Preserve input and attachments on session reset
- Ensure IDE settings loaded before creating new session
- Fix clicks blocked by context menu overlay
- Fix shallowRef reactivity issue in isGenerating state

### Performance
- Optimize patchCli task to skip when enhanced CLI is up-to-date
- Save only target file instead of all documents (faster file sync)
- Replace blocking calls with non-blocking coroutines

### Build
- Add java-library plugin to ai-agent-proto for proper OrBuilder exposure
- Remove cleanCli dependency from clean task to preserve committed CLI files
- Explicitly declare protobuf-java as api dependency for OrBuilder interfaces
- Add comprehensive IDE verification matrix for CI
- Bump IntelliJ Platform version to 2025.3.1

### Docs
- Add new screenshots to all README versions
- Add Claude Code Plus usage guide for promotion
- Clarify Rename tool usage requires line location

## [1.0.9] - 2025-12-16

### Added
- Context size snapshot display on user messages (shows input tokens at send time)
- Dynamic plugin support declaration (require-restart="false")

### Changed
- Centralize version and changelog in gradle.properties and CHANGELOG.md
- Exit edit mode immediately before resending message for better UX

### Fixed
- Fix inline editor not closing after edit-and-resend
- Fix bundled CLI filename (ast-enhanced -> enhanced)

## [1.0.8] - 2025-12-15

### Added
- Dynamic MCP tool allowlist for flexible tool permissions
- SchemaValidator for MCP tool schema validation
- ResourceLoaderTest for resource loading validation
- Edit-and-resend message functionality improvements

### Changed
- Improve UserInteractionMcpServer with better question handling
- Enhance MCP tools (CodeSearch, DirectoryTree, FileProblems)
- Improve HistorySessionAction and SessionTabsAction UI
- Update tools.json schema definitions
- Session tabs animation changed from pulse to spinner effect
- Allow closing the last session tab (resets instead of removes)
- Simplify session architecture and improve history UI
- Hide edit button during streaming response

### Fixed
- Avoid calling override-only actionPerformed method directly
- Correct Logger.warn to Logger.warning
- Resolve compiler warnings in jetbrains-plugin
- Escape agents JSON for Windows command line
- Resolve IntelliJ 253 compatibility issues with optional Java plugin

### Removed
- Remove deprecated ClaudeActionHandler and SessionActionHandler
- Remove obsolete IdeActionBridge and IdeActionBridgeImpl
- Remove unused AgentDefinitionsProvider and ClaudeSessionManager
- Remove deprecated ClaudeCodeOptions typealias

## [1.0.7] - 2025-12-15

### Added
- Full Plan Mode support for implementation planning workflow
- JetBrains IDE session command and theme change notifications
- Collapsible thinking display and force send functionality
- System prompt appendix support for MCP servers
- History sessions with custom titles and enhanced UI
- RSocket connection management improvements
- JetBrains MCP Server integration

### Changed
- Migrate RPC calls from JSON to Protobuf serialization
- Update Claude CLI to 2.0.69
- Simplify project instructions (AGENTS.md, CLAUDE.md)
- Improve token stats display with cumulative statistics
- Improve error handling and interrupt message styling

### Fixed
- Register JetBrains MCP Server when client connects
- Prevent auto-scroll from overriding user scroll during streaming
- Replace internal API StartupUiUtil.isDarkTheme with public JBColor.isBright()
- Session ID resume on auto-reconnect
- Windows argument quoting for tool names with special characters

### SDK
- Add appendSystemPromptFile option for MCP system prompt appendix
- Switch to Node.js direct execution of official bundled cli.js (no longer fallback to global CLI)
- Improve system prompt temp file organization in dedicated subdirectory

## [1.0.6] - 2025-12-14

### Added
- Multi-language README support (English/Chinese)
- Scroll-to-bottom button visibility improvements during streaming

### Changed
- Major UI/UX improvements and codebase refactoring
- Replace hardcoded Chinese text with English in UI components
- Tool IDEA actions now triggered only on user click
- i18n improvements for slash commands

### Fixed
- Dropdown menu theme adaptation
- Gradle task dependencies and production build optimization

### Docs
- Update system requirements to clarify bundled CLI
- Update screenshots and descriptions

## [1.0.5] - 2025-12-10

Initial stable release with core features:
- Claude AI integration in IntelliJ IDEA
- Chat interface with streaming responses
- File operations (Read, Write, Edit)
- Terminal command execution
- MCP server support
- Theme synchronization with IDE
