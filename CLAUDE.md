# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**home_inventory** is an app that helps users build a searchable index of their home
items. Users take photos of their belongings (electronics, tools, documents, kitchenware,
keepsakes, etc.) using their phone's normal camera app — nothing special, just their everyday
photo habits. In the background, home_inventory periodically scans new photos, identifies and
labels the belongings in them, and records them locally. Later on, users can search for
belongings by typing any descriptive words, and the app will show the matching photos.

Inventory data, photos, and search all stay on the user's device — there is no account and no
cloud sync of any kind. The one exception: a pre-filtered photo passes through a stateless
Firebase relay on its way to Gemini for item recognition, purely so the Gemini API key never
ships inside the app. The relay retains nothing.

---

## Architecture & Design

### Core Principles
- **No in-app camera** — the phone's native camera app is the only way photos get taken; home_inventory only reads what's already in the camera roll
- **Local data, stateless cloud relay only for Gemini calls** — no Firestore, no user accounts, no server-side storage of any kind. All inventory data (items, rooms, credits) lives in a local SQLite (Room) database on the device. The one thing that leaves the device is the specific, pre-filtered photo sent through a **stateless Firebase relay** to reach Gemini — this exists solely so the Gemini API key never ships inside the APK (see Gemini Integration below). The relay does not log, cache, or persist the image or the response
- **Scheduled background scanning** — a background job runs every N hours (user-configurable), finds new native-camera photos since the last scan, and processes them automatically. A manual "Scan now" action is also available
- **On-device pre-filter before Gemini** — a small on-device ML model screens out photos that are clearly not home objects (selfies, people, pets, outdoor scenery) before spending a Gemini call on them, since Gemini calls are the app's real cost
- **Optional, not mandatory, room tagging** — once an item is detected, the user can optionally tag which room it's in; nothing blocks on this

### High-Level Flow

```
User takes photos normally, with their phone's native camera app (no interaction with home_inventory)
  → background job wakes up every N hours (or user taps "Scan now")
  → job queries MediaStore for new camera photos since the last scan checkpoint
  → each new photo runs through an on-device pre-filter model
      → clearly not a home object (person/pet/scenery), high confidence → skip, no Gemini call
      → otherwise → sent through the stateless Firebase relay to Gemini for item identification
        (the relay only proxies the request/response; it retains nothing)
  → Gemini returns: items identified in the photo
  → matching photo is copied into the app's own private storage (so it survives the user
    deleting/moving the original) and TEXT results are stored in the local database
  → user gets a notification if new items were found; can optionally assign a room, or leave it for later
  → user can search immediately
```

### What We Store

| Stored                                  | Not Stored                    |
|------------------------------------------|--------------------------------|
| Item labels, descriptions & tags          | Photo bytes, on the device or off it, beyond the private copy described below |
| Room (optional, user-assigned)            | Any inventory data on a server — there is no data-storing server |
| A private copy of photos with detected items | Original, un-tagged photos (left alone in the camera roll) |
| Timestamp                                 | Anything retained by the Gemini relay — it's stateless and discards each image immediately after forwarding Gemini's response |

Everything above lives in a local Room (SQLite) database. Nothing is synced anywhere. The only
data that transits off-device at all is the individual photo sent to the stateless Gemini relay
at scan time — see Gemini Integration.

### Photo Storage Strategy

Android has no "Pictures/Home Inventory/" convention — that's a Windows-style path and doesn't
apply here. The correct approach on Android:

- The background job reads original photos from the standard camera folder via `MediaStore`
  (typically `DCIM/Camera`) — it does **not** move or modify the originals
- When Gemini finds items in a photo, the app copies that photo into its **own app-private
  external storage** (`context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)`) rather
  than internal storage (`filesDir`) — the external partition is usually much larger than the
  internal one (which can be tightly constrained on lower-end devices), and an inventory app
  scanning years of photos needs the headroom
  - This directory still isn't a shared MediaStore album — it's app-private, not visible to the
    Gallery, Google Photos, or other apps, and is automatically deleted if the app is uninstalled
  - It must be explicitly excluded from Android Auto Backup (`android:allowBackup` /
    `dataExtractionRules`), otherwise these private copies could otherwise get swept into the
    user's cloud device backup — which would silently violate the "stays on this device" principle
  - Copying (rather than only referencing) the original means the item stays in the inventory
    even if the user later deletes or moves the original from the camera roll
- If the user wants to reclaim space, "Clear local data" in Settings deletes these private
  copies along with the database — originals in the camera roll are never touched

---

### Tech Stack

| Layer                | Technology                                                        |
|-----------------------|--------------------------------------------------------------------|
| UI                    | Jetpack Compose                                                    |
| Language              | Kotlin                                                             |
| Auth                  | None — no user accounts, no sign-in                                |
| Photo source          | Native camera app + `MediaStore` query — no in-app camera          |
| Background scanning   | WorkManager periodic worker (default every 6h, user-configurable) + on-demand "Scan now" |
| On-device pre-filter  | ML Kit Object Detection & Tracking (coarse classifier) + ML Kit Face Detection — see On-Device Pre-Filter below |
| Gemini relay          | Firebase AI Logic — stateless proxy so the Gemini API key never ships in the client |
| Anti-abuse            | Firebase App Check (Play Integrity) — attests the calling app is a genuine install; no user account required |
| AI recognition        | Gemini API — `gemini-2.0-flash` (multimodal), via the relay, only for photos that pass the pre-filter |
| Local storage         | Room (SQLite) — sole source of truth: photos, items, rooms, credits |
| Cloud sync            | None — no Firestore, no account data, no inventory data stored off-device. The Firebase relay is stateless and touches only the in-flight photo, nothing else |
| Networking            | Retrofit + OkHttp / Firebase SDK (relayed Gemini calls only)       |
| Image display         | Coil (reads from app-private external storage)                    |
| DI                    | Hilt                                                               |
| Purchases             | Google Play Billing Library (locally-verified, no backend)        |
| Background work       | WorkManager                                                       |

---

### Data Model

#### `Photo`
| Field         | Type   | Notes                                                        |
|---------------|--------|----------------------------------------------------------------|
| id            | String | UUID — the link code shared by all items in this photo        |
| privatePath   | String | Path under the app-private external Pictures dir — the app's own copy of the photo |
| room          | String? | Optional, user-assigned; null until tagged                    |
| timestamp     | Long   | When the photo was taken (from MediaStore `DATE_ADDED`)        |

#### `InventoryItem`
| Field       | Type   | Notes                                           |
|-------------|--------|---------------------------------------------------|
| id          | String | UUID                                            |
| photoId     | String | FK → Photo.id — the link back to the photo      |
| label       | String | Primary item name (e.g. "Black cordless drill") |
| category    | String | e.g. "Tools", "Electronics", "Documents"        |
| description | String | Gemini's natural-language description           |
| tags        | List   | Additional search keywords                      |

#### `Room`
| Field | Type   | Notes                                    |
|-------|--------|------------------------------------------|
| id    | String | UUID                                     |
| name  | String | e.g. "Garage", "Kitchen", "Loft storage" |

#### `ScanCheckpoint`
| Field              | Type | Notes                                                        |
|--------------------|------|-----------------------------------------------------------------|
| lastScanTimestamp  | Long | MediaStore `DATE_ADDED` watermark; only photos newer than this are considered on the next scan |

#### `UserProfile` (local only — see Monetisation)
| Field         | Type | Notes                                   |
|---------------|------|-------------------------------------------|
| creditBalance | Int  | Scan credits remaining                    |
| storageTier   | Enum | Free / Standard / Large / Unlimited       |
| itemCount     | Int  | Cached count, used against the storage tier cap |

**How the link works:**
1. Background scan finds a new photo → sent to Gemini (if it passes the pre-filter) → if items are found, a `Photo` record is created with a UUID and its own private copy of the image
2. Gemini returns 3 items from that photo → 3 `InventoryItem` records created, all with the same `photoId`
3. User searches "drill" → `InventoryItem` found → `photoId` looked up → `Photo.privatePath` used to load the image
4. Room stays optional — the user can tag it from the "new items" review flow or from the item detail screen at any time; leaving it untagged doesn't block anything

User manages their own room list. Pre-populated with common rooms on first launch.

---

### Background Scanning

- A `WorkManager` `PeriodicWorkRequest` runs every N hours (default 6, configurable in Settings; WorkManager's practical minimum is 15 minutes)
- Each run:
  1. Reads `ScanCheckpoint.lastScanTimestamp`
  2. Queries `MediaStore.Images` for photos in the native camera folder (`DCIM/Camera`) with `DATE_ADDED` after the checkpoint — this deliberately excludes screenshots, downloads, and messaging-app images
  3. For each new photo, runs it through the on-device pre-filter (see below)
  4. Photos that pass are sent to Gemini for item identification; results are written to the local database and the photo is copied to app-private storage
  5. Updates the checkpoint to the newest `DATE_ADDED` seen
  6. If any items were found, shows a notification ("3 new items found") linking to a lightweight review screen where the user can optionally assign rooms
- A "Scan now" button in Settings/Inventory triggers the same logic immediately via a `OneTimeWorkRequest`, without waiting for the schedule

### Permissions & Play Store Compliance

Background scanning of the whole camera roll is the app's core feature, but broad photo access
is exactly what Google Play's **Photo and Video Permissions policy** restricts by default —
this needs explicit handling, not just a manifest entry.

- **Permission requested:** `READ_MEDIA_IMAGES` (API 33+) / `READ_EXTERNAL_STORAGE` (API <33).
  Google's default expectation is to use the Android **Photo Picker** instead of a broad
  permission, but Photo Picker only grants access to photos the user explicitly selects *at
  that moment* — it cannot see future new photos, which makes it fundamentally incompatible
  with "scan new photos automatically every N hours." That puts this app in the same policy
  category as gallery, backup, and photo-management apps, which Google does allow broad access
  for — but only after review
- **Permissions Declaration Form:** before publishing, submit Play Console's declaration for
  broad photo/video access, including a **demo video** showing the background-scan feature and
  why it needs ongoing access. Expect a longer review cycle than a typical app, and budget for
  at least one reject-and-resubmit if the justification isn't unambiguous
- **In-app rationale screen:** show a short explainer of why photo access is needed *before*
  triggering the OS permission dialog (first-run onboarding) — this is both a Play review
  expectation and better UX than an unexplained system prompt
- **Android 14+ partial access (`READ_MEDIA_VISUAL_USER_SELECTED`):** on API 34+, a user can
  grant access to only a subset of photos instead of the whole library. The app must detect this
  state and handle it gracefully — surface a clear explainer plus a shortcut to Android's "Manage
  access" flow — rather than silently under-scanning and leaving the user wondering why new
  items aren't appearing
- **Data Safety section:** must declare "Photos and videos" as collected data. Data that a
  developer's own "service provider" processes **ephemerally** (in-memory, not retained beyond
  serving the request) can qualify for exemption from being disclosed as "shared with a third
  party." The stateless Firebase relay (see Gemini Integration) is designed to fit that
  exemption, but it only actually qualifies if the implementation genuinely never logs or caches
  the image — including default Cloud Functions/Cloud Logging request-body capture, which must
  be explicitly disabled. This is an implementation requirement, not just a form checkbox
- **Privacy Policy:** mandatory once a sensitive permission plus any off-device data transit is
  involved (the Gemini relay counts, even though it's stateless). Must be hosted and linked from
  the Play listing, and must accurately describe photo access and the relay — no version of this
  app can claim "nothing ever leaves the device"

### On-Device Pre-Filter

Two on-device, offline ML Kit APIs combine to screen out photos before they cost a Gemini call —
chosen over generic Image Labeling because ML Kit's Object Detection & Tracking API ships a
**coarse classifier built for exactly this**: it buckets detected objects into `home goods`,
`fashion goods`, `food`, `plants`, or `places`.

- **ML Kit Object Detection & Tracking** (single-image mode, coarse classification enabled) runs
  first. If it finds at least one object classified as `home goods` (or `fashion goods` — worth
  keeping, since clothing/accessories are legitimate inventory items) above a confidence
  threshold, the photo goes to Gemini
- **ML Kit Face Detection** runs as a secondary signal: if a single face fills a large fraction
  of the frame (a portrait/selfie heuristic) and Object Detection found nothing in the accepted
  categories, the photo is treated as a portrait and skipped
- The filter only **skips** Gemini when it's **highly confident** the photo is a selfie/portrait
  or contains no objects in the accepted categories at all (e.g. a pure landscape/food/plant
  shot). Small on-device models aren't very accurate, so this stays conservative on purpose —
  false negatives here (skipping something we shouldn't) cost the user a missing item; false
  positives (sending something we shouldn't have) just cost a wasted scan credit. When in doubt,
  send it
- This reduces the number of paid Gemini calls without a meaningful accuracy trade-off, and both
  APIs run fully offline with no model download required at first launch (bundled, mobile-optimized)

---

### Gemini Integration

- Model: `gemini-2.0-flash` (multimodal)
- Photo is sent as compressed JPEG bytes through **Firebase AI Logic**, a stateless relay,
  rather than directly from the device to the Gemini API. This is the fix for a real risk: a
  raw `GEMINI_API_KEY` embedded in the client can be extracted from the APK via
  reverse-engineering and used to run up billing on our account. Firebase AI Logic holds Gemini
  credentials on Google's managed backend — no API key of any kind ships inside the app
- The client authenticates to the relay with a **Firebase App Check** token (backed by the Play
  Integrity API), which proves the request is coming from a genuine, unmodified install of this
  app — without requiring a user account or sign-in of any kind
- The relay is stateless: it forwards the photo to Gemini, forwards Gemini's response back, and
  retains nothing — no logging of image bytes, no caching, no Cloud Logging body capture
- Only photos that pass the on-device pre-filter reach this step

**Prompt:**
```
Look at this photo. Identify every distinct item visible.
Return a JSON array in this exact format:
[
  {
    "label": "Cordless drill",
    "category": "Tools",
    "description": "Black and yellow DeWalt cordless drill on a shelf",
    "tags": ["drill", "power tool", "DeWalt", "yellow"]
  }
]
Only return the JSON array, no other text.
```

- Gemini call is made via the Android SDK directly; image bytes are not logged or stored by our app beyond the private copy described above

---

### Search

Search uses Gemini for typo/vocabulary correction, but only as a fallback — the common case
is served entirely locally for speed and cost.

**At scan time — rich tagging**
The Gemini prompt explicitly asks for every descriptor as separate tags: color, material, brand, size, shape. A drill becomes `tags: ["drill", "black", "DeWalt", "cordless", "power tool", "battery", "yellow handle"]`. This means partial queries like "black drill" match on multiple independent tokens.

**At search time — local-first, Gemini fallback**
1. Run the raw query straight through FTS5 first: `SELECT * FROM items_fts WHERE items_fts MATCH 'black* OR dril*'` (prefix matching)
2. If FTS5 returns results, show them immediately — no network call, no latency, no cost
3. Only if FTS5 returns **zero** results, send the query to `gemini-2.0-flash` for spelling correction and synonym expansion:
```
Fix any spelling errors and expand this search query with synonyms.
Return a flat list of search terms, nothing else.
Input: "blakc dril"
Output: ["black", "drill", "cordless drill", "power tool", "DeWalt"]
```
4. Re-run FTS5 with the expanded term list, and cache the mapping (raw query → expanded terms) locally so the same typo never triggers a second Gemini call

**Full search flow:**
```
User types "blakc dril"
  → FTS5 direct match on "blakc" / "dril" → no results
  → check local cache for this raw query → miss
  → Gemini preprocesses → ["black", "drill", "cordless drill", "power tool"]
  → cache raw query → expanded terms, locally
  → FTS5: SELECT * FROM items_fts WHERE items_fts MATCH 'black OR drill OR "cordless drill" OR "power tool"'
  → JOIN Photo on photoId → load image from privatePath
  → Show results ranked by relevance score
```

**Offline:** if the device is offline, skip the Gemini fallback step entirely — direct FTS5 matches (including cached expansions from past searches) still work.

---

### App Screens

1. **Inventory** — default/home screen; grid of all items, filterable by room or category; "Scan now" action
2. **Review New Items** — shown after a scan finds new items; lets the user optionally assign a room per photo/item, or skip
3. **Search Results** — photo grid matching the query
4. **Item Detail** — photo (from app-private storage) + item list; room shown, editable, optional
5. **Rooms** — manage room list (add, rename, delete)
6. **Buy Credits** — credit pack selection and Google Play purchase flow
7. **Settings** — scan interval, credit balance, clear local data

---

## Monetisation

### Model — Scan Credit Packs (Google Play In-App Purchases)

Search is always free. Users pay only for scans (Gemini image calls), which is where our cost actually is.

| Tier | Scans | Price | Our cost | Margin |
|------|-------|-------|----------|--------|
| Free trial | 20 scans | Free | ~$0.01 | — |
| Starter pack | 50 scans | $0.99 | ~$0.02 | ~98% |
| Standard pack | 200 scans | $2.99 | ~$0.08 | ~97% |
| Large pack | 500 scans | $5.99 | ~$0.20 | ~97% |

- Credits never expire
- Balance shown in Settings
- When the user hits 0 credits, background scans still run the on-device pre-filter but skip Gemini calls and queue those photos; a "Buy more scans" prompt appears — search over existing inventory stays free
- Google Play handles payment, receipts, and refunds

### Storage Tiers (one-time purchase)

Separate from scan credits, users pay once to increase how many items they can hold in their inventory.

| Tier | Item cap | Price |
|------|----------|-------|
| Free | 200 items | Free |
| Standard | 2,000 items | one-time purchase |
| Large | 10,000 items | one-time purchase |
| Unlimited | No cap | one-time purchase |

When a user hits their item cap, new items from background scans are queued until they upgrade or delete items.

### Why not subscription?

Credit packs and one-time storage upgrades fit this app better than a monthly subscription — users scan their house once (or occasionally after moving/reorganising) and then mostly just search. A subscription would feel unfair when someone has already scanned everything they own and just wants to look things up.

### Security — Accepted Limitation of Local-Only Account Data

The Firebase relay exists solely to proxy Gemini calls (see Gemini Integration) — it never
sees credit balance, storage tier, or item count. Those stay in the local `UserProfile` row in
Room, the same as everything else, and there is no account system to enforce them server-side.
This is an intentional trade-off of keeping all inventory/account data local:

- **Normal usage is fully protected** — the app UI never exposes a way to edit these values directly
- **A technical user with root/ADB access to their own device could edit the local database
  to grant themselves credits.** There is no way to prevent this without adding a stateful
  backend and an account system to enforce it — which would reintroduce exactly the
  account/cloud-sync dependency this design otherwise avoids. This is accepted as a known
  limitation, not an oversight — it only allows someone to defraud themselves, not other users,
  and there is no shared resource to protect
- **Purchase verification** uses the Google Play Billing Library's built-in purchase signature
  verification: each purchase is signed by Google and can be verified on-device against the
  app's Play Console public key (bundled in the app) without a backend call. This is a lighter
  guarantee than full server-side verification against the Play Developer API, but is
  consistent with the no-account, no-inventory-data-on-server constraint
- **Gemini API key** is not embedded in the client at all. It never leaves Google's managed
  Firebase backend — see Gemini Integration. This is the one place the design does use a
  server, specifically because a client-embedded key was the alternative, and that's the worse
  option from a security standpoint. The relay is stateless and holds no user data, so it
  doesn't reintroduce the account/cloud-sync surface the rest of this design avoids

---

### Build & Development Notes

- Min SDK: 26 (Android 8.0)
- Target SDK: 35 (see Permissions & Play Store Compliance below for why the target SDK level matters for photo access)
- No `GEMINI_API_KEY` ships in the client. A Firebase project is required (`google-services.json`) to configure Firebase AI Logic (the stateless Gemini relay) and App Check — this is the only cloud dependency in the whole app, and it's stateless
- **It must never be committed to source control**: `google-services.json` still identifies our Firebase project and should stay out of public forks even though it contains no secret key material by itself
- Run `./gradlew test` for unit tests; `./gradlew connectedAndroidTest` for instrumented tests
