#!/bin/bash

# MeditationTimer - Run Script
# Copyright (c) 2025 Yuri Leikind

set -e

echo "🧘‍♂️ MeditationTimer - Building and Running"
echo "=========================================="

# Check if Android SDK is available
if ! command -v adb &> /dev/null; then
    echo "❌ Error: adb not found. Please ensure Android SDK is installed and in PATH."
    exit 1
fi

# Check if device/emulator is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ Error: No Android device or emulator found."
    echo "Please connect a device or start an emulator first."
    exit 1
fi

echo "📱 Device found:"
adb devices

echo ""
echo "🔨 Building debug APK..."
./gradlew assembleDebug

echo ""
echo "📦 Installing APK..."
./gradlew installDebug

echo ""
echo "🚀 Starting MeditationTimer..."
adb shell am start -n com.example.meditationtimer/.MainActivity

echo ""
echo "✅ MeditationTimer is now running on your device!"
echo ""
echo "📋 Useful commands:"
echo "   - View logs: adb logcat | grep MeditationTimer"
echo "   - Stop app: adb shell am force-stop com.example.meditationtimer"
echo "   - Uninstall: ./gradlew uninstallDebug"
