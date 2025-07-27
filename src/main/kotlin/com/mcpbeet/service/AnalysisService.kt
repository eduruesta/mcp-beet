package com.mcpbeet.service

import com.mcpbeet.model.*
import kotlinx.datetime.Clock
import mu.KotlinLogging
import kotlin.math.ln
import kotlin.math.max

private val logger = KotlinLogging.logger {}

class AnalysisService {
    
    fun analyzeEvent(event: BettingEvent): List<BettingAnalysis> {
        logger.info { "Analyzing event: ${event.homeTeam} vs ${event.awayTeam}" }
        
        val analyses = mutableListOf<BettingAnalysis>()
        
        // Analyze each market type (h2h, spreads, totals)
        val marketTypes = setOf("h2h", "spreads", "totals")
        
        for (marketType in marketTypes) {
            val marketAnalysis = analyzeMarket(event, marketType)
            if (marketAnalysis != null) {
                analyses.add(marketAnalysis)
            }
        }
        
        return analyses
    }
    
    private fun analyzeMarket(event: BettingEvent, marketType: String): BettingAnalysis? {
        val oddsForMarket = event.odds.values.mapNotNull { oddsData ->
            oddsData.bookmakers.mapNotNull { bookmaker ->
                bookmaker.markets.find { it.key == marketType }?.let { market ->
                    bookmaker.key to market
                }
            }
        }.flatten().toMap()
        
        if (oddsForMarket.isEmpty()) {
            logger.warn { "No odds found for market: $marketType in event ${event.id}" }
            return null
        }
        
        val bestOdds = findBestOdds(oddsForMarket)
        val arbitrageOpportunity = findArbitrageOpportunity(oddsForMarket)
        val valueAnalysis = calculateValueAnalysis(oddsForMarket, bestOdds)
        
        return BettingAnalysis(
            eventId = event.id,
            market = marketType,
            bestOdds = bestOdds,
            arbitrageOpportunity = arbitrageOpportunity,
            valueAnalysis = valueAnalysis,
            timestamp = Clock.System.now()
        )
    }
    
    private fun findBestOdds(oddsForMarket: Map<String, Market>): BestOdds {
        var bestBookmaker = ""
        var bestOutcome = ""
        var bestOddsValue = 0.0
        
        for ((bookmaker, market) in oddsForMarket) {
            for (outcome in market.outcomes) {
                if (outcome.price > bestOddsValue) {
                    bestOddsValue = outcome.price
                    bestBookmaker = bookmaker
                    bestOutcome = outcome.name
                }
            }
        }
        
        return BestOdds(
            outcome = bestOutcome,
            bookmaker = bestBookmaker,
            odds = bestOddsValue,
            impliedProbability = 1.0 / bestOddsValue
        )
    }
    
    private fun findArbitrageOpportunity(oddsForMarket: Map<String, Market>): ArbitrageOpportunity? {
        // Collect all outcomes and their best odds across bookmakers
        val outcomeOdds = mutableMapOf<String, Pair<String, Double>>() // outcome -> (bookmaker, odds)
        
        for ((bookmaker, market) in oddsForMarket) {
            for (outcome in market.outcomes) {
                val currentBest = outcomeOdds[outcome.name]
                if (currentBest == null || outcome.price > currentBest.second) {
                    outcomeOdds[outcome.name] = bookmaker to outcome.price
                }
            }
        }
        
        if (outcomeOdds.size < 2) return null
        
        // Calculate total implied probability
        val totalImpliedProb = outcomeOdds.values.sumOf { 1.0 / it.second }
        
        if (totalImpliedProb >= 1.0) return null // No arbitrage opportunity
        
        // Calculate stakes for each outcome (assuming $100 total stake)
        val totalStake = 100.0
        val stakes = mutableMapOf<String, Double>()
        
        for ((outcome, bookmakerOdds) in outcomeOdds) {
            val stake = (totalStake / bookmakerOdds.second) / totalImpliedProb
            stakes[bookmakerOdds.first] = stake
        }
        
        val profit = totalStake * (1.0 - totalImpliedProb)
        
        return ArbitrageOpportunity(
            guaranteed = true,
            profit = profit,
            stakes = stakes,
            totalStake = totalStake
        )
    }
    
    private fun calculateValueAnalysis(
        oddsForMarket: Map<String, Market>,
        bestOdds: BestOdds
    ): ValueAnalysis {
        // Calculate average implied probability across all bookmakers
        val allProbabilities = mutableListOf<Double>()
        
        for ((_, market) in oddsForMarket) {
            for (outcome in market.outcomes) {
                if (outcome.name == bestOdds.outcome) {
                    allProbabilities.add(1.0 / outcome.price)
                }
            }
        }
        
        val avgProbability = allProbabilities.average()
        val trueProbability = avgProbability * 0.95 // Adjust for overround
        
        // Calculate expected value
        val expectedValue = (bestOdds.odds * trueProbability) - 1.0
        
        // Kelly criterion: f = (bp - q) / b
        // where b = odds - 1, p = true probability, q = 1 - p
        val b = bestOdds.odds - 1.0
        val p = trueProbability
        val q = 1.0 - p
        val kelly = max(0.0, (b * p - q) / b)
        
        val confidence = calculateConfidence(allProbabilities)
        val recommendation = generateRecommendation(expectedValue, kelly, confidence)
        
        return ValueAnalysis(
            expectedValue = expectedValue,
            kelly = kelly,
            confidence = confidence,
            recommendation = recommendation
        )
    }
    
    private fun calculateConfidence(probabilities: List<Double>): Double {
        if (probabilities.size < 2) return 0.0
        
        val mean = probabilities.average()
        val variance = probabilities.map { (it - mean) * (it - mean) }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        
        // Lower standard deviation = higher confidence
        return max(0.0, 1.0 - (standardDeviation * 2.0))
    }
    
    private fun generateRecommendation(
        expectedValue: Double,
        kelly: Double,
        confidence: Double
    ): String {
        return when {
            expectedValue > 0.1 && kelly > 0.05 && confidence > 0.7 -> "STRONG BUY"
            expectedValue > 0.05 && kelly > 0.02 && confidence > 0.5 -> "BUY"
            expectedValue > 0.0 && confidence > 0.3 -> "WEAK BUY"
            expectedValue < -0.05 -> "AVOID"
            else -> "HOLD"
        }
    }
}