package com.mcpbeet.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
data class OddsData(
    val id: String,
    val sportKey: String,
    val sportTitle: String,
    val homeTeam: String,
    val awayTeam: String,
    val commenceTime: Instant,
    val bookmakers: List<Bookmaker>
)

@Serializable
data class Bookmaker(
    val key: String,
    val title: String,
    val lastUpdate: Instant,
    val markets: List<Market>
)

@Serializable
data class Market(
    val key: String, // h2h, spreads, totals
    val outcomes: List<Outcome>
)

@Serializable
data class Outcome(
    val name: String,
    val price: Double,
    val point: Double? = null // for spreads and totals
)

@Serializable
data class BettingEvent(
    val id: String,
    val sport: String,
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val startTime: Instant,
    val odds: Map<String, OddsData> // bookmaker key to odds
)

@Serializable
data class BettingAnalysis(
    val eventId: String,
    val market: String,
    val bestOdds: BestOdds,
    val arbitrageOpportunity: ArbitrageOpportunity? = null,
    val valueAnalysis: ValueAnalysis,
    val timestamp: Instant
)

@Serializable
data class BestOdds(
    val outcome: String,
    val bookmaker: String,
    val odds: Double,
    val impliedProbability: Double
)

@Serializable
data class ArbitrageOpportunity(
    val guaranteed: Boolean,
    val profit: Double,
    val stakes: Map<String, Double>, // bookmaker to stake amount
    val totalStake: Double
)

@Serializable
data class ValueAnalysis(
    val expectedValue: Double,
    val kelly: Double, // Kelly criterion
    val confidence: Double,
    val recommendation: String
)