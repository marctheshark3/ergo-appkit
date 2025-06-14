package org.ergoplatform.appkit.examples;

import org.ergoplatform.appkit.NetworkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example usage of the Storage Rent Bot
 * 
 * This example demonstrates different ways to configure and run the storage rent bot
 * for claiming storage rent fees on the Ergo blockchain.
 */
public class StorageRentBotExample {
    private static final Logger logger = LoggerFactory.getLogger(StorageRentBotExample.class);
    
    public static void main(String[] args) {
        // Example 1: Basic configuration for testing
        runBasicExample();
        
        // Example 2: Production configuration
        // runProductionExample();
        
        // Example 3: Scanner-only example
        // runScannerExample();
    }
    
    /**
     * Basic example using environment configuration
     */
    public static void runBasicExample() {
        logger.info("=== Storage Rent Bot - Basic Example ===");
        
        try {
            // Print configuration summary
            EnvConfig.printConfigSummary();
            
            // Create configuration from environment variables (.env file)
            StorageRentBot.Config config = new StorageRentBot.Config();
        
            StorageRentBot bot = new StorageRentBot(config);
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down Storage Rent Bot...");
                bot.stop();
            }));
            
            // Start the bot
            logger.info("Starting bot with environment configuration...");
            if (config.isDryRun()) {
                logger.info("Running in DRY-RUN mode (no actual transactions will be made)");
            } else {
                logger.warn("Running in LIVE mode - real transactions will be made!");
            }
            
            bot.start();
            
            // Let it run for a while in this example
            try {
                Thread.sleep(300000); // Run for 5 minutes
                bot.stop();
            } catch (InterruptedException e) {
                logger.info("Example interrupted");
                bot.stop();
            }
            
        } catch (Exception e) {
            logger.error("Error in basic example: ", e);
            if (e.getMessage().contains("ERGO_MNEMONIC")) {
                logger.error("Make sure to create a .env file with your ERGO_MNEMONIC set!");
                logger.error("Copy .env.example to .env and fill in your values.");
            }
        }
    }
    
    /**
     * Production configuration example using environment variables
     * Make sure to set STORAGE_RENT_DRY_RUN=false and ERGO_NETWORK=MAINNET in your .env
     */
    public static void runProductionExample() {
        logger.info("=== Storage Rent Bot - Production Example ===");
        
        try {
            // Print configuration summary
            EnvConfig.printConfigSummary();
            
            // Create configuration from environment variables
            StorageRentBot.Config config = new StorageRentBot.Config();
            
            // Validate configuration for production
            if (config.getWalletMnemonic() == null || config.getWalletMnemonic().isEmpty()) {
                logger.error("Wallet mnemonic not provided. Set ERGO_MNEMONIC in your .env file.");
                return;
            }
            
            if (config.isDryRun()) {
                logger.warn("Still in dry-run mode. Set STORAGE_RENT_DRY_RUN=false for production.");
                logger.warn("Make sure you've tested thoroughly before disabling dry-run mode!");
            }
            
            if (config.getNetworkType() != NetworkType.MAINNET) {
                logger.warn("Not using mainnet. Set ERGO_NETWORK=MAINNET for production.");
            }
        
        StorageRentBot bot = new StorageRentBot(config);
        
        Runtime.getRuntime().addShutdownHook(new Thread(bot::stop));
        
        logger.info("Starting production storage rent bot...");
        logger.warn("This will send real transactions! Make sure you understand the risks.");
        
        bot.start();
        
        try {
            Thread.currentThread().join(); // Run indefinitely
        } catch (InterruptedException e) {
            logger.info("Bot interrupted, shutting down...");
            bot.stop();
        }
    }
    
    /**
     * Scanner-only example for analysis
     */
    public static void runScannerExample() {
        logger.info("=== Storage Rent Scanner - Analysis Example ===");
        
        try {
            // This would require implementing the scanner integration
            // StorageRentScanner scanner = new StorageRentScanner(ctx, "https://api.ergoplatform.com");
            
            logger.info("Scanner example would analyze eligible boxes without claiming rent");
            logger.info("This is useful for understanding the current state of storage rent opportunities");
            
        } catch (Exception e) {
            logger.error("Error in scanner example: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Utility method to validate bot configuration
     */
    public static boolean validateConfig(StorageRentBot.Config config) {
        if (config.getWalletMnemonic() == null || config.getWalletMnemonic().trim().isEmpty()) {
            logger.error("Wallet mnemonic is required");
            return false;
        }
        
        if (config.getMinRentThreshold() < 1000000L) { // Less than 0.001 ERG
            logger.warn("Very low rent threshold: {} nanoERG", config.getMinRentThreshold());
        }
        
        if (config.getMaxBoxesPerTransaction() > 100) {
            logger.warn("Large transaction size may result in high fees: {} boxes", config.getMaxBoxesPerTransaction());
        }
        
        if (!config.isDryRun() && config.getNetworkType() == NetworkType.MAINNET) {
            logger.warn("PRODUCTION MODE: Real transactions will be sent on mainnet!");
        }
        
        return true;
    }
} 