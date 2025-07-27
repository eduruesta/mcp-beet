package com.mcpbeet.mcp

import com.mcpbeet.api.OddsApiClient
import com.mcpbeet.service.AnalysisService
import com.mcpbeet.model.BettingEvent
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun `run betting mcp server`() {
    val oddsApiClient = OddsApiClient()
    val analysisService = AnalysisService()
    
    val server = Server(
        Implementation(
            name = "mcp-beet",
            version = "1.0.0"
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )
    
    // Add betting analysis tools
    server.addTool(
        name = "compare-odds",
        description = "Compare odds across multiple sportsbooks for a specific sport and event",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key (e.g., basketball_nba, soccer_epl)")
                    }
                    putJsonObject("event_id") {
                        put("type", "string")
                        put("description", "Event ID to analyze")
                    }
                }
                putJsonArray("required") {
                    add("sport")
                    add("event_id")
                }
            }
        )
    ) { request ->
        val sport = request.arguments["sport"]?.jsonPrimitive?.content ?: ""
        val eventId = request.arguments["event_id"]?.jsonPrimitive?.content ?: ""
        
        try {
            val oddsData = oddsApiClient.getOdds(sport)
            val odds = oddsData.find { it.id == eventId }
            
            if (odds != null) {
                // Convert OddsData to BettingEvent
                val event = BettingEvent(
                    id = odds.id,
                    sport = odds.sportKey,
                    league = odds.sportTitle,
                    homeTeam = odds.homeTeam,
                    awayTeam = odds.awayTeam,
                    startTime = odds.commenceTime,
                    odds = mapOf(odds.id to odds)
                )
                val analysis = analysisService.analyzeEvent(event)
                CallToolResult(
                    content = listOf(
                        TextContent("Odds comparison analysis for $eventId:\n${analysis.joinToString("\n") { 
                            "Market: ${it.market}, Best: ${it.bestOdds.outcome} @ ${it.bestOdds.odds} (${it.bestOdds.bookmaker}), Recommendation: ${it.valueAnalysis.recommendation}"
                        }}")
                    )
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("Event not found: $eventId"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error comparing odds: ${e.message}"))
            )
        }
    }
    
    server.addTool(
        name = "find-arbitrage",
        description = "Find arbitrage betting opportunities across sportsbooks",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key to search for arbitrage opportunities")
                    }
                }
                putJsonArray("required") {
                    add("sport")
                }
            }
        )
    ) { request ->
        val sport = request.arguments["sport"]?.jsonPrimitive?.content ?: ""
        
        try {
            val oddsData = oddsApiClient.getOdds(sport)
            val arbitrageOpportunities = mutableListOf<String>()
            
            for (odds in oddsData) {
                // Convert OddsData to BettingEvent
                val event = BettingEvent(
                    id = odds.id,
                    sport = odds.sportKey,
                    league = odds.sportTitle,
                    homeTeam = odds.homeTeam,
                    awayTeam = odds.awayTeam,
                    startTime = odds.commenceTime,
                    odds = mapOf(odds.id to odds)
                )
                val analyses = analysisService.analyzeEvent(event)
                for (analysis in analyses) {
                    analysis.arbitrageOpportunity?.let { arb ->
                        if (arb.guaranteed && arb.profit > 0) {
                            arbitrageOpportunities.add(
                                "Event: ${event.homeTeam} vs ${event.awayTeam}, Market: ${analysis.market}, Profit: $${String.format("%.2f", arb.profit)}"
                            )
                        }
                    }
                }
            }
            
            CallToolResult(
                content = listOf(
                    TextContent(
                        if (arbitrageOpportunities.isNotEmpty()) {
                            "Found ${arbitrageOpportunities.size} arbitrage opportunities:\n${arbitrageOpportunities.joinToString("\n")}"
                        } else {
                            "No arbitrage opportunities found for $sport"
                        }
                    )
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error finding arbitrage: ${e.message}"))
            )
        }
    }
    
    // Get available sports
    server.addTool(
        name = "get-sports",
        description = "Get list of available sports for betting analysis",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
        )
    ) { request ->
        try {
            val sports = oddsApiClient.getSports()
            CallToolResult(
                content = listOf(
                    TextContent("Available sports:\n${sports.joinToString("\n") { 
                        "• ${it.title} (${it.key}) - ${if (it.active) "Active" else "Inactive"}"
                    }}")
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error fetching sports: ${e.message}"))
            )
        }
    }
    
    // Get upcoming events for a sport
    server.addTool(
        name = "get-events",
        description = "Get upcoming events for a specific sport",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key (e.g., basketball_nba, soccer_epl)")
                    }
                }
                putJsonArray("required") {
                    add("sport")
                }
            }
        )
    ) { request ->
        val sport = request.arguments["sport"]?.jsonPrimitive?.content ?: ""
        
        try {
            val events = oddsApiClient.getEvents(sport)
            CallToolResult(
                content = listOf(
                    TextContent("Upcoming events in $sport:\n${events.take(10).joinToString("\n") { 
                        "• ${it.home_team} vs ${it.away_team} - ${it.commence_time} (ID: ${it.id})"
                    }}")
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error fetching events: ${e.message}"))
            )
        }
    }
    
    // Get recent scores
    server.addTool(
        name = "get-scores",
        description = "Get recent scores and results for a sport",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key to get scores for")
                    }
                    putJsonObject("days_from") {
                        put("type", "integer")
                        put("description", "Number of days back to get scores (default: 3)")
                        put("default", 3)
                    }
                }
                putJsonArray("required") {
                    add("sport")
                }
            }
        )
    ) { request ->
        val sport = request.arguments["sport"]?.jsonPrimitive?.content ?: ""
        val daysFrom = request.arguments["days_from"]?.jsonPrimitive?.intOrNull ?: 3
        
        try {
            val scores = oddsApiClient.getScores(sport, daysFrom)
            CallToolResult(
                content = listOf(
                    TextContent("Recent scores in $sport:\n${scores.take(15).joinToString("\n") { 
                        val scoreText = it.scores?.joinToString(" - ") { score -> "${score.name}: ${score.score}" } ?: "No score"
                        "• ${it.home_team} vs ${it.away_team}: $scoreText ${if (it.completed) "(Final)" else "(Live)"}"
                    }}")
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error fetching scores: ${e.message}"))
            )
        }
    }
    
    // Best betting recommendations
    server.addTool(
        name = "best-bets",
        description = "Get best betting recommendations across all events in a sport based on value analysis",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key to analyze for best bets")
                    }
                    putJsonObject("min_confidence") {
                        put("type", "number")
                        put("description", "Minimum confidence level (0.0 to 1.0, default: 0.6)")
                        put("default", 0.6)
                    }
                }
                putJsonArray("required") {
                    add("sport")
                }
            }
        )
    ) { request ->
        val sport = request.arguments["sport"]?.jsonPrimitive?.content ?: ""
        val minConfidence = request.arguments["min_confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.6
        
        try {
            val oddsData = oddsApiClient.getOdds(sport)
            val allRecommendations = mutableListOf<String>()
            
            for (odds in oddsData.take(20)) { // Limit to avoid API quota issues
                val event = BettingEvent(
                    id = odds.id,
                    sport = odds.sportKey,
                    league = odds.sportTitle,
                    homeTeam = odds.homeTeam,
                    awayTeam = odds.awayTeam,
                    startTime = odds.commenceTime,
                    odds = mapOf(odds.id to odds)
                )
                
                val analyses = analysisService.analyzeEvent(event)
                for (analysis in analyses) {
                    val va = analysis.valueAnalysis
                    if (va.confidence >= minConfidence && (va.recommendation == "STRONG BUY" || va.recommendation == "BUY")) {
                        allRecommendations.add(
                            "🎯 ${event.homeTeam} vs ${event.awayTeam}\n" +
                            "   Market: ${analysis.market}\n" +
                            "   Best: ${analysis.bestOdds.outcome} @ ${analysis.bestOdds.odds} (${analysis.bestOdds.bookmaker})\n" +
                            "   Recommendation: ${va.recommendation}\n" +
                            "   Expected Value: ${String.format("%.2f%%", va.expectedValue * 100)}\n" +
                            "   Kelly: ${String.format("%.2f%%", va.kelly * 100)}\n" +
                            "   Confidence: ${String.format("%.1f%%", va.confidence * 100)}\n"
                        )
                    }
                }
            }
            
            CallToolResult(
                content = listOf(
                    TextContent(
                        if (allRecommendations.isNotEmpty()) {
                            "🏆 Best betting opportunities in $sport (confidence ≥ ${(minConfidence * 100).toInt()}%):\n\n${allRecommendations.take(10).joinToString("\n")}"
                        } else {
                            "No high-confidence betting opportunities found in $sport with the specified criteria."
                        }
                    )
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error analyzing best bets: ${e.message}"))
            )
        }
    }
    
    // Market analysis
    server.addTool(
        name = "market-analysis",
        description = "Detailed market analysis for a specific event showing all available markets and bookmakers",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key")
                    }
                    putJsonObject("event_id") {
                        put("type", "string")
                        put("description", "Event ID to analyze in detail")
                    }
                }
                putJsonArray("required") {
                    add("sport")
                    add("event_id")
                }
            }
        )
    ) { request ->
        val sport = request.arguments["sport"]?.jsonPrimitive?.content ?: ""
        val eventId = request.arguments["event_id"]?.jsonPrimitive?.content ?: ""
        
        try {
            val odds = oddsApiClient.getEventOdds(sport, eventId)
            
            if (odds != null) {
                val event = BettingEvent(
                    id = odds.id,
                    sport = odds.sportKey,
                    league = odds.sportTitle,
                    homeTeam = odds.homeTeam,
                    awayTeam = odds.awayTeam,
                    startTime = odds.commenceTime,
                    odds = mapOf(odds.id to odds)
                )
                
                val analysis = analysisService.analyzeEvent(event)
                val marketDetails = StringBuilder()
                
                marketDetails.append("📊 Detailed Market Analysis\n")
                marketDetails.append("Event: ${event.homeTeam} vs ${event.awayTeam}\n")
                marketDetails.append("Start Time: ${event.startTime}\n\n")
                
                for (analysisResult in analysis) {
                    marketDetails.append("🎯 ${analysisResult.market.uppercase()} MARKET\n")
                    marketDetails.append("Best Bet: ${analysisResult.bestOdds.outcome} @ ${analysisResult.bestOdds.odds} (${analysisResult.bestOdds.bookmaker})\n")
                    marketDetails.append("Recommendation: ${analysisResult.valueAnalysis.recommendation}\n")
                    marketDetails.append("Expected Value: ${String.format("%.2f%%", analysisResult.valueAnalysis.expectedValue * 100)}\n")
                    marketDetails.append("Kelly Stake: ${String.format("%.2f%%", analysisResult.valueAnalysis.kelly * 100)}\n")
                    
                    analysisResult.arbitrageOpportunity?.let { arb ->
                        if (arb.guaranteed) {
                            marketDetails.append("🚨 ARBITRAGE OPPORTUNITY: $${String.format("%.2f", arb.profit)} profit guaranteed!\n")
                        }
                    }
                    marketDetails.append("\n")
                }
                
                CallToolResult(
                    content = listOf(TextContent(marketDetails.toString()))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("Event not found: $eventId"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error analyzing market: ${e.message}"))
            )
        }
    }
    
    logger.info { "Starting MCP Beet Server..." }
    
    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered()
    )
    
    runBlocking {
        try {
            server.connect(transport)
            val done = Job()
            server.onClose {
                done.complete()
            }
            done.join()
        } catch (e: Exception) {
            logger.error(e) { "Failed to run MCP Beet Server" }
        }
    }
}