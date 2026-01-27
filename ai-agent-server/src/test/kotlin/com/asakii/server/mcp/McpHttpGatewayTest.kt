package com.asakii.server.mcp

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpHttpGatewayTest {
    @Test
    fun buildServerUrl_hasNoQueryParams() {
        val url = McpHttpGateway.buildServerUrl("ide-file")
        val uri = URI(url)

        assertTrue(uri.query.isNullOrBlank())
        assertTrue(url.contains("/mcp/ide-file"))
        assertEquals("/mcp/ide-file", uri.path)
    }
}
