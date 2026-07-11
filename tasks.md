# Tasks

## Phase 1 — Project Scaffold
- [x] Initialize Android project (Kotlin, Jetpack Compose, min SDK 26, target SDK 35)
- [x] Add Hilt for dependency injection
- [x] Basic navigation graph (Inventory as home screen — no sign-in step)
- [ ] Add a Firebase project (`google-services.json`) — needed only for Firebase AI Logic + App Check, no Firestore, no Auth
  - _Code wiring scaffolded (git-ignored `google-services.json`, `.example` template, README setup steps, google-services plugin applied only when the file is present). Creating the actual Firebase project requires the Firebase console._
- [ ] Enable Firebase AI Logic and configure it for `gemini-2.0-flash`
  - _Code side done: Hilt provider (`di/FirebaseModule.kt`) supplies a `GenerativeModel` configured for `gemini-2.0-flash` via Firebase AI Logic. Enabling AI Logic on the project is a console step._
- [ ] Enable Firebase App Check with the Play Integrity provider
  - _Code side done: Play Integrity provider installed at startup (`HomeInventoryApp.kt`). Registering the app in App Check is a console step._

## Phase 2 — Background Photo Scanning & Permissions
- [ ] Add WorkManager dependency; implement a `PeriodicWorkRequest` worker (default every 6h, interval configurable in Settings)
- [ ] Worker queries `MediaStore.Images` for photos in `DCIM/Camera` with `DATE_ADDED` after `ScanCheckpoint.lastScanTimestamp`
- [ ] Implement `ScanCheckpoint` table (Room) and update it after each successful scan pass
- [ ] Add a "Scan now" action (Inventory + Settings) that triggers a `OneTimeWorkRequest` immediately
- [ ] Request `READ_MEDIA_IMAGES` (API 33+) / `READ_EXTERNAL_STORAGE` (API <33); show an in-app rationale screen explaining why, before triggering the OS permission dialog
- [ ] Detect and handle Android 14+ partial access (`READ_MEDIA_VISUAL_USER_SELECTED`): show an explainer + shortcut to Android's "Manage access" flow instead of silently under-scanning
- [ ] Add ML Kit Object Detection & Tracking (coarse classification enabled) and ML Kit Face Detection dependencies
- [ ] Implement the on-device pre-filter: send to Gemini if Object Detection finds a `home goods`/`fashion goods` classification above threshold; skip only when Face Detection finds a dominant face and no accepted category was found; default to "send it" when ambiguous
- [ ] On item detection: copy the source photo into app-private external storage (`getExternalFilesDir(DIRECTORY_PICTURES)`); never write to a shared MediaStore album
- [ ] Exclude the app-private photo directory from Android Auto Backup (`android:allowBackup` / `dataExtractionRules`)
- [ ] Show a notification when a scan finds new items, linking to the Review New Items screen

## Phase 3 — Gemini Integration (via Firebase Relay)
- [ ] Add Firebase AI Logic Android SDK dependency (no raw Gemini API key anywhere in the client)
- [ ] Compress photo to JPEG before sending through the relay (reduce cost/latency)
- [ ] Send photo bytes through Firebase AI Logic, authenticated with an App Check token — only for photos that passed the pre-filter
- [ ] Confirm the relay path is stateless: disable request-body logging in Cloud Functions/Cloud Logging so no image bytes are retained server-side
- [ ] Design and test item-extraction prompt — must return rich tags (color, brand, material, size, shape) not just item name
- [ ] Parse Gemini response into `InventoryItem` Room entities, linked via `photoId`
- [ ] Handle relay/Gemini errors gracefully (retry once, then leave photo for the next scan pass)
- [ ] Track Gemini calls against `UserProfile.creditBalance`; when balance is 0, skip the relay call and queue pre-filtered photos for later

## Phase 4 — Local Storage
- [ ] Define Room schema: `Photo` (UUID + privatePath + room? + timestamp), `InventoryItem` (UUID + photoId FK + labels), `Room`, `ScanCheckpoint`, `UserProfile` (creditBalance, storageTier, itemCount)
- [ ] On item detection: create `Photo` record with UUID + private storage path; attach all returned items with the same `photoId`
- [ ] Pre-populate `Room` table with common rooms on first launch (Kitchen, Garage, Bedroom, etc.)
- [ ] Implement FTS5 virtual table for full-text search over label/description/tags/category
- [ ] No cloud sync of any kind — Room is the sole source of truth for inventory and account data

## Phase 5 — Search & Inventory UI
- [ ] Inventory screen: photo grid loaded from `Photo.privatePath`, filterable by room/category
- [ ] Search screen: raw query → FTS5 prefix match first; only if zero results, fall back to Gemini preprocessing via the relay (spelling fix + synonym expansion), then re-run FTS5 with expanded terms
- [ ] Cache raw-query → expanded-terms mappings locally so a repeated typo never re-triggers the relay
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
- [ ] Skip relay/Gemini calls (queue photos instead) when credit balance is 0 or item cap is reached; show appropriate upsell prompt
- [ ] Show credit balance in Settings
- [ ] Build "Buy Credits" screen with pack options and Play purchase flow
- [ ] Document the accepted local-tampering limitation (see CLAUDE.md → Monetisation → Security) — no further mitigation planned

## Phase 7 — Play Store Compliance & Release Prep
- [ ] App icon and branding
- [ ] Empty state on Inventory screen (first launch onboarding + free trial callout + explanation of background scanning)
- [ ] Error handling: MediaStore permission denied/partial-access, relay/Gemini timeout, no network, payment failure
- [ ] Settings screen: scan interval, credit balance, clear local data (deletes private photo copies + database, never touches the camera roll)
- [ ] Write and host a Privacy Policy covering photo access and the stateless Gemini relay; link it from the Play listing
- [ ] Fill out the Play Console Data Safety section: declare "Photos and videos" as collected; confirm the relay's ephemeral processing qualifies it for the "service provider" exemption from third-party sharing disclosure
- [ ] Submit Play Console's Permissions Declaration Form for broad photo/video access, including a demo video of the background-scan feature; budget time for possible reject-and-resubmit
- [ ] Secrets audit: no Firebase config or API keys committed beyond the required `google-services.json`
- [ ] ProGuard / R8 rules for release build
- [ ] Write README with setup instructions
- [ ] Publish to internal test track on Google Play
