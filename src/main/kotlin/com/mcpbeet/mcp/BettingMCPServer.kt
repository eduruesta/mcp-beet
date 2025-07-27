package com.mcpbeet.mcp

import com.mcpbeet.api.OddsApiClient
import com.mcpbeet.service.AnalysisService
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.capabilities.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.transport.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Implementation
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class BettingMCPServer {
    private val oddsApiClient = OddsApiClient()
    private val analysisService = AnalysisService()
    
    private val server = Server(
        serverInfo = Implementation(
            name = "mcp-beet",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(
                    subscribe = true,
                    listChanged = true
                ),
                tools = ServerCapabilities.Tools(
                    listChanged = true
                ),
                prompts = ServerCapabilities.Prompts(
                    listChanged = true
                )
            )
        )
    )
    
    init {
        setupResources()
        setupTools()
        setupPrompts()
    }
    
    private fun setupResources() {
        // Current odds resource
        server.addResource(
            uri = "odds://current/{sport}",
            name = "Current Odds",
            description = "Get current odds for a specific sport",
            mimeType = "application/json"
        ) { request ->
            runBlocking {
                try {
                    val sport = extractSportFromUri(request.uri)
                    val odds = oddsApiClient.getOdds(sport)
                    io.modelcontextprotocol.kotlin.sdk.shared.ReadResourceResult(
                        contents = listOf(
                            io.modelcontextprotocol.kotlin.sdk.shared.TextResourceContents(
                                text = kotlinx.serialization.json.Json.encodeToString(
                                    kotlinx.serialization.serializer(),
                                    odds
                                ),
                                uri = request.uri,
                                mimeType = "application/json"
                            )
                        )
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to fetch odds for ${request.uri}" }
                    io.modelcontextprotocol.kotlin.sdk.shared.ReadResourceResult(
                        contents = listOf(
                            io.modelcontextprotocol.kotlin.sdk.shared.TextResourceContents(
                                text = "{\"error\": \"${e.message}\"}",
                                uri = request.uri,
                                mimeType = "application/json"
                            )
                        )
                    )
                }
            }
        }
        
        // Sports list resource
        server.addResource(
            uri = "odds://sports",
            name = "Available Sports",
            description = "List all available sports for betting odds",
            mimeType = "application/json"
        ) { request ->
            runBlocking {
                try {
                    val sports = oddsApiClient.getSports()
                    io.modelcontextprotocol.kotlin.sdk.shared.ReadResourceResult(
                        contents = listOf(
                            io.modelcontextprotocol.kotlin.sdk.shared.TextResourceContents(
                                text = kotlinx.serialization.json.Json.encodeToString(
                                    kotlinx.serialization.serializer(),
                                    sports
                                ),
                                uri = request.uri,
                                mimeType = "application/json"
                            )
                        )
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to fetch sports list" }
                    io.modelcontextprotocol.kotlin.sdk.shared.ReadResourceResult(
                        contents = listOf(
                            io.modelcontextprotocol.kotlin.sdk.shared.TextResourceContents(
                                text = "{\"error\": \"${e.message}\"}",
                                uri = request.uri,
                                mimeType = "application/json"
                            )
                        )
                    )
                }
            }
        }
    }
    
    private fun setupTools() {
        // Compare odds tool
        server.addTool(
            name = "compare-odds",
            description = "Compare odds across multiple bookmakers for a specific event"
        ) { request ->
            runBlocking {
                try {
                    val sport = request.arguments?.get("sport")?.toString() ?: "americanfootball_nfl"
                    val odds = oddsApiClient.getOdds(sport)
                    
                    val result = buildString {
                        appendLine("Odds comparison for $sport:")
                        for (event in odds.take(5)) { // Limit to 5 events
                            appendLine("\n${event.homeTeam} vs ${event.awayTeam}")
                            for (bookmaker in event.bookmakers) {
                                appendLine("  ${bookmaker.title}:")
                                for (market in bookmaker.markets) {
                                    if (market.key == "h2h") {
                                        appendLine("    ${market.outcomes.joinToString(", ") { "${it.name}: ${it.price}" }}")
                                    }
                                }
                            }
                        }
                    }
                    
                    io.modelcontextprotocol.kotlin.sdk.shared.CallToolResult(
                        content = listOf(
                            io.modelcontextprotocol.kotlin.sdk.shared.TextContent(
                                type = "text",
                                text = result
                            )
                        )
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to compare odds" }
                    io.modelcontextprotocol.kotlin.sdk.shared.CallToolResult(
                        content = listOf(
                            io.modelcontextprotocol.kotlin.sdk.shared.TextContent(
                                type = "text",
                                text = "Error comparing odds: ${e.message}"
                            )
                        )
                    )
                }
            }
        }
    }
    
    private fun setupPrompts() {
        server.addPrompt(
            name = "betting-strategy",
            description = "Generate betting strategy recommendations based on current odds and analysis"
        ) { request ->
            runBlocking {
                val sport = request.arguments?.get("sport")?.toString() ?: "americanfootball_nfl"
                val bankroll = request.arguments?.get("bankroll")?.toString()?.toDoubleOrNull() ?: 1000.0
                
                val strategy = generateBettingStrategy(sport, bankroll)
                
                io.modelcontextprotocol.kotlin.sdk.shared.GetPromptResult(
                    description = "Betting strategy for $sport with $${bankroll} bankroll",
                    messages = listOf(
                        io.modelcontextprotocol.kotlin.sdk.shared.PromptMessage(
                            role = io.modelcontextprotocol.kotlin.sdk.shared.Role.USER,
                            content = io.modelcontextprotocol.kotlin.sdk.shared.TextContent(
                                type = "text",
                                text = strategy
                            )
                        )
                    )
                )
            }
        }
    }
    
    private suspend fun generateBettingStrategy(sport: String, bankroll: Double): String {
        return try {
            val odds = oddsApiClient.getOdds(sport)
            buildString {
                appendLine("🎯 BETTING STRATEGY RECOMMENDATION")
                appendLine("Sport: $sport")
                appendLine("Bankroll: $${bankroll}")
                appendLine("Risk Management: Use 1-5% of bankroll per bet")
                appendLine("\nTop Opportunities:")
                
                // Simple analysis for demonstration
                odds.take(3).forEach { event ->
                    appendLine("\n📊 ${event.homeTeam} vs ${event.awayTeam}")
                    val bestBookmaker = event.bookmakers.maxByOrNull { it.markets.firstOrNull()?.outcomes?.maxOfOrNull { outcome -> outcome.price } ?: 0.0 }
                    if (bestBookmaker != null) {
                        appendLine("  Best odds at: ${bestBookmaker.title}")
                        appendLine("  Recommended stake: $${(bankroll * 0.02).toInt()}")
                    }
                }
            }
        } catch (e: Exception) {
            "Error generating strategy: ${e.message}"
        }
    }
    
    private fun extractSportFromUri(uri: String): String {
        // Extract sport from URI like "odds://current/soccer_epl"
        return uri.substringAfterLast("/")
    }
    
    suspend fun start() {
        logger.info { "Starting MCP Beet Server..." }
        val transport = StdioServerTransport()
        server.connect(transport)
    }
    
    fun close() {
        oddsApiClient.close()
    }
}