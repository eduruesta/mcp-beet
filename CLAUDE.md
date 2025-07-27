# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository contains a Kotlin-based MCP (Model Context Protocol) server for betting odds analysis and comparison. The server connects to various sports betting APIs to fetch real-time odds, compare them across multiple sportsbooks, and provide analysis for better betting decisions.

## Architecture

- **MCP Server**: Built using the Kotlin SDK for Model Context Protocol
- **API Integration**: Connects to multiple betting odds APIs (The Odds API, SportsDataIO, OpticOdds, etc.)
- **Analysis Engine**: Compares odds across different sportsbooks and calculates value bets
- **Transport**: Uses STDIO transport for communication with MCP clients

## Dependencies

The project uses Gradle with Kotlin DSL. Key dependencies include:

```kotlin
dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion") 
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
}
```

## Development Commands

### Build and Run
```bash
# Build the project
./gradlew build

# Run the MCP server
./gradlew run

# Build executable JAR
./gradlew shadowJar

# Run tests
./gradlew test
```

### Code Quality
```bash
# Kotlin code formatting
./gradlew ktlintFormat

# Code analysis
./gradlew detekt

# Check code style
./gradlew ktlintCheck
```

## MCP Server Configuration

The server exposes the following capabilities:

### Resources
- `odds://current/{sport}` - Current odds for a specific sport
- `odds://best-value/{sport}` - Best value bets analysis
- `odds://sportsbook/{name}` - Odds from specific sportsbook

### Tools
- `compare-odds` - Compare odds across multiple sportsbooks
- `analyze-bet` - Analyze a specific betting opportunity
- `find-arbitrage` - Find arbitrage betting opportunities
- `calculate-value` - Calculate expected value of a bet

### Prompts
- `betting-strategy` - Generate betting strategy recommendations
- `risk-analysis` - Analyze risk for a betting portfolio

## API Integration

### Supported Betting APIs
1. **The Odds API** - Primary source for live odds
2. **SportsDataIO** - US-focused odds and market data
3. **OpticOdds** - Real-time data from 200+ sportsbooks
4. **SportsGame Odds** - Prop bets and alternate markets

### API Configuration
API keys should be configured via environment variables:
```bash
export ODDS_API_KEY="your_odds_api_key"
export SPORTSDATA_API_KEY="your_sportsdata_key"
export OPTIC_ODDS_API_KEY="your_optic_odds_key"
```

## Core Components

### Data Models
- `OddsData` - Represents betting odds from a sportsbook
- `BettingEvent` - Sports event with associated markets
- `Analysis` - Betting analysis results
- `Arbitrage` - Arbitrage opportunity data

### Services
- `OddsService` - Fetches and normalizes odds data
- `AnalysisService` - Performs betting analysis calculations
- `ComparisonService` - Compares odds across sportsbooks
- `ArbitrageService` - Identifies arbitrage opportunities

### MCP Integration
- `BettingMCPServer` - Main MCP server implementation
- `OddsResourceProvider` - Provides odds data as MCP resources
- `BettingToolProvider` - Implements betting analysis tools

## Testing

The project includes comprehensive tests:
- Unit tests for core analysis algorithms
- Integration tests for API connections
- MCP protocol tests for server functionality

Run specific test suites:
```bash
# Unit tests only
./gradlew test --tests "*Unit*"

# Integration tests
./gradlew test --tests "*Integration*" 

# MCP protocol tests
./gradlew test --tests "*MCP*"
```

## Development Notes

- All odds data is cached for 1 minute to reduce API calls
- The server implements rate limiting to respect API constraints
- Analysis algorithms account for bookmaker margins and commission
- Support for both American and decimal odds formats
- Real-time odds updates via WebSocket connections where available

## Deployment

The server can be deployed as:
1. Standalone JAR application
2. Docker container
3. MCP server for Claude Code integration

For Claude Code integration, ensure the server is registered in your MCP configuration file.