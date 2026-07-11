# Tasks

## Phase 1 — Project Scaffold
- [ ] Initialize Android project (Kotlin, Jetpack Compose, min SDK 26)
- [ ] Add Hilt for dependency injection
- [ ] Basic navigation graph (Inventory as home screen — no sign-in step)

## Phase 2 — Background Photo Scanning
- [ ] Add WorkManager dependency; implement a `PeriodicWorkRequest` worker (default every 6h, interval configurable in Settings)
- [ ] Worker queries `MediaStore.Images` for photos in `DCIM/Camera` with `DATE_ADDED` after `ScanCheckpoint.lastScanTimestamp`
- [ ] Implement `ScanCheckpoint` table (Room) and update it after each successful scan pass
- [ ] Add a "Scan now" action (Inventory + Settings) that triggers a `OneTimeWorkRequest` immediately
- [ ] Add ML Kit Image Labeling dependency; implement the on-device pre-filter (skip Gemini only when high-confidence person/pet/scenery label; default to "send it" when ambiguous)
- [ ] On item detection: copy the source photo into app-private internal storage (`filesDir/photos/`); never write to a shared MediaStore album
- [ ] Show a notification when a scan finds new items, linking to the Review New Items screen

## Phase 3 — Gemini Integration
- [ ] Add Gemini Android SDK dependency
- [ ] Compress photo to JPEG before sending to Gemini (reduce cost/latency)
- [ ] Send photo bytes directly from device to Gemini (`gemini-2.0-flash`) — only for photos that passed the pre-filter
- [ ] Design and test item-extraction prompt — must return rich tags (color, brand, material, size, shape) not just item name
- [ ] Parse Gemini response into `InventoryItem` Room entities, linked via `photoId`
- [ ] Handle Gemini errors gracefully (retry once, then leave photo for the next scan pass)
- [ ] Track Gemini calls against `UserProfile.creditBalance`; when balance is 0, skip Gemini and queue pre-filtered photos for later

## Phase 4 — Local Storage
- [ ] Define Room schema: `Photo` (UUID + privatePath + room? + timestamp), `InventoryItem` (UUID + photoId FK + labels), `Room`, `ScanCheckpoint`, `UserProfile` (creditBalance, storageTier, itemCount)
- [ ] On item detection: create `Photo` record with UUID + private storage path; attach all returned items with the same `photoId`
- [ ] Pre-populate `Room` table with common rooms on first launch (Kitchen, Garage, Bedroom, etc.)
- [ ] Implement FTS5 virtual table for full-text search over label/description/tags/category
- [ ] No cloud sync of any kind — Room is the sole source of truth

## Phase 5 — Search & Inventory UI
- [ ] Inventory screen: photo grid loaded from `Photo.privatePath`, filterable by room/category
- [ ] Search screen: raw query → FTS5 prefix match first; only if zero results, fall back to Gemini preprocessing (spelling fix + synonym expansion), then re-run FTS5 with expanded terms
- [ ] Cache raw-query → expanded-terms mappings locally so a repeated typo never re-triggers Gemini
- [ ] Rank search results by FTS5 relevance score (bm25)
- [ ] Review New Items screen: shown after a scan finds items; lets the user optionally assign a room per photo, or skip entirely
- [ ] Item detail screen: photo (from `privatePath`) + all items linked to that photo + editable, optional room field
- [ ] Rooms management screen: add, rename, delete rooms

## Phase 6 — Monetisation
- [ ] Set up Google Play In-App Products: 3 scan credit packs + 3 storage tier upgrades (Standard/Large/Unlimited)
- [ ] Integrate Google Play Billing Library into the app
- [ ] Store credit balance, storage tier, and item count in the local `UserProfile` Room table only — no server-side tracking
- [ ] On first launch: new local profile granted 20 free trial credits
- [ ] Verify purchases on-device using Play Billing Library's purchase signature verification against the bundled Play Console public key (no backend call)
- [ ] Skip Gemini calls (queue photos instead) when credit balance is 0 or item cap is reached; show appropriate upsell prompt
- [ ] Show credit balance in Settings
- [ ] Build "Buy Credits" screen with pack options and Play purchase flow
- [ ] Document the accepted local-tampering limitation (see CLAUDE.md → Monetisation → Security) — no further mitigation planned

## Phase 7 — Polish & Release Prep
- [ ] App icon and branding
- [ ] Empty state on Inventory screen (first launch onboarding + free trial callout + explanation of background scanning)
- [ ] Error handling: MediaStore permission denied, Gemini timeout, no network, payment failure
- [ ] Settings screen: scan interval, credit balance, clear local data (deletes private photo copies + database, never touches the camera roll)
- [ ] Restrict `GEMINI_API_KEY` in Google Cloud Console to this app's package name + signing SHA-1
- [ ] Secrets audit: no API keys committed
- [ ] ProGuard / R8 rules for release build
- [ ] Write README with setup instructions
- [ ] Publish to internal test track on Google Play
