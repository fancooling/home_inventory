# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**home_inventory** is an app that helps users build a searchable index of their home
items. The goal is to let people take pictures of the household belongings (electronics, tools, 
documents, kitchenware, keepsakes, etc.) along with the location these are stored. The app will
automatically scan their photos, identify / label these belongings, and record them. Later on,
users can search for belongings by typing any descriptive words. The app will show relevant photos.

---

## Architecture & Design

### Core Principles
- **No photos stored on our servers** — photos are saved to an app-specific album on the device (`Pictures/Home Inventory` via Android MediaStore); Google Photos auto-syncs this album automatically
- **Location set once per session, not per photo** — a persistent room selector on the camera screen; user picks "I'm in: Garage", takes as many photos as they want, all tagged automatically
- **Zero effort after the shot** — user takes a photo, everything else is automatic

### High-Level Flow

```
User opens app → selects current room ("I'm in: Garage") once
  → takes photo
  → photo saved to "Home Inventory" album on device (Google Photos syncs it)
  → photo sent directly from device to Gemini API (never to our servers)
  → Gemini returns: items identified in the photo
  → TEXT results + room tag + device photo reference stored locally and synced to Firestore
  → user can search immediately
```

### What We Store

| Stored                        | Not Stored              |
|-------------------------------|-------------------------|
| Item labels                   | Photo bytes on servers  |
| Descriptions & tags           | Photo URL on servers    |
| Room (from room selector)     |                         |
| Device photo URI (reference)  |                         |
| Timestamp                     |                         |

The device photo URI (`content://media/...`) lets the app display the photo from the user's own device in search results — it is never uploaded to our servers.

### Photo Album Strategy

- On capture, photo is written to `Pictures/Home Inventory/` via Android `MediaStore`
- This creates a named album visible in Google Photos, Samsung Gallery, etc.
- Google Photos auto-backup applies if the user has it enabled — we don't control or trigger it
- If the user deletes a photo from the album, the app shows a placeholder in search results

---

### Tech Stack

| Layer              | Technology                              |
|--------------------|-----------------------------------------|
| UI                 | Jetpack Compose                         |
| Language           | Kotlin                                  |
| Auth               | Firebase Auth (Google Sign-In)          |
| Camera             | CameraX                                 |
| AI recognition     | Gemini API — gemini-2.0-flash (multimodal) |
| Local storage      | Room (SQLite) — text data only          |
| Cloud sync         | Firebase Firestore — text data only     |
| Networking         | Retrofit + OkHttp                       |
| Image display      | Coil (reads from device, not our server)|
| DI                 | Hilt                                    |
| Background work    | WorkManager                             |

---

### Data Model

#### `Photo`
| Field         | Type   | Notes                                                   |
|---------------|--------|---------------------------------------------------------|
| id            | String | UUID — the link code shared by all items in this photo  |
| devicePhotoUri| String | `content://media/...` — used to load photo from device  |
| room          | String | Room selected at time of capture                        |
| timestamp     | Long   | When photo was taken                                    |

#### `InventoryItem`
| Field       | Type   | Notes                                           |
|-------------|--------|-------------------------------------------------|
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

**How the link works:**
1. Photo taken → saved to device → `Photo` record created with a UUID and the device URI
2. Gemini returns 3 items from that photo → 3 `InventoryItem` records created, all with the same `photoId`
3. User searches "drill" → `InventoryItem` found → `photoId` looked up → `Photo.devicePhotoUri` used to load the image from the device
4. No photo bytes, no image URLs in our database — just the UUID link

User manages their own room list. Pre-populated with common rooms on first launch.

---

### Gemini Integration

- Model: `gemini-2.0-flash` (multimodal)
- Photo is sent as compressed JPEG bytes **directly from the device to Gemini** — it does not pass through our servers
- Room is already known from the room selector — Gemini only needs to identify items:

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

- Gemini call is made via the Android SDK directly; image bytes are not logged or stored by our app

---

### Camera Flow

- App has a built-in camera screen (CameraX) with a **room selector chip** at the top (e.g. "I'm in: Garage ▼")
- User selects the room once; it persists until they change it — no per-photo prompting
- After capture:
  1. Photo saved to `Pictures/Home Inventory/` on device via MediaStore
  2. Photo compressed and sent directly to Gemini in the background
  3. Room tag from the selector is attached to the result
- User sees a brief "Scanning..." indicator — can keep taking more photos without waiting
- Results appear in the inventory once Gemini responds (typically 2–5 seconds)

---

### Search

Search uses Gemini at both ends to bridge vocabulary gaps and handle typos.

**At scan time — rich tagging**
The Gemini prompt explicitly asks for every descriptor as separate tags: color, material, brand, size, shape. A drill becomes `tags: ["drill", "black", "DeWalt", "cordless", "power tool", "battery", "yellow handle"]`. This means partial queries like "black drill" match on multiple independent tokens.

**At search time — query preprocessing**
Before hitting the database, the user's raw query is sent to `gemini-2.0-flash` with a lightweight prompt:
```
Fix any spelling errors and expand this search query with synonyms.
Return a flat list of search terms, nothing else.
Input: "blakc dril"
Output: ["black", "drill", "cordless drill", "power tool", "DeWalt"]
```
The expanded term list is then run through FTS5 with OR matching, ranked by how many terms matched.

**Full search flow:**
```
User types "blakc dril"
  → Gemini preprocesses → ["black", "drill", "cordless drill", "power tool"]
  → FTS5: SELECT * FROM items_fts WHERE items_fts MATCH 'black OR drill OR "cordless drill" OR "power tool"'
  → JOIN Photo on photoId → load image from devicePhotoUri
  → Show results ranked by relevance score
```

**Fallback:** if the device is offline, skip Gemini preprocessing and run the raw query directly through FTS5 — partial matches still work, just without spelling correction.

---

### App Screens

1. **Sign-In** — Google Sign-In
2. **Camera** — default/home screen; room selector chip at top, tap shutter to capture; scan credit count shown
3. **Inventory** — grid of all items, filterable by room or category
4. **Search Results** — photo grid matching the query
5. **Item Detail** — photo (loaded from device) + item list; room shown, editable
6. **Rooms** — manage room list (add, rename, delete)
7. **Buy Credits** — credit pack selection and Google Play purchase flow
8. **Settings** — account, credit balance, clear local data

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
- Balance shown on the camera screen and in Settings
- When user hits 0 credits, the shutter button is disabled and a "Buy more scans" prompt appears — they can still search existing inventory freely
- Google Play handles payment, receipts, and refunds; we store the credit balance in Firestore per user UID

### Storage Tiers (one-time purchase)

Separate from scan credits, users pay once to increase how many items they can hold in their inventory. This prevents abuse (e.g. scanning an entire warehouse on one credit pack and storing everything for free).

| Tier | Item cap | Price |
|------|----------|-------|
| Free | 200 items | Free |
| Standard | 2,000 items | one-time purchase |
| Large | 10,000 items | one-time purchase |
| Unlimited | No cap | one-time purchase |

When a user hits their item cap, scanning is blocked until they upgrade or delete items.

### Why not subscription?

Credit packs and one-time storage upgrades fit this app better than a monthly subscription — users scan their house once (or occasionally after moving/reorganising) and then mostly just search. A subscription would feel unfair when someone has already scanned everything they own and just wants to look things up.

### Security — Preventing Credit Fraud

Clients (the Android app) can **never write directly to credit balance, storage tier, or item count**. Those fields are locked to Cloud Functions only via the Firebase Admin SDK, which bypasses Firestore security rules. A malicious user modifying app traffic or reverse-engineering the app cannot give themselves credits.

**Firestore Security Rules structure:**
```
/users/{uid}/profile      → READ: owner only | WRITE: false (Cloud Functions only)
/users/{uid}/items        → READ + WRITE: owner only
/users/{uid}/photos       → READ + WRITE: owner only
/users/{uid}/rooms        → READ + WRITE: owner only
```

**Scan flow with server enforcement:**
1. App calls Cloud Function `reserveCredit` — CF checks balance, deducts 1, returns a one-time scan token
2. App sends photo directly to Gemini (photo never touches our servers)
3. App calls Cloud Function `commitScan` with the scan token + Gemini results — CF validates token, writes items to Firestore, increments item count
4. If Gemini fails: app calls Cloud Function `refundCredit` to restore the deducted credit

**Purchase verification:**
- Google Play purchase token is sent to Cloud Function `verifyPurchase`
- CF verifies token against Google Play Developer API
- If valid: CF adds credits or upgrades storage tier, marks purchase consumed
- Client never touches credit balance directly at any point

---

### Build & Development Notes

- Min SDK: 26 (Android 8.0)
- Target SDK: 35
- Secrets stored via `local.properties` + `BuildConfig`, never committed
- Required keys: `GEMINI_API_KEY`, Firebase `google-services.json`
- Run `./gradlew test` for unit tests; `./gradlew connectedAndroidTest` for instrumented tests
