# HealthTrack - Project Overview & Agent Context

**Project:** HealthTrack
**Goal:** A broad, simple health logging application for tracking "anything and everything" (medications, food, stool, mood) via quick photo capture and notes.
**Core Philosophy:** Super simple, low friction. Keep sensitive health photos (e.g., medical issues, stool) separate from the user's main camera roll.

## 🛠 Tech Stack
*   **Platform:** Native Android (Kotlin)
*   **UI Framework:** Jetpack Compose (Material3)
*   **Build System:** Gradle (Kotlin DSL)
*   **Architecture:** Single Activity (`MainActivity`), local state management (no complex database yet, just JSON).
*   **Data Storage:** 
    *   Metadata: `logs.json` in internal storage.
    *   Images: `getExternalFilesDir(Environment.DIRECTORY_PICTURES)` (Private app storage).

## 🚀 Current MVP Features
1.  **Camera Capture:** Uses `ActivityResultContracts.TakePicture()` to launch system camera.
2.  **Private Gallery:** Images are stored in app-private directories, not the system Gallery.
3.  **Metadata:** Automatically captures Timestamp and Location (Lat/Long).
4.  **Logging:** Immediate prompt for text notes after photo capture.
5.  **History:** Date-based horizontal scroll selector to view past entries.
6.  **Persistence:** Data survives app restarts via JSON serialization.

## 💻 Environment & Build Setup
*   **JDK:** Java 17 (Required for this Gradle version).
*   **Android SDK:** API 35 (Android 15) target.
*   **Local Properties:** `local.properties` must point to the SDK path.
    *   **Linux:** `sdk.dir=/home/jonny/Android/Sdk`
    *   **macOS:** `sdk.dir=/Users/<username>/Library/Android/sdk`
    *   *Note: Do not commit `local.properties`.*

## 🍎 macOS Development Quirks
If cloning this repo on a MacBook Pro:
1.  **SDK Location:** Ensure `local.properties` is created/updated to point to the macOS SDK path (usually `~/Library/Android/sdk`).
2.  **Permissions:** macOS might require granting "Full Disk Access" or specific folder permissions to Android Studio or Terminal if builds fail with file access errors.
3.  **ADB:** Ensure `adb` is in your path (`brew install android-platform-tools` is handy if you don't install the full Studio).

## 🔮 Future Roadmap (Context for AI)
*   **Integrations:** Health Connect (Android) / HealthKit (iOS).
*   **Data Types:** Voice notes, pure text logs (no image).
*   **Cloud:** Sync functionality (currently 100% local).
*   **AI:** Analyzing the images (identifying food, reading medication labels).

## ⚠️ Important Files
*   `app/src/main/java/com/jonny/healthtrack/MainActivity.kt`: Contains **ALL** UI and Logic currently (Monolithic for MVP speed).
*   `app/src/main/AndroidManifest.xml`: Permissions (Camera, Location).

## 🐛 Known Quirks / "Watch Outs"
*   **Permissions:** The app currently asks for permissions (Camera/Location) on the fly. If denied, the features just silently fail or don't open.
*   **Location:** Relies on `FusedLocationProviderClient`. Requires GPS/Network to be active.
*   **Image Loading:** Uses Coil for async image loading.
*   **The "Experimental" Tag:** We are using `@OptIn(ExperimentalMaterial3Api::class)` on some Composables (like Cards/Scaffolds). This is normal for Compose but watch for breaking changes in future library updates.
