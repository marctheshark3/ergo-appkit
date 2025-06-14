# Ergo Storage Rent Bot

A comprehensive bot for claiming storage rent fees on the Ergo blockchain using the Ergo AppKit. This bot automatically identifies boxes eligible for storage rent collection and creates transactions to claim the fees.

## Overview

According to Ergo's storage rent mechanism, boxes that are older than 4 years (approximately 1,051,200 blocks) can have their storage rent fees collected by miners. This bot automates the process of finding such boxes and claiming the rent fees.

### Key Features

- **Automated Scanning**: Continuously scans the blockchain for eligible boxes
- **Intelligent Filtering**: Filters boxes by profitability and other criteria
- **Batch Processing**: Handles multiple boxes in single transactions for efficiency
- **Safety Features**: Dry-run mode, configurable thresholds, and comprehensive logging
- **Explorer Integration**: Uses Ergo Explorer API for efficient box discovery
- **Detailed Analytics**: Provides comprehensive statistics and reporting

## Storage Rent Mechanism

### How Storage Rent Works

1. **Storage Period**: Boxes become eligible for rent collection after 4 years (1,051,200 blocks)
2. **Fee Calculation**: Rent fee = Box size (bytes) × Storage fee factor (nanoERG/byte)
3. **Default Parameters**:
   - Storage fee factor: 1,250,000 nanoERG/byte
   - Average box size: ~105 bytes
   - Estimated rent per standard box: ~0.13 ERG

### Storage Rent Collection Process

When a box is eligible for storage rent:
1. The original box is spent as input
2. A new box is created with the same content but reduced value
3. The difference (storage rent fee) goes to the miner
4. All registers and tokens are preserved (except R0 value and R3 creation info)

## Installation and Setup

### Prerequisites

- Java 8 or higher
- Ergo node running locally or access to remote node
- Wallet with mnemonic phrase for signing transactions
- Internet connection for Explorer API access

### Dependencies

Add the following dependencies to your project:

```xml
<dependency>
    <groupId>org.ergoplatform</groupId>
    <artifactId>ergo-appkit_2.12</artifactId>
    <version>4.0.9</version>
</dependency>
```

### Environment Configuration (Recommended)

The bot now supports environment-based configuration using a `.env` file for better security:

1. **Create your environment file**:
   ```bash
   cp .env.example .env
   ```

2. **Edit `.env` with your settings**:
   ```bash
   # Wallet Configuration (REQUIRED)
   ERGO_MNEMONIC=your twelve word mnemonic phrase goes here
   ERGO_PASSWORD=
   
   # Node Configuration
   ERGO_NODE_URL=http://127.0.0.1:9053
   ERGO_NETWORK=TESTNET
   
   # Bot Configuration
   STORAGE_RENT_DRY_RUN=true
   STORAGE_RENT_SCAN_INTERVAL_MINUTES=60
   STORAGE_RENT_MIN_THRESHOLD_NANOERG=100000000
   STORAGE_RENT_MAX_BOXES_PER_TX=50
   ```

3. **Use the configuration**:
   ```java
   // Configuration is automatically loaded from .env file
   StorageRentBot.Config config = new StorageRentBot.Config();
   StorageRentBot bot = new StorageRentBot(config);
   bot.start();
   ```

### Manual Configuration (Legacy)

You can still configure manually if needed:

```java
StorageRentBot.Config config = new StorageRentBot.Config()
    .setNodeUrl("http://127.0.0.1:9053")           // Ergo node URL
    .setNetworkType(NetworkType.MAINNET)           // Network type
    .setWalletMnemonic("your mnemonic here")       // Wallet mnemonic
    .setWalletPassword("")                         // Wallet password (if any)
    .setScanIntervalMinutes(60)                    // Scan every hour
    .setMinRentThreshold(100000000L)               // 0.1 ERG minimum
    .setMaxBoxesPerTransaction(50)                 // Max boxes per tx
    .setDryRun(true);                             // Dry run mode
```

## Usage

### Quick Start with Examples

The easiest way to get started is using the provided example file:

```java
// Run the basic example (dry-run mode)
java -cp your-classpath org.ergoplatform.appkit.examples.StorageRentBotExample
```

### Basic Usage

```java
// Create and configure the bot
StorageRentBot.Config config = new StorageRentBot.Config()
    .setWalletMnemonic("your twelve word mnemonic phrase here")
    .setDryRun(true) // ALWAYS start with dry-run mode
    .setNetworkType(NetworkType.TESTNET) // Use testnet for testing
    .setMinRentThreshold(50000000L) // 0.05 ERG minimum
    .setUseExplorerForScanning(true); // Use Explorer API for efficient scanning

StorageRentBot bot = new StorageRentBot(config);

// Start the bot
bot.start();

// The bot will run continuously until stopped
// Use Ctrl+C or call bot.stop() to stop
```

### Production Usage

**⚠️ IMPORTANT: Only use production mode after thorough testing!**

```java
StorageRentBot.Config config = new StorageRentBot.Config()
    .setNodeUrl("http://127.0.0.1:9053")
    .setNetworkType(NetworkType.MAINNET)
    .setWalletMnemonic(System.getenv("ERGO_MNEMONIC")) // Use environment variables
    .setWalletPassword(System.getenv("ERGO_PASSWORD"))
    .setScanIntervalMinutes(60)
    .setMinRentThreshold(100000000L) // 0.1 ERG minimum
    .setMaxBoxesPerTransaction(50)
    .setDryRun(false) // LIVE MODE - BE CAREFUL!
    .setUseExplorerForScanning(true)
    .setMaxTransactionFee(5000000L); // 0.005 ERG max fee

// Validate configuration before starting
if (StorageRentBotExample.validateConfig(config)) {
    StorageRentBot bot = new StorageRentBot(config);
    bot.start();
}
```

### Advanced Scanning

For more control over the scanning process, use the `StorageRentScanner`:

```java
ErgoClient ergoClient = RestApiErgoClient.create(nodeUrl, NetworkType.MAINNET, "", "");

ergoClient.execute(ctx -> {
    StorageRentScanner scanner = new StorageRentScanner(ctx, "https://api.ergoplatform.com");
    
    StorageRentScanner.ScanConfig scanConfig = new StorageRentScanner.ScanConfig()
        .setMaxBoxesToScan(1000)
        .setMinRentThreshold(50000000L)  // 0.05 ERG
        .setIncludeTokenBoxes(true)
        .setSortByRentDesc(true);
    
    StorageRentScanner.ScanResults results = scanner.scanForEligibleBoxes(scanConfig);
    results.printSummary();
    
    return null;
});
```

## Configuration Options

### Bot Configuration

| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| `nodeUrl` | Ergo node URL | `http://127.0.0.1:9053` | `http://your-node:9053` |
| `networkType` | Network type | `MAINNET` | `TESTNET` |
| `walletMnemonic` | Wallet mnemonic phrase | Required | `"word1 word2 ... word12"` |
| `walletPassword` | Wallet password | `""` | `"password"` |
| `scanIntervalMinutes` | Scan frequency | `60` | `30` (every 30 min) |
| `minRentThreshold` | Minimum rent to claim | `100000000L` | `50000000L` (0.05 ERG) |
| `maxBoxesPerTransaction` | Max boxes per tx | `50` | `25` |
| `dryRun` | Test mode (no actual txs) | `false` | `true` |

### Scanner Configuration

| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| `maxBoxesToScan` | Maximum boxes to analyze | `1000` | `500` |
| `minValueThreshold` | Minimum box value | `1000000L` | `5000000L` (0.005 ERG) |
| `minRentThreshold` | Minimum rent fee | `100000000L` | `50000000L` (0.05 ERG) |
| `includeTokenBoxes` | Include boxes with tokens | `true` | `false` |
| `sortByRentDesc` | Sort by rent descending | `true` | `false` |
| `maxPages` | Max API pages to scan | `10` | `5` |

## Safety Features

### Dry Run Mode

Always test with dry run mode first:

```java
config.setDryRun(true);
```

In dry run mode, the bot will:
- Scan for eligible boxes
- Calculate potential rent fees
- Log what it would do
- **NOT** send any transactions

### Minimum Thresholds

Set appropriate minimum thresholds to ensure profitability:

```java
config.setMinRentThreshold(100000000L); // Only claim if rent >= 0.1 ERG
```

### Transaction Limits

Limit the number of boxes per transaction to avoid large transaction fees:

```java
config.setMaxBoxesPerTransaction(50); // Max 50 boxes per transaction
```

## Monitoring and Logging

The bot provides comprehensive logging and monitoring:

### Status Reports

The bot automatically reports status every 30 minutes:

```
=== Storage Rent Bot Status ===
Running: true
Total rent claimed: 1500000000 nanoERG (1.5 ERG)
Total boxes processed: 12
Miner address: 9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v
==============================
```

### Scan Results

Detailed scan results show:

```
=== Storage Rent Scan Results ===
Total boxes scanned: 500
Eligible boxes found: 8
Total rent available: 1200000000 nanoERG (1.2 ERG)
Total value in eligible boxes: 15000000000 nanoERG (15.0 ERG)
Boxes with tokens: 3
Scan duration: 2500 ms
Average rent per eligible box: 150000000 nanoERG
================================
```

## Filtering and Analysis

### Built-in Filters

Use the `ResultsFilter` class for advanced filtering:

```java
// Filter by minimum rent
List<DetailedRentEligibleBox> highValueBoxes = ResultsFilter.filterByMinRent(
    results.getEligibleBoxes(), 500000000L); // 0.5 ERG minimum

// Filter by age
List<DetailedRentEligibleBox> veryOldBoxes = ResultsFilter.filterByMinAge(
    results.getEligibleBoxes(), STORAGE_RENT_PERIOD_BLOCKS * 2); // 8+ years

// Get top profitable boxes
List<DetailedRentEligibleBox> top10 = ResultsFilter.topByRent(
    results.getEligibleBoxes(), 10);

// Filter token boxes
List<DetailedRentEligibleBox> ergOnlyBoxes = ResultsFilter.filterTokenBoxes(
    results.getEligibleBoxes(), false); // Exclude token boxes
```

## Economics and Profitability

### Rent Calculation

Storage rent fee is calculated as:
```
Rent Fee = Box Size (bytes) × Storage Fee Factor (nanoERG/byte)
```

Current mainnet parameters:
- Storage fee factor: 1,250,000 nanoERG/byte
- Average box size: ~105 bytes
- Average rent: ~131,250,000 nanoERG (~0.13 ERG)

### Profitability Considerations

1. **Transaction Fees**: Each transaction costs at least 1,000,000 nanoERG (0.001 ERG)
2. **Batch Efficiency**: Processing multiple boxes in one transaction improves profitability
3. **Box Selection**: Focus on boxes with higher rent fees
4. **Network Congestion**: Higher fees during busy periods

### Example Profitability Analysis

```
Single Box:
- Rent fee: 131,250,000 nanoERG (0.13125 ERG)
- Transaction fee: 1,000,000 nanoERG (0.001 ERG)
- Net profit: 130,250,000 nanoERG (0.13025 ERG)

Batch of 50 Boxes:
- Total rent: 6,562,500,000 nanoERG (6.5625 ERG)
- Transaction fee: 1,000,000 nanoERG (0.001 ERG)
- Net profit: 6,561,500,000 nanoERG (6.5615 ERG)
- Profit per box: 131,230,000 nanoERG (0.13123 ERG)
```

## Troubleshooting

### Common Issues

1. **No eligible boxes found**
   - Check if boxes are actually old enough (4+ years)
   - Lower the minimum rent threshold
   - Increase the scan range

2. **Transaction failures**
   - Ensure wallet has enough ERG for transaction fees
   - Check if boxes are still unspent
   - Verify network connectivity

3. **API rate limiting**
   - Reduce scan frequency
   - Decrease max pages per scan
   - Add delays between requests

### Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Not enough funds" | Insufficient ERG for fees | Add ERG to wallet |
| "Box already spent" | Box spent by another tx | Normal, continue scanning |
| "API rate limit" | Too many API requests | Reduce scan frequency |
| "Invalid mnemonic" | Wrong wallet phrase | Check mnemonic phrase |

## Security Considerations

### Wallet Security

1. **Mnemonic Protection**: Never hardcode mnemonic in source code
2. **Environment Variables**: Use environment variables for sensitive data
3. **Limited Funds**: Use a dedicated wallet with limited funds
4. **Regular Monitoring**: Monitor bot activity and wallet balance

### Network Security

1. **Node Security**: Use trusted Ergo nodes
2. **API Endpoints**: Use official Explorer API endpoints
3. **Rate Limiting**: Respect API rate limits
4. **Error Handling**: Implement proper error handling

## Advanced Usage

### Custom Box Selection

Implement custom box selection logic:

```java
public class CustomBoxSelector {
    public static List<DetailedRentEligibleBox> selectProfitableBoxes(
            List<DetailedRentEligibleBox> boxes) {
        return boxes.stream()
            .filter(box -> box.getRentFee() > 200000000L) // > 0.2 ERG
            .filter(box -> !box.hasTokens()) // ERG only
            .filter(box -> box.getAgeInBlocks() > STORAGE_RENT_PERIOD_BLOCKS * 1.5) // 6+ years
            .sorted((a, b) -> Long.compare(b.getRentFee(), a.getRentFee()))
            .limit(25) // Top 25 boxes
            .collect(Collectors.toList());
    }
}
```

### Integration with Mining Pools

For mining pool integration:

```java
public class PoolIntegration {
    public void distributeRentFees(long totalRent, List<String> minerAddresses) {
        // Distribute rent fees among pool participants
        long feePerMiner = totalRent / minerAddresses.size();
        
        // Create distribution transaction
        // Implementation depends on pool structure
    }
}
```

## API Reference

### StorageRentBot

Main bot class for automated storage rent collection.

#### Methods

- `start()`: Starts the bot
- `stop()`: Stops the bot
- `reportStatus()`: Reports current status

### StorageRentScanner

Advanced scanner for finding eligible boxes.

#### Methods

- `scanForEligibleBoxes(ScanConfig)`: Scans for eligible boxes
- `analyzeBox(OutputInfo, ...)`: Analyzes a single box

### ResultsFilter

Utility class for filtering scan results.

#### Methods

- `filterByMinRent(boxes, minRent)`: Filter by minimum rent
- `filterByMaxAge(boxes, maxAge)`: Filter by maximum age
- `topByRent(boxes, count)`: Get top boxes by rent

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Submit a pull request

## License

This project is licensed under the CC0 License - see the LICENSE file for details.

## Disclaimer

This software is provided as-is without any warranties. Users are responsible for:

- Understanding Ergo's storage rent mechanism
- Testing thoroughly before mainnet use
- Securing their wallet and private keys
- Complying with applicable laws and regulations

Use at your own risk. The authors are not responsible for any losses or damages.

## Resources

- [Ergo Platform Documentation](https://docs.ergoplatform.com/)
- [Storage Rent Documentation](https://docs.ergoplatform.com/mining/rent/rent-fees/)
- [Ergo AppKit Repository](https://github.com/ergoplatform/ergo-appkit)
- [Ergo Explorer API](https://api.ergoplatform.com/docs/)

## Support

For support and questions:

- [Ergo Discord](https://discord.gg/ergo-platform-668903786361651200)
- [Ergo Telegram](https://t.me/ergoplatform)
- [GitHub Issues](https://github.com/ergoplatform/ergo-appkit/issues) 