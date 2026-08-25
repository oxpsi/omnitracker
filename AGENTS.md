# HealthTrack - Project Overview & Agent Context

**Project:** HealthTrack
**Goal:** A broad, simple health logging application for tracking "anything and everything" (medications, food, stool, mood) via quick photo capture and notes.
**Core Philosophy:** Super simple, low friction. Keep sensitive health photos (e.g., medical issues, stool) separate from the user's main camera roll.

## 🛠 Tech Stack
*   **Platform:** Native Android (Kotlin)
*   **UI Framework:** Jetpack Compose (Material3)
*   **Build System:** Gradle (Kotlin DSL)
*   **Architecture:** Single Activity (`MainActivity`), local state management (Room Database).
*   **Data Storage:** 
    *   Metadata: SQLite via Room (`healthtrack_database`).
    *   Images: `getExternalFilesDir(Environment.DIRECTORY_PICTURES)` (Private app storage).
*   **AI Analysis:** Single OpenAI-compatible chat completions provider (user-configurable base URL, API key, organization, model). NO more Gemini, Responses API, or dual-provider fallback.

## 🚀 Current MVP Features
1.  **Camera Capture:** Uses `ActivityResultContracts.TakePicture()` to launch system camera.
2.  **Private Gallery:** Images are stored in app-private directories, not the system Gallery.
3.  **Metadata:** Automatically captures Timestamp and Location (Lat/Long).
4.  **Logging:** Immediate prompt for text notes after photo capture.
5.  **History:** Date-based horizontal scroll selector to view past entries.
6.  **AI Analysis:** Analyzes photos to extract Title, Type (Food/Medicine/etc), and Components (Ingredients/Qty).
    *   Supports structured data extraction.
    *   User-configured endpoint via in-app Settings (base URL, API key, organization, model). Settings persist in SharedPreferences (`ai_settings`) and survive app updates.
7.  **Persistence:** Data survives app restarts via Room Database.
8.  **Import/Export:** Supports JSONL (Lite) and ZIP (Full with images) backup/restore.

## 💻 Environment & Build Setup
This project is designed to be built on **Linux (Ubuntu)** and **macOS**.

*   **JDK:** Java 17 (Required).
*   **Android SDK:** API 35 (Android 15) target.
*   **Local Properties:** You **MUST** create a `local.properties` file in the root directory. This file is git-ignored.

**`local.properties` Configuration:**
```properties
# Path to your Android SDK
# Linux Example:
sdk.dir=/home/<username>/Android/Sdk
# macOS Example:
sdk.dir=/Users/<username>/Library/Android/sdk
```

## 🔨 Build & Run Commands

**1. Build & Install to Emulator/Device:**
This command builds the debug APK and installs it immediately on the connected device.
```bash
./gradlew installDebug
```

**2. Build APK Only:**
Generates the APK file without installing.
```bash
./gradlew assembleDebug
```
*   **Output Location:** `app/build/outputs/apk/debug/app-debug.apk`

**3. Clean Build:**
Use this if you encounter weird compilation errors or after changing strict build configurations.
```bash
./gradlew clean installDebug
```

**4. Common ADB Commands:**
```bash
# Check connected devices
adb devices

# View live error logs
adb logcat *:E
```

> **⚠️ After any code change:** Always do a full rebuild (`./gradlew clean installDebug`) and re-open/restart the emulator with the new build to verify changes. Do not assume a stale running app reflects your edits.

## 🍎 macOS Development Quirks
If cloning this repo on a MacBook Pro:
1.  **SDK Location:** Double-check `local.properties` points to `~/Library/Android/sdk`.
2.  **Permissions:** If builds fail with file access errors, ensure Terminal/Android Studio has "Full Disk Access".
3.  **ADB:** Install via brew if missing: `brew install android-platform-tools`.

## 🔮 Future Roadmap (Context for AI)
*   **Integrations:** Health Connect (Android) / HealthKit (iOS).
*   **Cloud:** Sync functionality (currently 100% local).
*   **Wear OS:** Quick log from watch.

## ⚠️ Important Files
*   `app/src/main/java/com/jonny/healthtrack/MainActivity.kt`: Main UI and Navigation.
*   `app/src/main/java/com/jonny/healthtrack/ai/AiAnalysisService.kt`: Logic for choosing AI provider.
*   `app/src/main/java/com/jonny/healthtrack/ai/providers/`: provider interface and `ChatCompletionsProvider` implementation (chat completions endpoint, includes `discoverModels`).
*   `app/src/main/java/com/jonny/healthtrack/data/LogRepository.kt`: Data handling and AI invocation.

## 🐛 Known Quirks / "Watch Outs"
*   **Permissions:** The app asks for Camera/Location permissions on usage. If denied, features silently fail.
*   **Location:** Relies on `FusedLocationProviderClient`. Requires GPS/Network to be active.
*   **AI Quotas:** Depending on the configured endpoint/provider, you may hit rate limits (e.g. 429). Adjust the base URL/model in Settings or wait.
