# Fetch the official Gradle 8.13 wrapper assets from the Gradle source tag.
$ErrorActionPreference = "Stop"
$base = "https://raw.githubusercontent.com/gradle/gradle/v8.13.0"
$root = $PSScriptRoot
New-Item -ItemType Directory -Force -Path (Join-Path $root "gradle/wrapper") | Out-Null
Invoke-WebRequest -Uri "$base/gradle/wrapper/gradle-wrapper.jar" -OutFile (Join-Path $root "gradle/wrapper/gradle-wrapper.jar")
Invoke-WebRequest -Uri "$base/gradlew.bat" -OutFile (Join-Path $root "gradlew.bat")
Invoke-WebRequest -Uri "$base/gradlew" -OutFile (Join-Path $root "gradlew")
Write-Host "Official Gradle wrapper installed. Open this folder in Android Studio."
