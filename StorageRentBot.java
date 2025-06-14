package org.ergoplatform.appkit.examples;

import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoToken;
import org.ergoplatform.sdk.SecretString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Storage Rent Fee Claiming Bot for Ergo Blockchain
 * 
 * This bot automatically identifies boxes eligible for storage rent collection
 * and creates transactions to claim the storage rent fees. According to Ergo's
 * storage rent mechanism, boxes older than 4 years (approximately 1,051,200 blocks)
 * can have their storage rent fees collected by miners.
 * 
 * Key Features:
 * - Scans the blockchain for eligible boxes
 * - Calculates storage rent fees based on box size and age
 * - Creates and signs transactions to claim rent fees
 * - Handles multiple boxes in batch transactions
 * - Configurable scanning intervals and fee thresholds
 * 
 * Storage Rent Details:
 * - Default storage fee factor: 1,250,000 nanoergs/byte
 * - Average box size: ~105 bytes
 * - Storage rent period: 4 years (1,051,200 blocks)
 * - Estimated rent per standard box: ~0.13 ERG
 */
public class StorageRentBot {
    private static final Logger logger = LoggerFactory.getLogger(StorageRentBot.class);
    
    // Storage rent constants
    private static final int STORAGE_RENT_PERIOD_BLOCKS = 1051200; // 4 years in blocks
    private static final long DEFAULT_STORAGE_FEE_FACTOR = 1250000L; // nanoergs per byte
    private static final int AVERAGE_BOX_SIZE_BYTES = 105;
    private static final long MIN_RENT_THRESHOLD = 100000000L; // 0.1 ERG minimum to claim
    
    // Bot configuration
    private final ErgoClient ergoClient;
    private final ErgoProver prover;
    private final Address minerAddress;
    private final NetworkType networkType;
    private final ScheduledExecutorService scheduler;
    
    // Runtime state
    private volatile boolean isRunning = false;
    private long totalRentClaimed = 0L;
    private int totalBoxesProcessed = 0;
    
    /**
     * Configuration class for the Storage Rent Bot
     * Now loads from environment variables and .env file
     */
    public static class Config {
        private String nodeUrl;
        private NetworkType networkType;
        private String walletMnemonic;
        private String walletPassword;
        private long scanIntervalMinutes;
        private long minRentThreshold;
        private int maxBoxesPerTransaction;
        private boolean dryRun;
        private String explorerUrl;
        private boolean useExplorerForScanning;
        private int maxBoxesToScanPerRound;
        private boolean includeTokenBoxes;
        private long maxTransactionFee;
        
        /**
         * Default constructor that loads configuration from environment variables
         */
        public Config() {
            loadFromEnvironment();
        }
        
        /**
         * Load configuration from environment variables using EnvConfig
         */
        private void loadFromEnvironment() {
            // Required variables
            EnvConfig.requireEnv("ERGO_MNEMONIC");
            
            // Load all configuration
            this.nodeUrl = EnvConfig.get("ERGO_NODE_URL", "http://127.0.0.1:9053");
            this.walletMnemonic = EnvConfig.get("ERGO_MNEMONIC");
            this.walletPassword = EnvConfig.get("ERGO_PASSWORD", "");
            
            // Parse network type
            String networkStr = EnvConfig.get("ERGO_NETWORK", "TESTNET");
            try {
                this.networkType = NetworkType.valueOf(networkStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Invalid network type '" + networkStr + "'. Using TESTNET.");
                this.networkType = NetworkType.TESTNET;
            }
            
            // Bot configuration
            this.dryRun = EnvConfig.getBoolean("STORAGE_RENT_DRY_RUN", true);
            this.scanIntervalMinutes = EnvConfig.getLong("STORAGE_RENT_SCAN_INTERVAL_MINUTES", 60L);
            this.minRentThreshold = EnvConfig.getLong("STORAGE_RENT_MIN_THRESHOLD_NANOERG", MIN_RENT_THRESHOLD);
            this.maxBoxesPerTransaction = EnvConfig.getInt("STORAGE_RENT_MAX_BOXES_PER_TX", 50);
            this.maxTransactionFee = EnvConfig.getLong("STORAGE_RENT_MAX_TX_FEE_NANOERG", 10000000L);
            
            // Explorer configuration
            this.explorerUrl = EnvConfig.get("ERGO_EXPLORER_URL", "https://api.ergoplatform.com");
            this.useExplorerForScanning = EnvConfig.getBoolean("STORAGE_RENT_USE_EXPLORER", true);
            this.maxBoxesToScanPerRound = EnvConfig.getInt("STORAGE_RENT_MAX_BOXES_TO_SCAN", 1000);
            this.includeTokenBoxes = EnvConfig.getBoolean("STORAGE_RENT_INCLUDE_TOKEN_BOXES", true);
        } max fee
        
        // Getters and setters
        public String getNodeUrl() { return nodeUrl; }
        public Config setNodeUrl(String nodeUrl) { this.nodeUrl = nodeUrl; return this; }
        
        public NetworkType getNetworkType() { return networkType; }
        public Config setNetworkType(NetworkType networkType) { this.networkType = networkType; return this; }
        
        public String getWalletMnemonic() { return walletMnemonic; }
        public Config setWalletMnemonic(String walletMnemonic) { this.walletMnemonic = walletMnemonic; return this; }
        
        public String getWalletPassword() { return walletPassword; }
        public Config setWalletPassword(String walletPassword) { this.walletPassword = walletPassword; return this; }
        
        public long getScanIntervalMinutes() { return scanIntervalMinutes; }
        public Config setScanIntervalMinutes(long scanIntervalMinutes) { this.scanIntervalMinutes = scanIntervalMinutes; return this; }
        
        public long getMinRentThreshold() { return minRentThreshold; }
        public Config setMinRentThreshold(long minRentThreshold) { this.minRentThreshold = minRentThreshold; return this; }
        
        public int getMaxBoxesPerTransaction() { return maxBoxesPerTransaction; }
        public Config setMaxBoxesPerTransaction(int maxBoxesPerTransaction) { this.maxBoxesPerTransaction = maxBoxesPerTransaction; return this; }
        
        public boolean isDryRun() { return dryRun; }
        public Config setDryRun(boolean dryRun) { this.dryRun = dryRun; return this; }
        
        public String getExplorerUrl() { return explorerUrl; }
        public Config setExplorerUrl(String explorerUrl) { this.explorerUrl = explorerUrl; return this; }
        
        public boolean isUseExplorerForScanning() { return useExplorerForScanning; }
        public Config setUseExplorerForScanning(boolean useExplorerForScanning) { this.useExplorerForScanning = useExplorerForScanning; return this; }
        
        public int getMaxBoxesToScanPerRound() { return maxBoxesToScanPerRound; }
        public Config setMaxBoxesToScanPerRound(int maxBoxesToScanPerRound) { this.maxBoxesToScanPerRound = maxBoxesToScanPerRound; return this; }
        
        public boolean isIncludeTokenBoxes() { return includeTokenBoxes; }
        public Config setIncludeTokenBoxes(boolean includeTokenBoxes) { this.includeTokenBoxes = includeTokenBoxes; return this; }
        
        public long getMaxTransactionFee() { return maxTransactionFee; }
        public Config setMaxTransactionFee(long maxTransactionFee) { this.maxTransactionFee = maxTransactionFee; return this; }
    }
    
    /**
     * Represents a box eligible for storage rent collection
     */
    public static class RentEligibleBox {
        private final InputBox box;
        private final long rentFee;
        private final int ageInBlocks;
        private final long boxSizeBytes;
        
        public RentEligibleBox(InputBox box, long rentFee, int ageInBlocks, long boxSizeBytes) {
            this.box = box;
            this.rentFee = rentFee;
            this.ageInBlocks = ageInBlocks;
            this.boxSizeBytes = boxSizeBytes;
        }
        
        public InputBox getBox() { return box; }
        public long getRentFee() { return rentFee; }
        public int getAgeInBlocks() { return ageInBlocks; }
        public long getBoxSizeBytes() { return boxSizeBytes; }
    }
    
    private final Config config;
    
    /**
     * Creates a new Storage Rent Bot with the given configuration
     */
    public StorageRentBot(Config config) {
        this.config = config;
        this.ergoClient = RestApiErgoClient.create(config.getNodeUrl(), config.getNetworkType(), "", "");
        this.networkType = config.getNetworkType();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Initialize prover with wallet mnemonic
        this.prover = ergoClient.execute(ctx -> {
            return ctx.newProverBuilder()
                .withMnemonic(SecretString.create(config.getWalletMnemonic()), SecretString.create(config.getWalletPassword()), false)
                .build();
        });
        
        this.minerAddress = prover.getAddress();
        logger.info("Storage Rent Bot initialized for address: {}", minerAddress);
    }
    
    /**
     * Starts the storage rent bot
     */
    public void start() {
        if (isRunning) {
            logger.warn("Bot is already running");
            return;
        }
        
        isRunning = true;
        logger.info("Starting Storage Rent Bot...");
        logger.info("Network: {}", networkType);
        logger.info("Miner Address: {}", minerAddress);
        logger.info("Scan Interval: {} minutes", config.getScanIntervalMinutes());
        logger.info("Min Rent Threshold: {} nanoERG", config.getMinRentThreshold());
        logger.info("Dry Run Mode: {}", config.isDryRun());
        
        // Schedule periodic scanning
        scheduler.scheduleAtFixedRate(
            this::scanAndClaimRent,
            0, // Start immediately
            config.getScanIntervalMinutes(),
            TimeUnit.MINUTES
        );
        
        // Schedule periodic status reporting
        scheduler.scheduleAtFixedRate(
            this::reportStatus,
            5, // First report after 5 minutes
            30, // Then every 30 minutes
            TimeUnit.MINUTES
        );
    }
    
    /**
     * Stops the storage rent bot
     */
    public void stop() {
        if (!isRunning) {
            logger.warn("Bot is not running");
            return;
        }
        
        isRunning = false;
        scheduler.shutdown();
        logger.info("Storage Rent Bot stopped");
        reportFinalStatus();
    }
    
    /**
     * Main scanning and claiming logic
     */
    private void scanAndClaimRent() {
        if (!isRunning) return;
        
        try {
            logger.info("Starting storage rent scan...");
            
            ergoClient.execute(ctx -> {
                int currentHeight = ctx.getHeight();
                logger.info("Current blockchain height: {}", currentHeight);
                
                // Find eligible boxes
                List<RentEligibleBox> eligibleBoxes = findEligibleBoxes(ctx, currentHeight);
                logger.info("Found {} boxes eligible for storage rent collection", eligibleBoxes.size());
                
                if (eligibleBoxes.isEmpty()) {
                    logger.info("No eligible boxes found for rent collection");
                    return null;
                }
                
                // Filter by minimum rent threshold
                List<RentEligibleBox> profitableBoxes = eligibleBoxes.stream()
                    .filter(box -> box.getRentFee() >= config.getMinRentThreshold())
                    .collect(Collectors.toList());
                
                logger.info("Found {} profitable boxes (above {} nanoERG threshold)", 
                    profitableBoxes.size(), config.getMinRentThreshold());
                
                if (profitableBoxes.isEmpty()) {
                    logger.info("No profitable boxes found for rent collection");
                    return null;
                }
                
                // Process boxes in batches
                processBatches(ctx, profitableBoxes);
                
                return null;
            });
            
        } catch (Exception e) {
            logger.error("Error during storage rent scan: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Finds boxes eligible for storage rent collection
     */
    private List<RentEligibleBox> findEligibleBoxes(BlockchainContext ctx, int currentHeight) {
        List<RentEligibleBox> eligibleBoxes = new ArrayList<>();
        
        try {
            // Get blockchain parameters for storage fee calculation
            BlockchainParameters params = ctx.getParameters();
            long storageFeeFactor = params.getStorageFeeFactor();
            
            logger.debug("Storage fee factor: {} nanoERG/byte", storageFeeFactor);
            
            List<InputBox> unspentBoxes = getUnspentBoxes(ctx);
            
            for (InputBox box : unspentBoxes) {
                int boxAge = currentHeight - box.getCreationHeight();
                
                if (boxAge >= STORAGE_RENT_PERIOD_BLOCKS) {
                    // Calculate storage rent fee
                    long boxSizeBytes = estimateBoxSize(box);
                    long rentFee = calculateStorageRentFee(boxSizeBytes, storageFeeFactor);
                    
                    // Only include if the box has enough value to pay the rent
                    if (box.getValue() > rentFee + Parameters.MinChangeValue) {
                        eligibleBoxes.add(new RentEligibleBox(box, rentFee, boxAge, boxSizeBytes));
                        logger.debug("Eligible box found: {} (age: {} blocks, rent: {} nanoERG)", 
                            box.getId(), boxAge, rentFee);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error finding eligible boxes: {}", e.getMessage(), e);
        }
        
        return eligibleBoxes;
    }
    
    /**
     * Gets unspent boxes from the blockchain
     */
    private List<InputBox> getUnspentBoxes(BlockchainContext ctx) {
        List<InputBox> boxes = new ArrayList<>();
        
        try {
            // Get all unspent boxes from the blockchain using pagination
            // This is a simplified approach - in practice you might want to scan specific addresses
            // or use the Explorer API for more efficient scanning
            
            // For now, we'll use a placeholder approach that would need to be implemented
            // based on your specific scanning strategy
            
            // Option 1: Scan specific addresses (if you have a list of addresses to monitor)
            // Option 2: Use Explorer API to get all unspent boxes (implemented in StorageRentScanner)
            // Option 3: Use node API to scan the UTXO set
            
            logger.debug("Scanning for unspent boxes...");
            
            // This is where you would implement your box retrieval strategy
            // For example, using the Explorer API or scanning specific addresses
            
            // Placeholder implementation - you would replace this with actual box retrieval
            // boxes = scanAllUnspentBoxes(ctx);
            
        } catch (Exception e) {
            logger.error("Error retrieving unspent boxes: {}", e.getMessage(), e);
        }
        
        logger.debug("Retrieved {} unspent boxes for analysis", boxes.size());
        return boxes;
    }
    
    /**
     * Alternative method using Explorer API for box scanning
     * This integrates with the StorageRentScanner for more efficient scanning
     */
    private List<InputBox> getUnspentBoxesUsingExplorer(BlockchainContext ctx) {
        try {
            StorageRentScanner scanner = new StorageRentScanner(ctx, "https://api.ergoplatform.com");
            
            StorageRentScanner.ScanConfig scanConfig = new StorageRentScanner.ScanConfig()
                .setMaxBoxesToScan(config.getMaxBoxesPerTransaction() * 10) // Scan more than we can process
                .setMinRentThreshold(config.getMinRentThreshold())
                .setIncludeTokenBoxes(true)
                .setSortByRentDesc(true);
            
            StorageRentScanner.ScanResults results = scanner.scanForEligibleBoxes(scanConfig);
            
            // Convert DetailedRentEligibleBox to InputBox
            // Note: This would require additional implementation to convert between types
            List<InputBox> inputBoxes = new ArrayList<>();
            
            // This is a placeholder - you'd need to implement the conversion
            // from DetailedRentEligibleBox to InputBox
            
            logger.info("Explorer scan found {} eligible boxes", results.getEligibleBoxCount());
            return inputBoxes;
            
        } catch (Exception e) {
            logger.error("Error using explorer for box scanning: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Estimates the size of a box in bytes
     */
    private long estimateBoxSize(InputBox box) {
        long size = 32; // Base size
        size += 8; // Value
        size += 4; // Creation height
        size += box.getErgoTree().bytes().length; // ErgoTree size
        
        // Add token sizes
        for (ErgoToken token : box.getTokens()) {
            size += 32; // Token ID
            size += 8;  // Token amount
        }
        
        // Add register sizes
        size += box.getRegisters().size() * 16;
        
        return Math.max(size, AVERAGE_BOX_SIZE_BYTES);
    }
    
    /**
     * Calculates storage rent fee for a box
     */
    private long calculateStorageRentFee(long boxSizeBytes, long storageFeeFactor) {
        return boxSizeBytes * storageFeeFactor;
    }
    
    /**
     * Processes eligible boxes in batches
     */
    private void processBatches(BlockchainContext ctx, List<RentEligibleBox> eligibleBoxes) {
        int batchSize = config.getMaxBoxesPerTransaction();
        
        for (int i = 0; i < eligibleBoxes.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, eligibleBoxes.size());
            List<RentEligibleBox> batch = eligibleBoxes.subList(i, endIndex);
            
            try {
                processBatch(ctx, batch, i / batchSize + 1);
            } catch (Exception e) {
                logger.error("Error processing batch {}: {}", i / batchSize + 1, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Processes a single batch of eligible boxes
     */
    private void processBatch(BlockchainContext ctx, List<RentEligibleBox> batch, int batchNumber) {
        logger.info("Processing batch {} with {} boxes", batchNumber, batch.size());
        
        try {
            long totalRent = batch.stream().mapToLong(RentEligibleBox::getRentFee).sum();
            
            logger.info("Batch {} - Total rent to collect: {} nanoERG from {} boxes", 
                batchNumber, totalRent, batch.size());
            
            if (config.isDryRun()) {
                logger.info("DRY RUN - Would claim {} nanoERG in storage rent fees", totalRent);
                totalRentClaimed += totalRent;
                totalBoxesProcessed += batch.size();
                return;
            }
            
            // Create storage rent collection transaction
            UnsignedTransaction unsignedTx = createStorageRentTransaction(ctx, batch);
            
            if (unsignedTx == null) {
                logger.warn("Failed to create transaction for batch {}", batchNumber);
                return;
            }
            
            // Sign and send transaction
            SignedTransaction signedTx = prover.sign(unsignedTx);
            String txId = ctx.sendTransaction(signedTx);
            
            logger.info("Storage rent transaction sent: {} (batch {})", txId, batchNumber);
            logger.info("Claimed {} nanoERG from {} boxes", totalRent, batch.size());
            
            totalRentClaimed += totalRent;
            totalBoxesProcessed += batch.size();
            
        } catch (Exception e) {
            logger.error("Error processing batch {}: {}", batchNumber, e.getMessage(), e);
        }
    }
    
    /**
     * Creates a transaction to collect storage rent from eligible boxes
     */
    private UnsignedTransaction createStorageRentTransaction(BlockchainContext ctx, List<RentEligibleBox> eligibleBoxes) {
        try {
            UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();
            
            // Add input boxes
            List<InputBox> inputBoxes = eligibleBoxes.stream()
                .map(RentEligibleBox::getBox)
                .collect(Collectors.toList());
            
            txBuilder.boxesToSpend(inputBoxes);
            
            // Create output boxes (recreate boxes with reduced value)
            for (RentEligibleBox eligibleBox : eligibleBoxes) {
                InputBox originalBox = eligibleBox.getBox();
                long rentFee = eligibleBox.getRentFee();
                long newValue = originalBox.getValue() - rentFee;
                
                // Only create output if there's enough value left
                if (newValue >= Parameters.MinChangeValue) {
                    OutBoxBuilder outputBuilder = txBuilder.outBoxBuilder()
                        .value(newValue)
                        .contract(new ErgoTreeContract(originalBox.getErgoTree(), networkType));
                    
                    // Preserve tokens
                    if (!originalBox.getTokens().isEmpty()) {
                        outputBuilder.tokens(originalBox.getTokens().toArray(new ErgoToken[0]));
                    }
                    
                    // Preserve registers (except R0 which is value, and R3 which contains creation info)
                    List<ErgoValue<?>> registers = originalBox.getRegisters();
                    if (registers.size() > 4) { // R4 and beyond
                        ErgoValue<?>[] registerArray = registers.subList(4, registers.size()).toArray(new ErgoValue[0]);
                        outputBuilder.registers(registerArray);
                    }
                    
                    txBuilder.outputs(outputBuilder.build());
                }
            }
            
            // Set fee and change address
            txBuilder.fee(Parameters.MinFee);
            txBuilder.sendChangeTo(minerAddress.getErgoAddress());
            
            return txBuilder.build();
            
        } catch (Exception e) {
            logger.error("Error creating storage rent transaction: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Reports current bot status
     */
    private void reportStatus() {
        logger.info("=== Storage Rent Bot Status ===");
        logger.info("Running: {}", isRunning);
        logger.info("Total rent claimed: {} nanoERG ({} ERG)", totalRentClaimed, totalRentClaimed / 1e9);
        logger.info("Total boxes processed: {}", totalBoxesProcessed);
        logger.info("Miner address: {}", minerAddress);
        logger.info("==============================");
    }
    
    /**
     * Reports final status when bot stops
     */
    private void reportFinalStatus() {
        logger.info("=== Final Storage Rent Bot Report ===");
        logger.info("Total rent claimed: {} nanoERG ({} ERG)", totalRentClaimed, totalRentClaimed / 1e9);
        logger.info("Total boxes processed: {}", totalBoxesProcessed);
        if (totalBoxesProcessed > 0) {
            logger.info("Average rent per box: {} nanoERG", totalRentClaimed / totalBoxesProcessed);
        }
        logger.info("=====================================");
    }
    
    /**
     * Main method to run the storage rent bot
     */
    public static void main(String[] args) {
        Config config = new Config()
            .setNodeUrl("http://127.0.0.1:9053")
            .setNetworkType(NetworkType.MAINNET)
            .setWalletMnemonic("your wallet mnemonic here")
            .setWalletPassword("")
            .setScanIntervalMinutes(60)
            .setMinRentThreshold(100000000L) // 0.1 ERG
            .setMaxBoxesPerTransaction(50)
            .setDryRun(true);
        
        StorageRentBot bot = new StorageRentBot(config);
        
        Runtime.getRuntime().addShutdownHook(new Thread(bot::stop));
        
        bot.start();
        
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Bot interrupted, shutting down...");
            bot.stop();
        }
    }
} 