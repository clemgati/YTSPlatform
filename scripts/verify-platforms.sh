#!/usr/bin/env bash

set -euo pipefail

echo "Building shared code..."
./gradlew :shared:build

echo "Building Android..."
./gradlew :androidApp:assembleDebug

echo "Building Desktop..."
./gradlew :desktopApp:build

echo "Building Web..."
./gradlew :webApp:wasmJsBrowserDevelopmentWebpack

echo "Building iOS simulator framework..."
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

echo "All configured platform builds succeeded."
