/**
 * 审批相关类型
 *
 * 翻译自: codex-agent-sdk/.../appserver/AppServerTypes.kt 行 633-729
 */

// ============== Approval Decision ==============

export interface ExecPolicyAmendment {
  command: string[]
}

export interface ApprovalDecisionAccept {
  type: 'accept'
}

export interface ApprovalDecisionAcceptForSession {
  type: 'acceptForSession'
}

export interface ApprovalDecisionAcceptWithAmendment {
  type: 'acceptWithExecpolicyAmendment'
  execpolicyAmendment: ExecPolicyAmendment
}

export interface ApprovalDecisionDecline {
  type: 'decline'
}

export interface ApprovalDecisionCancel {
  type: 'cancel'
}

export type ApprovalDecision =
  | ApprovalDecisionAccept
  | ApprovalDecisionAcceptForSession
  | ApprovalDecisionAcceptWithAmendment
  | ApprovalDecisionDecline
  | ApprovalDecisionCancel

// ============== Command Approval ==============

export interface CommandExecutionRequestApprovalParams {
  threadId: string
  turnId: string
  itemId: string
  reason?: string
  proposedExecpolicyAmendment?: ExecPolicyAmendment
}

export interface CommandExecutionRequestApprovalResponse {
  decision: ApprovalDecision
}

// ============== File Change Approval ==============

export interface FileChangeRequestApprovalParams {
  threadId: string
  turnId: string
  itemId: string
  reason?: string
  grantRoot?: string
}

export interface FileChangeRequestApprovalResponse {
  decision: ApprovalDecision
}

// ============== ServerRequest (用于处理服务器审批请求) ==============

export interface ServerRequestBase {
  requestId: string
  rawId: unknown // 保留原始 id (整数或字符串)
}

export interface ServerRequestCommandApproval extends ServerRequestBase {
  type: 'commandApproval'
  params: CommandExecutionRequestApprovalParams
}

export interface ServerRequestFileChangeApproval extends ServerRequestBase {
  type: 'fileChangeApproval'
  params: FileChangeRequestApprovalParams
}

export type ServerRequest = ServerRequestCommandApproval | ServerRequestFileChangeApproval

// ============== Helper Functions ==============

export function encodeApprovalDecision(decision: ApprovalDecision): unknown {
  switch (decision.type) {
    case 'accept':
      return 'accept'
    case 'acceptForSession':
      return 'acceptForSession'
    case 'decline':
      return 'decline'
    case 'cancel':
      return 'cancel'
    case 'acceptWithExecpolicyAmendment':
      return {
        acceptWithExecpolicyAmendment: decision.execpolicyAmendment,
      }
  }
}
