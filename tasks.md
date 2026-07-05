# Tasks

## Phase 1 — Project Scaffold & Auth
- [ ] Initialize Android project (Kotlin, Jetpack Compose, min SDK 26)
- [ ] Add Hilt for dependency injection
- [ ] Add Firebase to the project (`google-services.json`, Firebase Auth)
- [ ] Implement Google Sign-In via Firebase Auth
- [ ] Basic navigation graph (Sign-In → Camera)

## Phase 2 — Camera & Photo Storage
- [ ] Add CameraX dependency and implement camera screen
- [ ] Add room selector chip to camera screen (persists last selected room)
- [ ] On capture: save photo to `Pictures/Home Inventory/` via Android MediaStore
- [ ] Confirm photo appears in Google Photos under "Home Inventory" album
- [ ] Store device photo URI (`content://media/...`) for later display

## Phase 3 — Gemini Integration
- [ ] Add Gemini Android SDK dependency
- [ ] Compress photo to JPEG before sending to Gemini (reduce cost/latency)
- [ ] Send photo bytes directly from device to Gemini (`gemini-2.0-flash`)
- [ ] Design and test item-extraction prompt — must return rich tags (color, brand, material, size, shape) not just item name
- [ ] Parse Gemini response into `InventoryItem` Room entities, attaching room from selector
- [ ] Show "Scanning..." indicator on camera screen; allow more photos to be taken while scanning
- [ ] Handle Gemini errors gracefully (retry once, then surface to user)

## Phase 4 — Local Storage & Cloud Sync
- [ ] Define Room schema: `Photo` table (UUID + devicePhotoUri + room + timestamp), `InventoryItem` table (UUID + photoId FK + labels), `Room` table
- [ ] On capture: create `Photo` record with UUID before sending to Gemini; use that UUID as the link for all items returned
- [ ] Pre-populate `Room` table with common rooms on first launch (Kitchen, Garage, Bedroom, etc.)
- [ ] Implement FTS5 virtual table for full-text search over label/description/tags/category
- [ ] Implement Firestore sync (text data only — `Photo` references + `InventoryItem` records per user UID, no image bytes)
- [ ] Offline-first: write to Room DB immediately; sync to Firestore in background via WorkManager

## Phase 5 — Search & Inventory UI
- [ ] Inventory screen: photo grid loaded from device using `devicePhotoUri`, filterable by room/category
- [ ] Search screen: raw query → Gemini preprocessing (spelling fix + synonym expansion) → FTS5 OR match → join `Photo` via `photoId` → load image from `devicePhotoUri`
- [ ] Implement offline fallback: skip Gemini preprocessing, run raw query through FTS5 directly
- [ ] Rank search results by FTS5 relevance score (bm25)
- [ ] Item detail screen: photo (loaded from device via `devicePhotoUri`) + all items linked to that photo + editable room field
- [ ] Handle case where device photo has been deleted (show placeholder)
- [ ] Rooms management screen: add, rename, delete rooms

## Phase 6 — Monetisation
- [ ] Set up Google Play In-App Products: 3 scan credit packs + 3 storage tier upgrades (Standard/Large/Unlimited)
- [ ] Integrate Google Play Billing Library into the app
- [ ] Store credit balance and storage tier in Firestore per user UID; cache in Room for offline display
- [ ] On app launch: new users granted 20 free trial credits
- [ ] Write Firestore Security Rules: profile doc (credits/storageTier/itemCount) is client read-only; items/photos/rooms are owner read-write
- [ ] Cloud Function `reserveCredit`: check balance, deduct 1, return one-time scan token
- [ ] Cloud Function `commitScan`: validate scan token, write Gemini results to Firestore, increment item count
- [ ] Cloud Function `refundCredit`: restore 1 credit if Gemini call failed
- [ ] Cloud Function `verifyPurchase`: verify Google Play token via Play Developer API, add credits or upgrade storage tier
- [ ] App calls `reserveCredit` before Gemini, `commitScan` after, `refundCredit` on failure — never writes credits directly
- [ ] Disable camera shutter when credit balance is 0 or item cap is reached; show appropriate upsell prompt
- [ ] Show credit balance on camera screen and in Settings
- [ ] Build "Buy Credits" screen with pack options and Play purchase flow

## Phase 7 — Polish & Release Prep
- [ ] App icon and branding
- [ ] Empty state on camera screen (first launch onboarding + free trial callout)
- [ ] Error handling: camera permission denied, Gemini timeout, no network, payment failure
- [ ] Settings screen: account info, credit balance, clear local inventory
- [ ] Secrets audit: no API keys committed
- [ ] ProGuard / R8 rules for release build
- [ ] Write README with setup instructions
- [ ] Publish to internal test track on Google Play
