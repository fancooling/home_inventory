# Tasks

## Phase 1 — Project Scaffold & Auth
- [ ] Initialize Android project (Kotlin, Jetpack Compose, min SDK 26)
- [ ] Add Hilt for dependency injection
- [ ] Add Firebase to the project (`google-services.json`, Firebase Auth)
- [ ] Implement Google Sign-In via Firebase Auth
- [ ] Store and refresh Google OAuth token for Google Photos scope
- [ ] Basic navigation graph (Sign-In → Home)

## Phase 2 — Google Photos Integration
- [ ] Add Google Photos Library API dependency and Retrofit client
- [ ] Implement album listing screen (show user's Google Photos albums)
- [ ] Implement photo listing within a selected album
- [ ] Allow user to select albums or individual photos for scanning
- [ ] Fetch photo base URLs and metadata from Google Photos API
- [ ] Store photo records in Room database (`Photo` table)

## Phase 3 — Gemini API Integration
- [ ] Add Gemini SDK or Retrofit client for `gemini-2.0-flash`
- [ ] Design and test item-extraction prompt (returns structured JSON)
- [ ] Implement single-photo scan: send photo → parse Gemini JSON response
- [ ] Map Gemini response to `InventoryItem` Room entities
- [ ] Implement WorkManager job for background batch scanning
- [ ] Add rate-limiting / retry logic to respect Gemini API quotas
- [ ] Show scan progress in the UI (photos scanned / total)

## Phase 4 — Local Storage & Cloud Sync
- [ ] Define Room schema: `Photo`, `InventoryItem`, `Location` tables
- [ ] Implement FTS5 virtual table for full-text search
- [ ] Implement Firestore sync (upload inventory items per user UID)
- [ ] Offline-first: reads from Room, writes sync to Firestore in background
- [ ] Location management: create / rename / delete locations
- [ ] Allow user to tag a photo with a location

## Phase 5 — Search & Inventory UI
- [ ] Home screen: search bar + recent items grid
- [ ] Search screen: FTS query against Room, display photo grid results
- [ ] Filter inventory by category and location
- [ ] Photo detail screen: full image + item list + location tag editor
- [ ] Inventory screen: paginated grid of all items

## Phase 6 — Polish & Release Prep
- [ ] App icon and branding
- [ ] Empty states (no photos scanned, no search results)
- [ ] Error handling: network failures, Gemini errors, Photos API errors
- [ ] Settings screen: account info, re-scan library, clear local data
- [ ] Secrets audit: ensure no API keys in version control
- [ ] ProGuard / R8 rules for release build
- [ ] Write README with setup instructions (API keys, Firebase config)
- [ ] Publish to internal test track on Google Play
