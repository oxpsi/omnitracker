# OmniTracker

A broad, simple health-logging app for tracking "anything and everything" with a focus on **nutrition** — snap a photo of a meal, supplement, or anything else, and get an automated nutrition and component breakdown alongside your log. Also tracks medications, stool, mood, and general wellness entries via quick photo capture and notes.

**Works with any multimodal chat-completions endpoint.** OmniTracker speaks the standard OpenAI chat-completions API, so any provider that supports vision/image inputs (OpenAI, OpenRouter, local Ollama/Llama.cpp with vision models, LM Studio, etc.) works out of the box. You configure base URL, API key, organization, and model in-app; nothing is hardcoded to a specific vendor.

**Core philosophy:** super simple, low friction. Sensitive health photos stay in app-private storage, separate from your main camera roll.

## Features

- **Camera capture** — `ActivityResultContracts.TakePicture()` launches the system camera.
- **Private gallery** — images are stored in app-private directories, not the system Gallery.
- **Automatic metadata** — timestamp and location (lat/long) captured per entry.
- **Quick notes** — immediate text-note prompt after each capture.
- **History** — date-based selector to browse past entries, with day-level nutrition rollups (calories, macros, component aggregation, and caloric-contribution percentages).
- **Nutrition analysis** — the AI extracts per-entry Title, Type (food/medicine/etc.), and Components (ingredients with quantities), enabling calorie and macro estimates per log and per day.
- **AI analysis (bring your own endpoint)** — uses any multimodal OpenAI-compatible chat-completions endpoint (base URL, API key, organization, model all user-configurable in Settings). Supports a model discovery list when the endpoint exposes one. Settings persist across app updates.
- **Persistence** — Room database; data survives app restarts.
- **Import / Export** — JSONL (lite, metadata only) and ZIP (full, with images) backup/restore.

## Tech stack

- Native Android (Kotlin), Jetpack Compose (Material3)
- Gradle (Kotlin DSL), single-activity architecture
- Room (SQLite) for metadata; app-private picture storage for images
- WorkManager for background AI analysis

## Build & run

Requires **JDK 17** and the **Android SDK** (target API 35). Create a `local.properties` in the repo root (git-ignored) pointing at your SDK:

```properties
# Linux
sdk.dir=/home/<username>/Android/Sdk
# macOS
sdk.dir=/Users/<username>/Library/Android/sdk
```

Build and install to a connected device/emulator:

```bash
./gradlew installDebug
```

Build the APK only:

```bash
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/omnitracker.apk
```

After any code change, do a clean rebuild (`./gradlew clean installDebug`) and restart the app to verify.

## Notes

- The app requests Camera/Location permissions on use; if denied, those features silently fail.
- Location relies on `FusedLocationProviderClient` and requires GPS/network to be active.
- AI features depend on your configured endpoint; you may hit rate limits (e.g. 429). Adjust the base URL/model in Settings or wait.

## License

MIT — see [LICENSE](LICENSE).
