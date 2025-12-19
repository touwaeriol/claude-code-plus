# Changelog

All notable changes to this project will be documented in this file.

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
