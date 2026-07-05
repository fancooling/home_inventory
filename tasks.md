# Tasks

## Phase 1 — Project Scaffold & Auth
- [ ] Initialize Android project (Kotlin, Jetpack Compose, min SDK 26)
- [ ] Add Hilt for dependency injection
- [ ] Add Firebase to the project (`google-services.json`, Firebase Auth)
- [ ] Implement Google Sign-In via Firebase Auth
- [ ] Basic navigation graph (Sign-In → Camera)

## Phase 2 — Camera & Gemini Integration
- [ ] Add CameraX dependency and implement camera screen
- [ ] Capture photo and save to device storage
- [ ] Compress photo to JPEG before sending to Gemini (reduce API cost)
- [ ] Add Gemini SDK / Retrofit client for `gemini-2.0-flash`
- [ ] Design and test item + room inference prompt (returns structured JSON)
- [ ] Send photo bytes directly from device to Gemini (never via our servers)
- [ ] Parse Gemini JSON response into `InventoryItem` entities
- [ ] Show "Scanning..." indicator; allow user to keep taking photos while scanning runs
- [ ] Handle Gemini errors gracefully (retry once, then surface error to user)

## Phase 3 — Local Storage & Cloud Sync
- [ ] Define Room schema: `InventoryItem` table only (no photo storage)
- [ ] Implement FTS5 virtual table for full-text search over label/description/tags/room
- [ ] Store `devicePhotoId` (local asset URI) as reference to display photos from device
- [ ] Implement Firestore sync (text data only — item records per user UID)
- [ ] Offline-first: writes go to Room immediately; sync to Firestore in background via WorkManager

## Phase 4 — Search & Inventory UI
- [ ] Camera screen as the default home screen
- [ ] Inventory screen: photo grid loaded from device using `devicePhotoId`, filterable by inferred room / category
- [ ] Search bar: FTS5 query, results shown as photo grid
- [ ] Item detail screen: photo (loaded from device) + item list + optional room correction tap
- [ ] Handle case where device photo has been deleted (show placeholder)

## Phase 5 — Polish & Release Prep
- [ ] App icon and branding
- [ ] Empty state (no items scanned yet)
- [ ] Error handling: no camera permission, Gemini timeout, no network
- [ ] Settings screen: account info, clear local inventory data
- [ ] Secrets audit: ensure no API keys committed
- [ ] ProGuard / R8 rules for release build
- [ ] Write README with setup instructions
- [ ] Publish to internal test track on Google Play
