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
    
    // Live betting tracker
    server.addTool(
        name = "live-tracker",
        description = "Track live games with real-time scores and odds changes",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key to track live games")
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
            val scores = oddsApiClient.getScores(sport, daysFrom = 1)
            val liveGames = scores.filter { !it.completed }
            
            if (liveGames.isNotEmpty()) {
                val liveInfo = StringBuilder()
                liveInfo.append("🔴 LIVE GAMES in $sport:\n\n")
                
                for (game in liveGames.take(10)) {
                    liveInfo.append("⚽ ${game.home_team} vs ${game.away_team}\n")
                    val scoreText = game.scores?.joinToString(" - ") { "${it.name}: ${it.score}" } ?: "No score yet"
                    liveInfo.append("   Score: $scoreText\n")
                    liveInfo.append("   Started: ${game.commence_time}\n\n")
                }
                
                CallToolResult(content = listOf(TextContent(liveInfo.toString())))
            } else {
                CallToolResult(
                    content = listOf(TextContent("No live games currently in $sport"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error tracking live games: ${e.message}"))
            )
        }
    }
    
    // Bookmaker comparison
    server.addTool(
        name = "bookmaker-comparison",
        description = "Compare all bookmakers for a specific market across multiple events",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key to analyze")
                    }
                    putJsonObject("market") {
                        put("type", "string") 
                        put("description", "Market type: h2h, spreads, or totals")
                        put("default", "h2h")
                    }
                }
                putJsonArray("required") {
                    add("sport")
                }
            }
        )
    ) { request ->
        val sport = request.arguments["sport"]?.jsonPrimitive?.content ?: ""
        val market = request.arguments["market"]?.jsonPrimitive?.content ?: "h2h"
        
        try {
            val oddsData = oddsApiClient.getOdds(sport, markets = market)
            val bookmakersStats = mutableMapOf<String, MutableList<Double>>()
            
            // Collect all odds from all bookmakers
            for (odds in oddsData.take(20)) {
                for (bookmaker in odds.bookmakers) {
                    val targetMarket = bookmaker.markets.find { it.key == market }
                    targetMarket?.let { mkt ->
                        for (outcome in mkt.outcomes) {
                            val key = "${bookmaker.title} - ${outcome.name}"
                            bookmakersStats.getOrPut(key) { mutableListOf() }.add(outcome.price)
                        }
                    }
                }
            }
            
            val comparison = StringBuilder()
            comparison.append("📊 BOOKMAKER COMPARISON - $market market in $sport\n\n")
            
            val avgByBookmaker = bookmakersStats.map { (key, prices) ->
                val avg = prices.average()
                val count = prices.size
                Triple(key, avg, count)
            }.sortedByDescending { it.second }
            
            for ((key, avg, count) in avgByBookmaker.take(15)) {
                comparison.append("• $key: ${String.format("%.2f", avg)} avg (${count} events)\n")
            }
            
            CallToolResult(content = listOf(TextContent(comparison.toString())))
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error comparing bookmakers: ${e.message}"))
            )
        }
    }
    
    // Value hunting across multiple sports
    server.addTool(
        name = "value-hunter",
        description = "Hunt for value bets across multiple sports with advanced filtering. Example: sports can be 'basketball_nba,soccer_epl' or [\"basketball_nba\", \"soccer_epl\"]",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sports") {
                        put("type", "string")
                        put("description", "Comma-separated sports or JSON array: 'basketball_nba,soccer_epl' or [\"basketball_nba\", \"soccer_epl\"]")
                    }
                    putJsonObject("min_expected_value") {
                        put("type", "number")
                        put("description", "Minimum expected value (0.06 = 6%)")
                        put("default", 0.05)
                    }
                    putJsonObject("min_kelly") {
                        put("type", "number")
                        put("description", "Minimum Kelly criterion (0.02 = 2%)")
                        put("default", 0.02)
                    }
                }
                putJsonArray("required") {
                    add("sports")
                }
            }
        )
    ) { request ->
        val minEV = request.arguments["min_expected_value"]?.jsonPrimitive?.doubleOrNull ?: 0.05
        val minKelly = request.arguments["min_kelly"]?.jsonPrimitive?.doubleOrNull ?: 0.02
        
        // Handle multiple formats for sports input
        val sportsToAnalyze = try {
            // Try to parse as JSON array first
            request.arguments["sports"]?.jsonArray?.map { it.jsonPrimitive.content }
        } catch (_: Exception) {
            // If that fails, try to parse as string
            val sportsString = request.arguments["sports"]?.jsonPrimitive?.content
            if (sportsString != null) {
                try {
                    // Try parsing as JSON array string
                    val jsonElement = Json.parseToJsonElement(sportsString)
                    jsonElement.jsonArray.map { it.jsonPrimitive.content }
                } catch (_: Exception) {
                    // Fall back to comma-separated format
                    sportsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
            } else null
        }
        
        if (sportsToAnalyze == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("Error: Please provide sports as an array, e.g., [\"basketball_nba\", \"soccer_epl\"]"))
            )
        }
        
        try {
            val allValueBets = mutableListOf<String>()
            
            sportsToAnalyze.forEach { sport ->
                val oddsData = oddsApiClient.getOdds(sport)
                
                for (odds in oddsData.take(10)) { // Limit per sport to manage API quota
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
                        if (va.expectedValue >= minEV && va.kelly >= minKelly) {
                            allValueBets.add(
                                "💎 ${event.homeTeam} vs ${event.awayTeam} (${event.league})\n" +
                                "   Market: ${analysis.market} | Bet: ${analysis.bestOdds.outcome}\n" +
                                "   Odds: ${analysis.bestOdds.odds} @ ${analysis.bestOdds.bookmaker}\n" +
                                "   Expected Value: ${String.format("%.1f%%", va.expectedValue * 100)}\n" +
                                "   Kelly: ${String.format("%.1f%%", va.kelly * 100)}\n" +
                                "   Confidence: ${String.format("%.0f%%", va.confidence * 100)}\n"
                            )
                        }
                    }
                }
            }
            
            CallToolResult(
                content = listOf(
                    TextContent(
                        if (allValueBets.isNotEmpty()) {
                            "🏆 VALUE BETS ACROSS SPORTS (EV ≥ ${(minEV*100).toInt()}%, Kelly ≥ ${(minKelly*100).toInt()}%):\n\n${
                                allValueBets.take(15).joinToString("\n")
                            }"
                        } else {
                            "No value bets found meeting the specified criteria across the selected sports."
                        }
                    )
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error hunting for value: ${e.message}"))
            )
        }
    }
    
    // Quick event lookup
    server.addTool(
        name = "find-event",
        description = "Find events by team names or keywords",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("sport") {
                        put("type", "string")
                        put("description", "Sport key to search in")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Team name or keyword to search for")
                    }
                }
                putJsonArray("required") {
                    add("sport")
                    add("query")
                }
            }
        )
    ) { request ->
        val sport = request.arguments["sport"]?.jsonPrimitive?.content ?: ""
        val query = request.arguments["query"]?.jsonPrimitive?.content?.lowercase() ?: ""
        
        try {
            val events = oddsApiClient.getEvents(sport)
            val matchingEvents = events.filter { 
                it.home_team.lowercase().contains(query) || 
                it.away_team.lowercase().contains(query)
            }
            
            CallToolResult(
                content = listOf(
                    TextContent(
                        if (matchingEvents.isNotEmpty()) {
                            "🔍 Found ${matchingEvents.size} events matching '$query' in $sport:\n\n${
                                matchingEvents.take(10).joinToString("\n") { 
                                    "• ${it.home_team} vs ${it.away_team} - ${it.commence_time}\n  ID: ${it.id}"
                                }
                            }"
                        } else {
                            "No events found matching '$query' in $sport"
                        }
                    )
                )
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error finding events: ${e.message}"))
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