#!/bin/bash

# Ergo Storage Rent Bot Setup Script
# This script helps you set up the storage rent bot environment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Ergo Storage Rent Bot Setup ===${NC}"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed or not in PATH${NC}"
    echo -e "${YELLOW}Please install Java 8 or higher and try again${NC}"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo -e "${GREEN}Java version: ${JAVA_VERSION}${NC}"

# Check if .env.example exists
if [ ! -f ".env.example" ]; then
    echo -e "${RED}Error: .env.example file not found${NC}"
    echo -e "${YELLOW}Make sure you're in the correct directory${NC}"
    exit 1
fi

# Check if .env already exists
if [ -f ".env" ]; then
    echo -e "${YELLOW}Warning: .env file already exists${NC}"
    read -p "Do you want to overwrite it? (y/N): " overwrite
    if [[ "$overwrite" != "y" && "$overwrite" != "Y" ]]; then
        echo -e "${BLUE}Keeping existing .env file${NC}"
    else
        echo -e "${YELLOW}Creating new .env file...${NC}"
        cp .env.example .env
    fi
else
    echo -e "${GREEN}Creating .env file from template...${NC}"
    cp .env.example .env
fi

echo -e "${BLUE}=== Configuration Setup ===${NC}"
echo -e "${YELLOW}You need to configure your wallet mnemonic and other settings${NC}"
echo -e "${YELLOW}Opening .env file for editing...${NC}"
echo ""

# Try to open with different editors
if command -v nano &> /dev/null; then
    echo -e "${GREEN}Opening with nano editor${NC}"
    echo -e "${YELLOW}Important: Make sure to set your ERGO_MNEMONIC with quotes!${NC}"
    echo -e "${YELLOW}Example: ERGO_MNEMONIC=\"word1 word2 word3 ... word12\"${NC}"
    echo ""
    read -p "Press Enter to continue..."
    nano .env
elif command -v vim &> /dev/null; then
    echo -e "${GREEN}Opening with vim editor${NC}"
    echo -e "${YELLOW}Important: Make sure to set your ERGO_MNEMONIC with quotes!${NC}"
    echo -e "${YELLOW}Example: ERGO_MNEMONIC=\"word1 word2 word3 ... word12\"${NC}"
    echo ""
    read -p "Press Enter to continue..."
    vim .env
else
    echo -e "${YELLOW}No text editor found. Please manually edit the .env file${NC}"
    echo -e "${YELLOW}Set your ERGO_MNEMONIC and other configuration values${NC}"
    echo -e "${RED}IMPORTANT: Put quotes around your mnemonic phrase!${NC}"
    echo -e "${YELLOW}Example: ERGO_MNEMONIC=\"word1 word2 word3 ... word12\"${NC}"
fi

echo ""
echo -e "${BLUE}=== Testing Configuration ===${NC}"

# Test the configuration
if [ -f "TestEnvConfig.java" ] && [ -f "EnvConfig.java" ]; then
    echo -e "${GREEN}Compiling test classes...${NC}"
    javac TestEnvConfig.java EnvConfig.java
    
    # Create proper directory structure
    mkdir -p org/ergoplatform/appkit/examples
    mv *.class org/ergoplatform/appkit/examples/ 2>/dev/null || true
    
    echo -e "${GREEN}Testing configuration...${NC}"
    java -cp . org.ergoplatform.appkit.examples.TestEnvConfig
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Configuration test passed!${NC}"
    else
        echo -e "${RED}Configuration test failed${NC}"
        echo -e "${YELLOW}Please check your .env file settings${NC}"
    fi
else
    echo -e "${YELLOW}Test files not found, skipping configuration test${NC}"
fi

echo ""
echo -e "${GREEN}=== Setup Complete ===${NC}"
echo -e "${BLUE}Next steps:${NC}"
echo -e "${YELLOW}1. Make sure your .env file is properly configured${NC}"
echo -e "${YELLOW}2. Start with dry-run mode: STORAGE_RENT_DRY_RUN=true${NC}"
echo -e "${YELLOW}3. Use testnet first: ERGO_NETWORK=TESTNET${NC}"
echo -e "${YELLOW}4. Run the bot: ./run-storage-rent-bot.sh${NC}"
echo ""
echo -e "${RED}SECURITY REMINDER:${NC}"
echo -e "${RED}- Never commit your .env file to version control${NC}"
echo -e "${RED}- Keep your mnemonic phrase secure and private${NC}"
echo -e "${RED}- Always test with dry-run mode first${NC}"
echo "" 