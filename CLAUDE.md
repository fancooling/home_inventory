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
- **No photos stored on our servers** — photos are saved to an app-specific album on the device (`Pictures/Home Inventory` via Android MediaStore); Google Photos auto-syncs this album just like Pokémon GO snapshots
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
- This creates a named album visible in Google Photos, Samsung Gallery, etc. — identical to how Pokémon GO stores AR snapshots
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
2. **Camera** — default/home screen; room selector chip at top, tap shutter to capture
3. **Inventory** — grid of all items, filterable by room or category
4. **Search Results** — photo grid matching the query
5. **Item Detail** — photo (loaded from device) + item list; room shown, editable
6. **Rooms** — manage room list (add, rename, delete)
7. **Settings** — account, clear local data

---

### Build & Development Notes

- Min SDK: 26 (Android 8.0)
- Target SDK: 35
- Secrets stored via `local.properties` + `BuildConfig`, never committed
- Required keys: `GEMINI_API_KEY`, Firebase `google-services.json`
- Run `./gradlew test` for unit tests; `./gradlew connectedAndroidTest` for instrumented tests
