# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**home_inventory** is an app that helps users build a searchable index of their home
items. Users take photos of their belongings (electronics, tools, documents, kitchenware,
keepsakes, etc.) using their phone's normal camera app — nothing special, just their everyday
photo habits. In the background, home_inventory periodically scans new photos, identifies and
labels the belongings in them, and records them locally. Later on, users can search for
belongings by typing any descriptive words, and the app will show the matching photos.

Everything — photos, data, and search — stays on the user's device. There is no server, no
account, and no cloud sync.

---

## Architecture & Design

### Core Principles
- **No in-app camera** — the phone's native camera app is the only way photos get taken; home_inventory only reads what's already in the camera roll
- **Fully local, no cloud** — no Firebase, no Firestore, no auth, no server of any kind. All data lives in a local SQLite (Room) database on the device. Photos are sensitive personal data, so nothing leaves the device except the specific image sent to Gemini for item recognition
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
      → otherwise → send to Gemini for item identification
  → Gemini returns: items identified in the photo
  → matching photo is copied into the app's own private storage (so it survives the user
    deleting/moving the original) and TEXT results are stored in the local database
  → user gets a notification if new items were found; can optionally assign a room, or leave it for later
  → user can search immediately
```

### What We Store

| Stored                                  | Not Stored                    |
|------------------------------------------|--------------------------------|
| Item labels, descriptions & tags          | Anything on a server — there is no server |
| Room (optional, user-assigned)            | Photo bytes anywhere but the device |
| A private copy of photos with detected items | Original, un-tagged photos (left alone in the camera roll) |
| Timestamp                                 |                                |

Everything above lives in a local Room (SQLite) database. Nothing is synced anywhere.

### Photo Storage Strategy

Android has no "Pictures/Home Inventory/" convention — that's a Windows-style path and doesn't
apply here. The correct approach on Android:

- The background job reads original photos from the standard camera folder via `MediaStore`
  (typically `DCIM/Camera`) — it does **not** move or modify the originals
- When Gemini finds items in a photo, the app copies that photo into its **own app-private
  internal storage** (`context.filesDir/photos/`) — not a shared MediaStore album
  - This directory isn't visible to the Gallery, Google Photos, or other apps, isn't backed up
    to the cloud, and is automatically deleted if the app is uninstalled
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
| Auth                  | None — single local user, no sign-in                               |
| Photo source          | Native camera app + `MediaStore` query — no in-app camera          |
| Background scanning   | WorkManager periodic worker (default every 6h, user-configurable) + on-demand "Scan now" |
| On-device pre-filter  | ML Kit Image Labeling (on-device, offline) — screens out person/pet/scenery photos |
| AI recognition        | Gemini API — `gemini-2.0-flash` (multimodal), only for photos that pass the pre-filter |
| Local storage         | Room (SQLite) — sole source of truth: photos, items, rooms, credits |
| Cloud sync            | None — no Firebase, no Firestore, no server                       |
| Networking            | Retrofit + OkHttp (Gemini API calls only)                         |
| Image display         | Coil (reads from app-private internal storage)                    |
| DI                    | Hilt                                                               |
| Purchases             | Google Play Billing Library (locally-verified, no backend)        |
| Background work       | WorkManager                                                       |

---

### Data Model

#### `Photo`
| Field         | Type   | Notes                                                        |
|---------------|--------|----------------------------------------------------------------|
| id            | String | UUID — the link code shared by all items in this photo        |
| privatePath   | String | Path under `filesDir/photos/` — the app's own copy of the photo |
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

### On-Device Pre-Filter

- Uses ML Kit Image Labeling (on-device, offline, free) to get generic labels for each candidate photo before spending a Gemini call on it
- Small on-device models aren't very accurate, so the filter is deliberately conservative: it only **skips** Gemini when it's **highly confident** the photo is clearly not a home object — e.g. a label like "Person", "Selfie", "Dog", "Cat", or "Nature"/"Sky" above a high confidence threshold (e.g. 0.9)
- Anything ambiguous or below that threshold still goes to Gemini — false negatives here (skipping something we shouldn't) cost the user a missing item; false positives (sending something we shouldn't have) just cost a wasted scan credit — so the filter is biased toward "when in doubt, send it"
- This reduces the number of paid Gemini calls without a meaningful accuracy trade-off

---

### Gemini Integration

- Model: `gemini-2.0-flash` (multimodal)
- Photo is sent as compressed JPEG bytes **directly from the device to Gemini** — it does not pass through any server, because there is no server
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

### Security — Accepted Limitation of a Fully Local App

There is no server, so credit balance, storage tier, and item count are stored in the local
`UserProfile` row in Room, the same as everything else. This is an intentional trade-off of the
"nothing leaves the device" design:

- **Normal usage is fully protected** — the app UI never exposes a way to edit these values directly
- **A technical user with root/ADB access to their own device could edit the local database
  to grant themselves credits.** There is no way to prevent this without a server, and adding
  one would reintroduce the exact cloud dependency this design avoids. This is accepted as a
  known limitation, not an oversight — it only allows someone to defraud themselves, not other
  users, and there is no shared resource to protect
- **Purchase verification** uses the Google Play Billing Library's built-in purchase signature
  verification: each purchase is signed by Google and can be verified on-device against the
  app's Play Console public key (bundled in the app) without a backend call. This is a lighter
  guarantee than full server-side verification against the Play Developer API, but is
  consistent with the no-backend constraint
- **Gemini API key** is embedded in the client via `BuildConfig` (see Build & Development
  Notes) since there is no server to proxy calls through. The key is restricted in Google Cloud
  Console to this app's package name + signing certificate SHA-1 fingerprint, which limits (but
  does not eliminate) the impact of key extraction via reverse-engineering. This is the
  strongest mitigation available without introducing a backend

---

### Build & Development Notes

- Min SDK: 26 (Android 8.0)
- Target SDK: 35
- `GEMINI_API_KEY` is stored in `local.properties` and injected into the app via `BuildConfig` at build time. **It must never be committed to source control** (already covered by `.gitignore`) and never written into any web page, log, or crash report
- The Gemini API key is restricted in Google Cloud Console to this app's package name + signing SHA-1, since it necessarily ships inside the client (see Monetisation → Security above)
- Required keys: `GEMINI_API_KEY` only — no `google-services.json`, no Firebase project
- Run `./gradlew test` for unit tests; `./gradlew connectedAndroidTest` for instrumented tests
