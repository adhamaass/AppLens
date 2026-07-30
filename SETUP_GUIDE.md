# AppLens - Download & Setup Guide

## How to Get the APK

*Option 1: Download from GitHub Releases (easiest)*
1. Go to https://github.com/masonjenkins893-dot/AppLens/releases
2. Download AppLens-v2.0.apk from the latest release
3. Transfer to your phone if you downloaded on PC
4. Tap the APK file to install

*Option 2: Download from GitHub Actions*
1. Go to https://github.com/masonjenkins893-dot/AppLens/actions
2. Click the latest "Build APK" run
3. Scroll down to "Artifacts" section
4. Download "AppLens-APK"

*Option 3: Build it yourself*
1. Install Android Studio on any computer
2. Clone: git clone https://github.com/masonjenkins893-dot/AppLens.git
3. Open the android/ folder in Android Studio
4. Click Build > Build APK
5. Find the APK in android/app/build/outputs/apk/debug/

## One-Time Setup on Your Phone

*Step 1: Install Shizuku*
1. Download Shizuku from Play Store or https://shizuku.rikka.app
2. Open Shizuku app
3. Follow the in-app instructions to start it via Wireless Debugging

*Step 2: Start Shizuku*
1. Go to Settings > Developer Options
2. Enable Wireless Debugging
3. Open Shizuku app > tap "Start" > pair with your phone
4. Shizuku should show "Running"

*Step 3: Set Up AppLens*
1. Install AppLens APK
2. Open AppLens
3. Tap "Grant Shizuku Permission" (approve in Shizuku app)
4. Tap "Enable Accessibility Service" (toggle AppLens in accessibility settings)
5. Tap "Proceed to App Picker"

*Step 4: Extract Any App*
1. Select any app from the list
2. Watch the live log as it traverses the app
3. When done, tap "Share ZIP File"
4. ZIP is saved in Downloads/AppLens/

## What's in the ZIP

```
screens/       Raw UIAutomator XML per screen
wireframes/    SVG wireframes (no screenshots)
components/    Structured JSON component trees
manifest/      Permissions, activities, services, providers
flow.mmd       Mermaid navigation graph
report.md      Full extraction report with stats
```

## Requirements
1. Android 8.0+ (API 26+)
2. Shizuku running (needs Android 11+ for Wireless Debugging)
3. About 50MB free space for the ZIP output

## Troubleshooting
*Shizuku not running:* Make sure Wireless Debugging is on, then restart Shizuku
*Accessibility not enabled:* Go to Settings > Accessibility > AppLens > enable
*Extraction stops early:* Some apps have anti-automation, try a simpler app first
*ZIP not found:* Check Downloads/AppLens/ folder with a file manager app
