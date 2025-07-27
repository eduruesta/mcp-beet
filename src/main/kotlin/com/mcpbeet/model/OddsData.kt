package com.mcpbeet.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.datetime.Instant

@Serializable
data class OddsData(
    val id: String,
    @SerialName("sport_key") val sportKey: String,
    @SerialName("sport_title") val sportTitle: String,
    @SerialName("home_team") val homeTeam: String,
    @SerialName("away_team") val awayTeam: String,
    @SerialName("commence_time") val commenceTime: Instant,
    val bookmakers: List<Bookmaker> = emptyList()
)

@Serializable
data class Bookmaker(
    val key: String,
    val title: String,
    @SerialName("last_update") val lastUpdate: Instant? = null,
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

@Serializable
data class ScoreData(
    val id: String,
    val sport_key: String,
    val sport_title: String,
    val commence_time: Instant,
    val completed: Boolean,
    val home_team: String,
    val away_team: String,
    val scores: List<Score>? = null,
    val last_update: Instant? = null
)

@Serializable
data class Score(
    val name: String,
    val score: String
)

@Serializable
data class EventData(
    val id: String,
    val sport_key: String,
    val sport_title: String,
    val commence_time: Instant,
    val home_team: String,
    val away_team: String
)

@Serializable
data class Participant(
    val key: String,
    val name: String
)