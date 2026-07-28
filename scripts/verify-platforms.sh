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

# The framework is produced by :shared:app, which owns the iOS targets. ":shared" is only
# an intermediate container project and has no link task of its own.
echo "Building iOS simulator framework..."
./gradlew :shared:app:linkDebugFrameworkIosSimulatorArm64

echo "All configured platform builds succeeded."
