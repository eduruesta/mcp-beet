# MCP Beet - Betting Odds Analysis Server

A Model Context Protocol (MCP) server for analyzing sports betting odds and finding the best betting opportunities.

## Features

- 🔍 Compare odds across multiple sportsbooks
- 📊 Find arbitrage opportunities
- 💰 Calculate expected value and Kelly criterion
- 🎯 Generate betting strategy recommendations
- 🏆 Real-time odds from The Odds API

## Quick Start

1. **Set up API key:**
   ```bash
   export ODDS_API_KEY="5c88d13c45c678faa196d0cf16117ac2"
   ```

2. **Build and run:**
   ```bash
   ./gradlew build
   ./gradlew run
   ```

3. **Or create executable:**
   ```bash
   ./gradlew shadowJar
   java -jar build/libs/mcp-beet-server.jar
   ```

## API Configuration

Get your free API key from [The Odds API](https://the-odds-api.com/) and set it as an environment variable:

```bash
# Windows
set ODDS_API_KEY=your_api_key_here

# Linux/Mac
export ODDS_API_KEY=your_api_key_here
```

## MCP Integration

This server can be integrated with Claude Code or other MCP clients. Add to your MCP configuration:

```json
{
  "mcpServers": {
    "mcp-beet": {
      "command": "java",
      "args": ["-jar", "path/to/mcp-beet-server.jar"]
    }
  }
}
```

## Development

See [CLAUDE.md](./CLAUDE.md) for detailed development information.

## License

MIT