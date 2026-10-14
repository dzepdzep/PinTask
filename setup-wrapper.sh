#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
base="https://raw.githubusercontent.com/gradle/gradle/v8.13.0"
mkdir -p gradle/wrapper
curl -fL "$base/gradle/wrapper/gradle-wrapper.jar" -o gradle/wrapper/gradle-wrapper.jar
curl -fL "$base/gradlew.bat" -o gradlew.bat
curl -fL "$base/gradlew" -o gradlew
chmod +x gradlew
printf 'Official Gradle wrapper installed. Open this folder in Android Studio.\n'
