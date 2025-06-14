package org.ergoplatform.appkit.examples;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Utility class for loading environment variables from .env file and system environment.
 * 
 * This class provides a secure way to manage configuration by:
 * 1. Loading from .env file if it exists
 * 2. Falling back to system environment variables
 * 3. Providing default values where appropriate
 */
public class EnvConfig {
    private static final Map<String, String> envVars = new HashMap<>();
    private static boolean loaded = false;
    
    static {
        loadEnvironment();
    }
    
    /**
     * Load environment variables from .env file and system environment
     */
    private static void loadEnvironment() {
        if (loaded) return;
        
        // First load from .env file if it exists
        loadFromDotEnvFile();
        
        // Then load from system environment (this will override .env values)
        envVars.putAll(System.getenv());
        
        loaded = true;
    }
    
    /**
     * Load variables from .env file
     */
    private static void loadFromDotEnvFile() {
        Path envFile = Paths.get(".env");
        if (!Files.exists(envFile)) {
            System.out.println("No .env file found. Using system environment variables only.");
            return;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(envFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse KEY=VALUE format
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String key = line.substring(0, equalIndex).trim();
                    String value = line.substring(equalIndex + 1).trim();
                    
                    // Remove quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    envVars.put(key, value);
                }
            }
            System.out.println("Loaded configuration from .env file");
        } catch (IOException e) {
            System.err.println("Warning: Could not read .env file: " + e.getMessage());
        }
    }
    
    /**
     * Get environment variable as string
     */
    public static String get(String key) {
        return envVars.get(key);
    }
    
    /**
     * Get environment variable as string with default value
     */
    public static String get(String key, String defaultValue) {
        return envVars.getOrDefault(key, defaultValue);
    }
    
    /**
     * Get environment variable as boolean
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = envVars.get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Get environment variable as long
     */
    public static long getLong(String key, long defaultValue) {
        String value = envVars.get(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid number format for " + key + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Get environment variable as integer
     */
    public static int getInt(String key, int defaultValue) {
        String value = envVars.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid number format for " + key + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Check if a required environment variable is set
     */
    public static void requireEnv(String key) {
        if (!envVars.containsKey(key) || envVars.get(key).isEmpty()) {
            throw new IllegalStateException("Required environment variable not set: " + key);
        }
    }
    
    /**
     * Get all loaded environment variables (for debugging)
     */
    public static Map<String, String> getAllVars() {
        return new HashMap<>(envVars);
    }
    
    /**
     * Print configuration summary (without sensitive values)
     */
    public static void printConfigSummary() {
        System.out.println("=== Configuration Summary ===");
        System.out.println("Node URL: " + get("ERGO_NODE_URL", "not set"));
        System.out.println("Network: " + get("ERGO_NETWORK", "not set"));
        System.out.println("Dry Run: " + get("STORAGE_RENT_DRY_RUN", "not set"));
        System.out.println("Scan Interval: " + get("STORAGE_RENT_SCAN_INTERVAL_MINUTES", "not set") + " minutes");
        System.out.println("Min Threshold: " + get("STORAGE_RENT_MIN_THRESHOLD_NANOERG", "not set") + " nanoERG");
        System.out.println("Use Explorer: " + get("STORAGE_RENT_USE_EXPLORER", "not set"));
        System.out.println("Mnemonic: " + (get("ERGO_MNEMONIC") != null ? "***SET***" : "NOT SET"));
        System.out.println("==============================");
    }
} 