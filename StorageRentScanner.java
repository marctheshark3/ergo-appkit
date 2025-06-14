package org.ergoplatform.appkit.examples;

import org.ergoplatform.appkit.*;
import org.ergoplatform.explorer.client.ExplorerApiClient;
import org.ergoplatform.explorer.client.model.OutputInfo;
import org.ergoplatform.explorer.client.model.Items;
import org.ergoplatform.sdk.ErgoToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced Storage Rent Scanner that uses the Explorer API to find eligible boxes
 * 
 * This scanner can identify boxes that are eligible for storage rent collection
 * by querying the Ergo Explorer API for unspent boxes and checking their age.
 */
public class StorageRentScanner {
    private static final Logger logger = LoggerFactory.getLogger(StorageRentScanner.class);
    
    private static final int STORAGE_RENT_PERIOD_BLOCKS = 1051200; // 4 years
    private static final long DEFAULT_STORAGE_FEE_FACTOR = 1250000L; // nanoergs per byte
    private static final int AVERAGE_BOX_SIZE_BYTES = 105;
    
    private final ExplorerApiClient explorerClient;
    private final BlockchainContext ctx;
    
    /**
     * Represents a box eligible for storage rent collection with detailed information
     */
    public static class DetailedRentEligibleBox {
        private final String boxId;
        private final String address;
        private final long value;
        private final int creationHeight;
        private final int currentHeight;
        private final long rentFee;
        private final long estimatedSize;
        private final List<ErgoToken> tokens;
        private final boolean hasTokens;
        
        public DetailedRentEligibleBox(String boxId, String address, long value, 
                                     int creationHeight, int currentHeight, long rentFee, 
                                     long estimatedSize, List<ErgoToken> tokens) {
            this.boxId = boxId;
            this.address = address;
            this.value = value;
            this.creationHeight = creationHeight;
            this.currentHeight = currentHeight;
            this.rentFee = rentFee;
            this.estimatedSize = estimatedSize;
            this.tokens = tokens != null ? tokens : new ArrayList<>();
            this.hasTokens = !this.tokens.isEmpty();
        }
        
        // Getters
        public String getBoxId() { return boxId; }
        public String getAddress() { return address; }
        public long getValue() { return value; }
        public int getCreationHeight() { return creationHeight; }
        public int getCurrentHeight() { return currentHeight; }
        public long getRentFee() { return rentFee; }
        public long getEstimatedSize() { return estimatedSize; }
        public List<ErgoToken> getTokens() { return tokens; }
        public boolean hasTokens() { return hasTokens; }
        public int getAgeInBlocks() { return currentHeight - creationHeight; }
        public long getValueAfterRent() { return value - rentFee; }
        public boolean isProfitable(long minThreshold) { return rentFee >= minThreshold; }
        
        @Override
        public String toString() {
            return String.format("Box[%s] Age: %d blocks, Value: %d nERG, Rent: %d nERG, Tokens: %d", 
                boxId.substring(0, 8), getAgeInBlocks(), value, rentFee, tokens.size());
        }
    }
    
    /**
     * Scanning configuration
     */
    public static class ScanConfig {
        private int maxBoxesToScan = 1000;
        private long minValueThreshold = 1000000L; // 0.001 ERG minimum box value
        private long minRentThreshold = 100000000L; // 0.1 ERG minimum rent
        private boolean includeTokenBoxes = true;
        private boolean sortByRentDesc = true;
        private int maxPages = 10; // Limit API calls
        
        // Getters and setters
        public int getMaxBoxesToScan() { return maxBoxesToScan; }
        public ScanConfig setMaxBoxesToScan(int maxBoxesToScan) { this.maxBoxesToScan = maxBoxesToScan; return this; }
        
        public long getMinValueThreshold() { return minValueThreshold; }
        public ScanConfig setMinValueThreshold(long minValueThreshold) { this.minValueThreshold = minValueThreshold; return this; }
        
        public long getMinRentThreshold() { return minRentThreshold; }
        public ScanConfig setMinRentThreshold(long minRentThreshold) { this.minRentThreshold = minRentThreshold; return this; }
        
        public boolean isIncludeTokenBoxes() { return includeTokenBoxes; }
        public ScanConfig setIncludeTokenBoxes(boolean includeTokenBoxes) { this.includeTokenBoxes = includeTokenBoxes; return this; }
        
        public boolean isSortByRentDesc() { return sortByRentDesc; }
        public ScanConfig setSortByRentDesc(boolean sortByRentDesc) { this.sortByRentDesc = sortByRentDesc; return this; }
        
        public int getMaxPages() { return maxPages; }
        public ScanConfig setMaxPages(int maxPages) { this.maxPages = maxPages; return this; }
    }
    
    /**
     * Scan results with statistics
     */
    public static class ScanResults {
        private final List<DetailedRentEligibleBox> eligibleBoxes;
        private final int totalBoxesScanned;
        private final long totalRentAvailable;
        private final long totalValueInEligibleBoxes;
        private final int boxesWithTokens;
        private final long scanDurationMs;
        
        public ScanResults(List<DetailedRentEligibleBox> eligibleBoxes, int totalBoxesScanned, 
                          long scanDurationMs) {
            this.eligibleBoxes = eligibleBoxes;
            this.totalBoxesScanned = totalBoxesScanned;
            this.scanDurationMs = scanDurationMs;
            this.totalRentAvailable = eligibleBoxes.stream().mapToLong(DetailedRentEligibleBox::getRentFee).sum();
            this.totalValueInEligibleBoxes = eligibleBoxes.stream().mapToLong(DetailedRentEligibleBox::getValue).sum();
            this.boxesWithTokens = (int) eligibleBoxes.stream().filter(DetailedRentEligibleBox::hasTokens).count();
        }
        
        // Getters
        public List<DetailedRentEligibleBox> getEligibleBoxes() { return eligibleBoxes; }
        public int getTotalBoxesScanned() { return totalBoxesScanned; }
        public long getTotalRentAvailable() { return totalRentAvailable; }
        public long getTotalValueInEligibleBoxes() { return totalValueInEligibleBoxes; }
        public int getBoxesWithTokens() { return boxesWithTokens; }
        public long getScanDurationMs() { return scanDurationMs; }
        public int getEligibleBoxCount() { return eligibleBoxes.size(); }
        
        public void printSummary() {
            logger.info("=== Storage Rent Scan Results ===");
            logger.info("Total boxes scanned: {}", totalBoxesScanned);
            logger.info("Eligible boxes found: {}", getEligibleBoxCount());
            logger.info("Total rent available: {} nanoERG ({} ERG)", totalRentAvailable, totalRentAvailable / 1e9);
            logger.info("Total value in eligible boxes: {} nanoERG ({} ERG)", totalValueInEligibleBoxes, totalValueInEligibleBoxes / 1e9);
            logger.info("Boxes with tokens: {}", boxesWithTokens);
            logger.info("Scan duration: {} ms", scanDurationMs);
            logger.info("Average rent per eligible box: {} nanoERG", 
                getEligibleBoxCount() > 0 ? totalRentAvailable / getEligibleBoxCount() : 0);
            logger.info("================================");
        }
    }
    
    public StorageRentScanner(BlockchainContext ctx, String explorerUrl) {
        this.ctx = ctx;
        this.explorerClient = new ExplorerApiClient(explorerUrl);
    }
    
    /**
     * Scans for boxes eligible for storage rent collection
     */
    public ScanResults scanForEligibleBoxes(ScanConfig config) {
        logger.info("Starting storage rent scan with config: maxBoxes={}, minRent={} nERG", 
            config.getMaxBoxesToScan(), config.getMinRentThreshold());
        
        long startTime = System.currentTimeMillis();
        List<DetailedRentEligibleBox> eligibleBoxes = new ArrayList<>();
        int totalScanned = 0;
        int currentHeight = ctx.getHeight();
        long storageFeeFactor = ctx.getParameters().getStorageFeeFactor();
        
        try {
            // Scan unspent boxes using pagination
            int page = 0;
            int limit = Math.min(100, config.getMaxBoxesToScan()); // API limit per request
            
            while (page < config.getMaxPages() && totalScanned < config.getMaxBoxesToScan()) {
                logger.debug("Scanning page {} (limit: {})", page, limit);
                
                // Get unspent boxes from explorer
                Items<OutputInfo> outputs = explorerClient.getApiV1BoxesUnspentByoffsetByLimit(page * limit, limit);
                
                if (outputs.getItems().isEmpty()) {
                    logger.debug("No more boxes found, stopping scan");
                    break;
                }
                
                for (OutputInfo output : outputs.getItems()) {
                    if (totalScanned >= config.getMaxBoxesToScan()) break;
                    
                    totalScanned++;
                    
                    try {
                        DetailedRentEligibleBox eligibleBox = analyzeBox(output, currentHeight, storageFeeFactor, config);
                        if (eligibleBox != null) {
                            eligibleBoxes.add(eligibleBox);
                            logger.debug("Found eligible box: {}", eligibleBox);
                        }
                    } catch (Exception e) {
                        logger.warn("Error analyzing box {}: {}", output.getBoxId(), e.getMessage());
                    }
                }
                
                page++;
                
                // Rate limiting to avoid overwhelming the API
                try {
                    Thread.sleep(100); // 100ms delay between requests
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during box scanning: {}", e.getMessage(), e);
        }
        
        // Sort results if requested
        if (config.isSortByRentDesc()) {
            eligibleBoxes.sort((a, b) -> Long.compare(b.getRentFee(), a.getRentFee()));
        }
        
        long scanDuration = System.currentTimeMillis() - startTime;
        ScanResults results = new ScanResults(eligibleBoxes, totalScanned, scanDuration);
        
        logger.info("Scan completed: found {} eligible boxes out of {} scanned in {} ms", 
            eligibleBoxes.size(), totalScanned, scanDuration);
        
        return results;
    }
    
    /**
     * Analyzes a single box to determine if it's eligible for storage rent collection
     */
    private DetailedRentEligibleBox analyzeBox(OutputInfo output, int currentHeight, 
                                             long storageFeeFactor, ScanConfig config) {
        
        // Check if box value meets minimum threshold
        if (output.getValue() < config.getMinValueThreshold()) {
            return null;
        }
        
        // Check box age
        int boxAge = currentHeight - output.getCreationHeight();
        if (boxAge < STORAGE_RENT_PERIOD_BLOCKS) {
            return null;
        }
        
        // Parse tokens if present
        List<ErgoToken> tokens = new ArrayList<>();
        if (output.getAssets() != null) {
            for (var asset : output.getAssets()) {
                tokens.add(new ErgoToken(asset.getTokenId(), asset.getAmount()));
            }
        }
        
        // Skip token boxes if not configured to include them
        if (!config.isIncludeTokenBoxes() && !tokens.isEmpty()) {
            return null;
        }
        
        // Estimate box size
        long estimatedSize = estimateBoxSizeFromOutput(output);
        
        // Calculate storage rent fee
        long rentFee = estimatedSize * storageFeeFactor;
        
        // Check if rent meets minimum threshold
        if (rentFee < config.getMinRentThreshold()) {
            return null;
        }
        
        // Check if box has enough value to pay rent
        if (output.getValue() <= rentFee + Parameters.MinChangeValue) {
            return null;
        }
        
        return new DetailedRentEligibleBox(
            output.getBoxId(),
            output.getAddress(),
            output.getValue(),
            output.getCreationHeight(),
            currentHeight,
            rentFee,
            estimatedSize,
            tokens
        );
    }
    
    /**
     * Estimates box size from OutputInfo
     */
    private long estimateBoxSizeFromOutput(OutputInfo output) {
        long size = 32; // Base size
        size += 8; // Value
        size += 4; // Creation height
        size += 32; // ErgoTree hash (estimated)
        
        // Add token sizes
        if (output.getAssets() != null) {
            size += output.getAssets().size() * 40; // 32 bytes ID + 8 bytes amount
        }
        
        // Add register sizes (estimated)
        if (output.getAdditionalRegisters() != null) {
            size += output.getAdditionalRegisters().size() * 16;
        }
        
        return Math.max(size, AVERAGE_BOX_SIZE_BYTES);
    }
    
    /**
     * Filters scan results by various criteria
     */
    public static class ResultsFilter {
        
        public static List<DetailedRentEligibleBox> filterByMinRent(List<DetailedRentEligibleBox> boxes, long minRent) {
            return boxes.stream()
                .filter(box -> box.getRentFee() >= minRent)
                .collect(Collectors.toList());
        }
        
        public static List<DetailedRentEligibleBox> filterByMaxAge(List<DetailedRentEligibleBox> boxes, int maxAge) {
            return boxes.stream()
                .filter(box -> box.getAgeInBlocks() <= maxAge)
                .collect(Collectors.toList());
        }
        
        public static List<DetailedRentEligibleBox> filterByMinAge(List<DetailedRentEligibleBox> boxes, int minAge) {
            return boxes.stream()
                .filter(box -> box.getAgeInBlocks() >= minAge)
                .collect(Collectors.toList());
        }
        
        public static List<DetailedRentEligibleBox> filterTokenBoxes(List<DetailedRentEligibleBox> boxes, boolean includeTokens) {
            return boxes.stream()
                .filter(box -> includeTokens || !box.hasTokens())
                .collect(Collectors.toList());
        }
        
        public static List<DetailedRentEligibleBox> topByRent(List<DetailedRentEligibleBox> boxes, int count) {
            return boxes.stream()
                .sorted((a, b) -> Long.compare(b.getRentFee(), a.getRentFee()))
                .limit(count)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Example usage and testing
     */
    public static void main(String[] args) {
        String nodeUrl = "http://127.0.0.1:9053";
        String explorerUrl = "https://api.ergoplatform.com";
        NetworkType networkType = NetworkType.MAINNET;
        
        ErgoClient ergoClient = RestApiErgoClient.create(nodeUrl, networkType, "", "");
        
        ergoClient.execute(ctx -> {
            StorageRentScanner scanner = new StorageRentScanner(ctx, explorerUrl);
            
            ScanConfig config = new ScanConfig()
                .setMaxBoxesToScan(500)
                .setMinRentThreshold(50000000L) // 0.05 ERG
                .setIncludeTokenBoxes(true)
                .setSortByRentDesc(true)
                .setMaxPages(5);
            
            logger.info("Starting storage rent scan...");
            ScanResults results = scanner.scanForEligibleBoxes(config);
            
            results.printSummary();
            
            // Show top 10 most profitable boxes
            List<DetailedRentEligibleBox> top10 = ResultsFilter.topByRent(results.getEligibleBoxes(), 10);
            
            logger.info("\nTop 10 most profitable boxes:");
            for (int i = 0; i < top10.size(); i++) {
                DetailedRentEligibleBox box = top10.get(i);
                logger.info("{}. {} - Rent: {} nERG ({} ERG)", 
                    i + 1, box.toString(), box.getRentFee(), box.getRentFee() / 1e9);
            }
            
            // Filter examples
            List<DetailedRentEligibleBox> highValueBoxes = ResultsFilter.filterByMinRent(
                results.getEligibleBoxes(), 500000000L); // 0.5 ERG minimum
            
            logger.info("\nHigh-value boxes (>0.5 ERG rent): {}", highValueBoxes.size());
            
            List<DetailedRentEligibleBox> veryOldBoxes = ResultsFilter.filterByMinAge(
                results.getEligibleBoxes(), STORAGE_RENT_PERIOD_BLOCKS * 2); // 8+ years old
            
            logger.info("Very old boxes (8+ years): {}", veryOldBoxes.size());
            
            return null;
        });
    }
} 