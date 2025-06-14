#!/bin/bash

# Ergo Storage Rent Bot Runner Script
# This script helps you run the storage rent bot with proper configuration

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Ergo Storage Rent Bot Runner ===${NC}"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed or not in PATH${NC}"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo -e "${GREEN}Java version: ${JAVA_VERSION}${NC}"

# Check for .env file
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}Warning: No .env file found${NC}"
    if [ -f ".env.example" ]; then
        echo -e "${YELLOW}Copy .env.example to .env and configure your settings:${NC}"
        echo -e "${YELLOW}cp .env.example .env${NC}"
        echo -e "${YELLOW}Then edit .env with your wallet mnemonic and other settings${NC}"
    else
        echo -e "${RED}Error: No .env.example file found either${NC}"
        exit 1
    fi
    exit 1
fi

echo -e "${GREEN}Found .env file${NC}"

# Load environment variables from .env file
set -a  # automatically export all variables
source .env
set +a  # stop automatically exporting

echo -e "${GREEN}Loaded environment variables from .env${NC}"

# Set default values from environment variables
NODE_URL="${ERGO_NODE_URL:-http://127.0.0.1:9053}"
NETWORK="${ERGO_NETWORK:-TESTNET}"
DRY_RUN="${STORAGE_RENT_DRY_RUN:-true}"
SCAN_INTERVAL="${STORAGE_RENT_SCAN_INTERVAL_MINUTES:-60}"
MIN_RENT_THRESHOLD="${STORAGE_RENT_MIN_THRESHOLD_NANOERG:-100000000}"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --node-url)
            NODE_URL="$2"
            shift 2
            ;;
        --network)
            NETWORK="$2"
            shift 2
            ;;
        --mnemonic)
            ERGO_MNEMONIC="$2"
            shift 2
            ;;
        --password)
            ERGO_PASSWORD="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN="$2"
            shift 2
            ;;
        --scan-interval)
            SCAN_INTERVAL="$2"
            shift 2
            ;;
        --min-rent)
            MIN_RENT_THRESHOLD="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --node-url URL          Ergo node URL (default: http://127.0.0.1:9053)"
            echo "  --network NETWORK       Network type: MAINNET or TESTNET (default: TESTNET)"
            echo "  --mnemonic MNEMONIC     Wallet mnemonic phrase"
            echo "  --password PASSWORD     Wallet password (optional)"
            echo "  --dry-run BOOLEAN       Dry run mode: true or false (default: true)"
            echo "  --scan-interval MINUTES Scan interval in minutes (default: 60)"
            echo "  --min-rent NANOERG      Minimum rent threshold in nanoERG (default: 100000000)"
            echo "  --help                  Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  ERGO_MNEMONIC          Wallet mnemonic phrase"
            echo "  ERGO_PASSWORD          Wallet password"
            echo ""
            echo "Examples:"
            echo "  $0 --dry-run true --network TESTNET"
            echo "  $0 --mnemonic \"your mnemonic here\" --dry-run false --network MAINNET"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Check for mnemonic
if [[ -z "$ERGO_MNEMONIC" ]]; then
    echo -e "${RED}Error: Wallet mnemonic is required${NC}"
    echo "Set it using --mnemonic option or ERGO_MNEMONIC environment variable"
    exit 1
fi

# Validate network
if [[ "$NETWORK" != "MAINNET" && "$NETWORK" != "TESTNET" ]]; then
    echo -e "${RED}Error: Network must be MAINNET or TESTNET${NC}"
    exit 1
fi

# Warning for production mode
if [[ "$DRY_RUN" == "false" && "$NETWORK" == "MAINNET" ]]; then
    echo -e "${YELLOW}WARNING: You are about to run in PRODUCTION MODE on MAINNET!${NC}"
    echo -e "${YELLOW}This will send real transactions and spend real ERG!${NC}"
    echo -e "${YELLOW}Make sure you understand the risks and have tested thoroughly.${NC}"
    echo ""
    read -p "Are you sure you want to continue? (type 'yes' to confirm): " confirm
    if [[ "$confirm" != "yes" ]]; then
        echo "Aborted."
        exit 1
    fi
fi

# Build classpath (adjust this based on your setup)
CLASSPATH="."
if [[ -f "build.sbt" ]]; then
    # SBT project
    echo -e "${GREEN}Detected SBT project, building...${NC}"
    sbt compile
    CLASSPATH=$(sbt "export runtime:fullClasspath" | tail -1)
elif [[ -f "pom.xml" ]]; then
    # Maven project
    echo -e "${GREEN}Detected Maven project, building...${NC}"
    mvn compile
    CLASSPATH="target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)"
else
    echo -e "${YELLOW}Warning: No build file detected. Make sure classpath is set correctly.${NC}"
fi

# Export environment variables
export ERGO_MNEMONIC
export ERGO_PASSWORD

# Display configuration
echo -e "${GREEN}Configuration:${NC}"
echo "  Node URL: $NODE_URL"
echo "  Network: $NETWORK"
echo "  Dry Run: $DRY_RUN"
echo "  Scan Interval: $SCAN_INTERVAL minutes"
echo "  Min Rent Threshold: $MIN_RENT_THRESHOLD nanoERG"
echo ""

# Run the bot
echo -e "${GREEN}Starting Storage Rent Bot...${NC}"
java -cp "$CLASSPATH" \
    -Dnode.url="$NODE_URL" \
    -Dnetwork="$NETWORK" \
    -Ddry.run="$DRY_RUN" \
    -Dscan.interval="$SCAN_INTERVAL" \
    -Dmin.rent.threshold="$MIN_RENT_THRESHOLD" \
    org.ergoplatform.appkit.examples.StorageRentBotExample

echo -e "${GREEN}Storage Rent Bot finished.${NC}" 