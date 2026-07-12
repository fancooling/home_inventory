# Home Inventory

An Android app that builds a searchable, on-device index of your belongings from photos you
already take with your phone's normal camera. See [CLAUDE.md](CLAUDE.md) for the full design and
[tasks.md](tasks.md) for the phased build plan.

## Status

**Phase 1 — Project Scaffold** (in progress). The app compiles to a navigable shell:

- Kotlin + Jetpack Compose, min SDK 26, target SDK 35
- Hilt dependency injection (`@HiltAndroidApp`, `SingletonComponent` module)
- Navigation graph with **Inventory as the home screen** (no sign-in) and placeholder
  destinations for every screen in the design
- Firebase AI Logic (Gemini relay) + App Check (Play Integrity) wiring — dependencies, DI
  provider configured for `gemini-2.0-flash`, and App Check installed at startup

Background scanning, the on-device pre-filter, Gemini calls, Room storage, search, and
monetisation arrive in later phases (see [tasks.md](tasks.md)).

## Prerequisites

- JDK 17
- Android SDK with platform 35
- A Firebase project (needed for the Gemini relay + App Check — **no Firestore, no Auth**)

## Firebase setup (required before Gemini features run)

The Gemini API key never ships in the app; a Firebase project brokers the calls instead.

1. Create a Firebase project in the [Firebase console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.homeinventory.app`.
3. Enable **Firebase AI Logic** (Vertex AI in Firebase) and confirm `gemini-2.0-flash` access.
4. Enable **App Check** with the **Play Integrity** provider.
5. Download `google-services.json` and place it at `app/google-services.json`.

`app/google-services.json` is **git-ignored and must never be committed** — a template lives at
[`app/google-services.json.example`](app/google-services.json.example). The Gradle build applies
the `google-services` plugin only when the real file is present, so the project still configures
without it (Gemini/App Check calls simply no-op or throw until it's added).

## Build & run

```bash
./gradlew assembleDebug     # build the debug APK
./gradlew test              # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
```

## Module layout

```
app/
  src/main/java/com/homeinventory/app/
    HomeInventoryApp.kt        # @HiltAndroidApp + App Check init
    MainActivity.kt            # Compose host
    di/FirebaseModule.kt       # Hilt provider for the Gemini model
    ui/navigation/             # Destinations + NavHost (Inventory = start)
    ui/screens/                # Inventory + placeholder screens
    ui/theme/                  # Material 3 theme
```
