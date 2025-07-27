package com.mcpbeet.api

import com.mcpbeet.model.OddsData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class OddsApiClient {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }
    
    private val apiKey = System.getenv("ODDS_API_KEY") 
        ?: throw IllegalStateException("ODDS_API_KEY environment variable not set")
    
    private val baseUrl = "https://api.the-odds-api.com/v4"
    
    suspend fun getSports(): List<Sport> {
        logger.info { "Fetching available sports..." }
        return httpClient.get("$baseUrl/sports") {
            parameter("api_key", apiKey)
        }.body()
    }
    
    suspend fun getOdds(
        sport: String,
        regions: String = "us,eu",
        markets: String = "h2h,spreads,totals",
        oddsFormat: String = "decimal"
    ): List<OddsData> {
        logger.info { "Fetching odds for sport: $sport" }
        return httpClient.get("$baseUrl/sports/$sport/odds") {
            parameter("api_key", apiKey)
            parameter("regions", regions)
            parameter("markets", markets)
            parameter("oddsFormat", oddsFormat)
        }.body()
    }
    
    suspend fun getEventOdds(
        sport: String,
        eventId: String,
        markets: String = "h2h,spreads,totals"
    ): OddsData? {
        logger.info { "Fetching odds for event: $eventId" }
        return try {
            httpClient.get("$baseUrl/sports/$sport/odds") {
                parameter("api_key", apiKey)
                parameter("eventIds", eventId)
                parameter("markets", markets)
            }.body<List<OddsData>>().firstOrNull()
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch event odds for $eventId" }
            null
        }
    }
    
    fun close() {
        httpClient.close()
    }
}

@kotlinx.serialization.Serializable
data class Sport(
    val key: String,
    val group: String,
    val title: String,
    val description: String,
    val active: Boolean,
    val has_outrights: Boolean
)