#!/bin/bash

# Quick OAuth Setup Script for Email Manager

echo "====================================="
echo "Email Manager - OAuth Setup Helper"
echo "====================================="
echo ""

# Check if .env file exists
if [ -f .env ]; then
    echo "[OK] .env file found"
    echo ""
else
    echo "[!] No .env file found. Creating from template..."
    cp .env.example .env
    echo ""
    echo "[ACTION REQUIRED] Please edit .env file with your Google OAuth credentials"
    echo ""
    echo "Steps:"
    echo "1. Open .env file in an editor"
    echo "2. Get credentials from: https://console.cloud.google.com/"
    echo "3. Fill in GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET"
    echo "4. Save the file and run this script again"
    echo ""
    read -p "Press Enter to continue..."
    exit 1
fi

# Check if credentials are set in .env
if grep -q "your-client-id-here" .env; then
    echo "[!] WARNING: .env file still contains placeholder values"
    echo "Please update .env with your actual Google OAuth credentials"
    echo ""
    read -p "Press Enter to continue anyway or Ctrl+C to exit..."
fi

echo "Loading environment variables from .env..."
echo ""

# Load .env file
set -a
source .env
set +a

echo "Environment variables loaded"
echo ""

echo "====================================="
echo "Starting Email Manager Backend"
echo "====================================="
echo ""
echo "OAuth Configuration:"
echo "  Redirect URI: http://localhost:8080/api/oauth/callback"
echo "  Frontend URL: http://localhost:3000"
echo ""
echo "After backend starts:"
echo "  1. Run ./start-frontend.sh in another terminal"
echo "  2. Open http://localhost:3000"
echo "  3. Go to Account Management"
echo "  4. Click 'Connect Gmail (OAuth)'"
echo ""
echo "Starting backend server..."
echo ""

./mvnw spring-boot:run
