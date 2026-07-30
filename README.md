# AppLens

Extract any Android app's full UI structure — every screen, every component, every navigation path. No screenshots, no AI, purely deterministic.

## How It Works

1. **Android app** uses Shizuku (ADB-level privileges) + AccessibilityService to traverse any installed app's screens
2. BFS traversal clicks every clickable element, dumps UIAutomator XML for each screen
3. Collects full app metadata (permissions, activities, services, providers, SDK info)
4. Sends everything to a **Node.js backend** running on your PC (same WiFi)
5. Backend parses XML, generates wireframe SVGs, detects component patterns, builds navigation graph
6. Returns a **ZIP file** with everything organized

## Project Structure

```
AppLens/
├── android/          # Kotlin + Jetpack Compose Android app
│   └── app/src/main/java/com/applens/
│       ├── ui/              # 4 screens: Onboarding, AppPicker, Progress, Done
│       ├── service/         # AccessibilityService
│       ├── engine/          # BFS extraction engine
│       ├── network/         # Retrofit HTTP client
│       ├── data/            # Models & state
│       └── util/            # Shizuku manager
└── server/          # Node.js + Express backend
    ├── index.js             # POST /analyze endpoint
    ├── xmlParser.js         # XML → component tree
    ├── wireframeGenerator.js # Component tree → SVG
    ├── patternDetector.js   # Detects List, Form, BottomNav, etc.
    ├── navGraphBuilder.js   # Screen transitions → Mermaid graph
    └── reportGenerator.js   # Full Markdown report
```

## Setup

### Backend (your PC)
```bash
cd server
npm install
npm start
# Runs on http://0.0.0.0:3000
```

### Android App
1. Install [Shizuku](https://shizuku.rikka.app) on your device
2. Start Shizuku via Wireless Debugging
3. Build & install AppLens: `./gradlew assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk`
4. Open AppLens → grant Shizuku permission → enable Accessibility Service
5. Enter your PC's IP address
6. Pick any app → extraction runs automatically
7. Done screen shows summary + ZIP download

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
- Everything deterministic from XML data
- Android min SDK 26
- Shizuku min version 12
- Backend runs locally on same WiFi
