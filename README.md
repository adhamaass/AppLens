# AppLens

Extract any Android app's full UI structure — every screen, every component, every navigation path. No PC, no backend, no screenshots. Everything runs on-device.

## How It Works

1. AppLens uses Shizuku (ADB-level privileges) + AccessibilityService to traverse any installed app's screens
2. BFS traversal clicks every clickable element, dumps UIAutomator XML for each screen
3. Collects full app metadata (permissions, activities, services, providers, SDK info)
4. All processing happens on-device — XML parsing, wireframe SVG generation, pattern detection, navigation graph, Markdown report
5. Saves a ZIP file to your phone's Downloads/AppLens folder
6. Share the ZIP directly from the app

No PC needed. No backend server. No WiFi. No internet. 100% on-device.

## Project Structure

```
AppLens/
└── android/                          # Kotlin + Jetpack Compose (standalone app)
    └── app/src/main/java/com/applens/
        ├── ui/                        # 4 screens: Onboarding, AppPicker, Progress, Done
        ├── service/                   # AccessibilityService
        ├── engine/                    # BFS extraction engine
        ├── processor/                 # On-device processing (replaces backend)
        │   ├── XmlProcessor.kt        # UIAutomator XML → component tree
        │   ├── WireframeGenerator.kt  # Component tree → SVG wireframes
        │   ├── PatternDetector.kt     # Detects List, Form, BottomNav, etc.
        │   ├── NavGraphBuilder.kt     # Screen transitions → Mermaid graph
        │   ├── ReportGenerator.kt     # Full Markdown report
        │   └── ZipBuilder.kt          # Packages everything into ZIP
        ├── data/                      # Models & state
        └── util/                      # Shizuku manager
```

## Setup

1. Install Shizuku on your device: https://shizuku.rikka.app
2. Start Shizuku via Wireless Debugging (no PC needed — Android 11+)
3. Build AppLens: `./gradlew assembleDebug`
4. Install: `adb install app/build/outputs/apk/debug/app-debug.apk` (or transfer the APK to your phone)
5. Open AppLens → grant Shizuku permission → enable Accessibility Service
6. Pick any app → extraction runs automatically
7. Done screen shows summary + ZIP saved to Downloads/AppLens/

## ZIP Output Structure

```
output.zip
├── screens/          # Raw UIAutomator XML per screen
├── wireframes/       # SVG wireframes (boxes + labels, no screenshots)
├── components/       # Structured JSON component trees per screen
├── manifest/         # permissions, activities, services, providers, app_info
├── flow.mmd          # Mermaid navigation graph
└── report.md         # Full extraction report with stats
```

## Constraints
- No screenshots anywhere
- No AI or external APIs
- No PC or backend server required
- Everything deterministic from XML data
- Android min SDK 26
- Shizuku min version 12
- ZIP saved to device Downloads folder
