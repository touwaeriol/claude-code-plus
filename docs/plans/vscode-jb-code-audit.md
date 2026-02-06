# VS Code vs JetBrains Code Audit Plan & Log

## Goal
- Verify VS Code extension parity with JetBrains implementation at module + file level.
- Record all behavioral/contract mismatches for follow-up fixes.

## Scope
- VS Code: `vscode-extension/src/**`
- JetBrains: `jetbrains-plugin/src/main/**`, `ai-agent-server/src/main/**`, `claude-agent-sdk/src/main/**`
- Frontend: `frontend/src/**` only when the contract is defined/consumed there.

## Method (module-first, file-by-file)
1. Use `docs/plans/vscode-module-comparison.md` as the base mapping.
2. For each module:
   - Open the mapped files (VS Code ↔ JB).
   - Compare behavior, API contract, and data shape.
   - Record any mismatch in **Findings** with evidence.
3. Mark file status as Reviewed only after explicit comparison.
4. Keep severity tags: High / Medium / Low.

## Audit Order
1. Server & Protocol (HTTP, RSocket)
2. SDK / CLI control protocol
3. IDE integrations (tools, history/rollback, terminal)
4. MCP servers and registry
5. Settings (storage + UI)
6. Remaining utilities/types

## Progress (module-level)
| Module | VS Code files | JetBrains files | Status | Notes |
|---|---|---|---|---|
| Server & Protocol | `vscode-extension/src/server/**` | `ai-agent-server/src/main/**` | In progress | HTTP/RSocket reviewed; WS/aux pending |
| SDK / CLI | `vscode-extension/src/sdk/claude/**` | `claude-agent-sdk/src/main/**` | Pending | Control protocol + message parsing |
| IDE Tools | `vscode-extension/src/ide/**` | `jetbrains-plugin/src/main/**` | Pending | Includes rollback/history |
| MCP | `vscode-extension/src/ide/mcp/**` | `jetbrains-plugin/src/main/**` | Pending | Tool parity + registry |
| Settings | `vscode-extension/src/ide/settings/**` + `webview-ui/**` | `jetbrains-plugin/src/main/**` | Pending | UI + storage |
| Utilities/Types | `vscode-extension/src/**` | `jetbrains-plugin/src/main/**` | Pending | Path, types, helpers |

## Reviewed Files (Server & Protocol)
| File | Status | Notes |
|---|---|---|
| `vscode-extension/src/server/HttpApiServer.ts` | Reviewed | HTTP endpoints + auth |
| `vscode-extension/src/server/apiHandlers.ts` | Reviewed | HTTP action RPC + file search |
| `vscode-extension/src/server/rsocket/agentRSocketServer.ts` | Reviewed | agent.* routes |
| `vscode-extension/src/server/rsocket/ideRSocketServer.ts` | Reviewed | ide.* routes |
| `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Reviewed | HTTP endpoints |
| `ai-agent-server/src/main/kotlin/com/asakii/server/rsocket/RSocketHandler.kt` | Reviewed | agent.* routes |
| `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt` | Reviewed | ide.* routes |

## Findings (confirmed)
| ID | Severity | Area | Description | VS Code | JetBrains | Status |
|---|---|---|---|---|---|---|
| F-001 | High | CLI control | VS Code returns error for `mcp_message`, but JB SDK parses/expects it from CLI startup. | `vscode-extension/src/sdk/claude/claudeCli.ts` | `claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/protocol/MessageParser.kt` + `ControlProtocol.kt` | Open |
| F-002 | High | Rollback | VS Code rollback snapshots are memory-only; JB uses LocalHistory and survives IDE restart. | `vscode-extension/src/ide/rollback/snapshotStore.ts` | `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/FileHistoryService.kt` | Open |
| F-003 | Medium | HTTP auth | VS Code HTTP server enforces `X-Claude-Code-Plus-Token`; JB HTTP does not enforce token at server level. | `vscode-extension/src/server/HttpApiServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Open |
| F-004 | Low | HTTP health | `/health` response shape differs (VS wraps in `{success,data}`; JB returns `{status,port}`). | `vscode-extension/src/server/HttpApiServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Open |
| F-005 | Medium | File search | `/api/files/search` may return directories + `isDirectory` in VS; JB returns files only and has no `isDirectory`. | `vscode-extension/src/server/apiHandlers.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Open |
| F-006 | Low | File search | JB returns `errorCode=INDEXING` when indexing; VS lacks equivalent error signaling. | `vscode-extension/src/server/apiHandlers.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Open |
| F-007 | Medium | History storage | VS history store is in-memory (restart loses sessions); JB history persists via JSONL/session files. | `vscode-extension/src/ide/history/historyStore.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Open |
| F-008 | Low | History API | Default `maxResults` differs (VS 50 vs JB 30); VS returns empty on missing sessionId where JB may return 400 (codex). | `vscode-extension/src/server/HttpApiServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Open |
| F-009 | Low | Backend availability | Codex availability check differs (VS uses `running`, JB checks provider not null). | `vscode-extension/src/server/HttpApiServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Open |
| F-010 | Low | Font API | Font source differs (VS best-effort system fonts; JB uses IDE/JBR fonts). | `vscode-extension/src/server/HttpApiServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | Open |
| F-011 | Medium | RSocket auth | VS `/rsocket` + `/ide-rsocket` require token + origin checks; JB RSocket has no equivalent auth gate. | `vscode-extension/src/server/rsocket/agentRSocketServer.ts` + `ideRSocketServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/rsocket/RSocketHandler.kt` + `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt` | Open |
| F-012 | Medium | agent.connect | VS returns hardcoded capabilities and does not return Codex threadId as sessionId; JB returns SDK capabilities and provider sessionId (threadId). | `vscode-extension/src/server/rsocket/agentRSocketServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/rpc/AiAgentRpcServiceImpl.kt` | Open |
| F-013 | Medium | agent.set* | VS `setSandboxMode` / `setMaxThinkingTokens` are no-ops; `setModel` not applied to Codex session options. | `vscode-extension/src/server/rsocket/agentRSocketServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/rsocket/RSocketHandler.kt` | Open |
| F-014 | Medium | agent.disconnect/dispose | VS only unregisters ClientCaller; CLI sessions and terminal sessions are not disposed. | `vscode-extension/src/server/rsocket/agentRSocketServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/rpc/AiAgentRpcServiceImpl.kt` | Open |
| F-015 | Low | agent.truncateHistory | VS ignores `projectPath` and uses in-memory history; JB uses projectPath in truncate. | `vscode-extension/src/server/rsocket/agentRSocketServer.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/rsocket/RSocketHandler.kt` | Open |
| F-016 | Medium | ide.reportSessionState | VS returns success without applying state; JB forwards state to `jetbrainsApi.session.receiveState`. | `vscode-extension/src/server/rsocket/ideRSocketServer.ts` | `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt` | Open |

## Notes
- Add new findings as they are discovered, with direct file references.
- Keep descriptions concise and action-oriented.
