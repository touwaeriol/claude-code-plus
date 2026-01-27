// Source: ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiModels.kt
// Differences:
// - Uses TS interfaces instead of Kotlin data classes.

export interface FrontendRequest {
  action: string
  data?: any
}

export interface FrontendResponse {
  success: boolean
  data?: any
  error?: string
}
