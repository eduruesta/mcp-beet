package com.mcpbeet

import com.mcpbeet.mcp.BettingMCPServer
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

suspend fun main() {
    logger.info { "Starting MCP Beet Server..." }
    
    try {
        val server = BettingMCPServer()
        server.start()
    } catch (e: Exception) {
        logger.error(e) { "Failed to start MCP Beet Server" }
    }
}