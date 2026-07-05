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

### Platform
- **Android** (Kotlin + Jetpack Compose)
- **Google Photos** as the primary photo source (via Google Photos Library API)
- **Gemini API** (gemini-2.0-flash) for AI-powered item recognition from photos
- **Firebase** for authentication and cloud sync

### High-Level Flow

```
User → Google Sign-In → Grant Google Photos access
     → Select albums / photos to scan
     → App sends photos to Gemini API
     → Gemini returns structured item data (label, category, description)
     → Items stored in local Room DB + synced to Firestore
     → User searches by text → matching photos shown
```

### Tech Stack

| Layer              | Technology                              |
|--------------------|-----------------------------------------|
| UI                 | Jetpack Compose                         |
| Language           | Kotlin                                  |
| Auth               | Firebase Auth (Google Sign-In)          |
| Photo source       | Google Photos Library API               |
| AI recognition     | Gemini API — gemini-2.0-flash           |
| Local storage      | Room (SQLite)                           |
| Cloud sync         | Firebase Firestore                      |
| Networking         | Retrofit + OkHttp                       |
| Image loading      | Coil                                    |
| DI                 | Hilt                                    |
| Background work    | WorkManager (batch scanning)            |

---

### Data Model

#### `Photo`
| Field           | Type     | Notes                                |
|-----------------|----------|--------------------------------------|
| id              | String   | Local UUID                           |
| googlePhotosId  | String   | ID from Google Photos API            |
| baseUrl         | String   | Google Photos base URL               |
| dateTaken       | Long     | Unix timestamp                       |
| locationId      | String?  | FK → Location (user-tagged)          |
| scanned         | Boolean  | Whether Gemini has processed it      |

#### `InventoryItem`
| Field       | Type    | Notes                                      |
|-------------|---------|--------------------------------------------|
| id          | String  | Local UUID                                 |
| photoId     | String  | FK → Photo                                 |
| label       | String  | Primary item name (e.g. "Black drill")     |
| category    | String  | e.g. "Tools", "Electronics", "Documents"   |
| description | String  | Gemini's natural-language description      |
| confidence  | Float   | Gemini confidence score (0.0–1.0)          |
| tags        | List    | Additional search keywords                 |

#### `Location`
| Field | Type   | Notes                              |
|-------|--------|------------------------------------|
| id    | String | Local UUID                         |
| name  | String | e.g. "Garage", "Kitchen drawer 2"  |

---

### Gemini Integration

- Model: `gemini-2.0-flash` (multimodal, cost-effective)
- Each photo is sent as inline image data (JPEG bytes) or via URI
- Prompt instructs Gemini to return a JSON array of items found in the photo:

```json
[
  {
    "label": "Cordless drill",
    "category": "Tools",
    "description": "Black and yellow DeWalt cordless drill stored on shelf",
    "tags": ["drill", "power tool", "DeWalt", "yellow"],
    "confidence": 0.95
  }
]
```

- Scanning runs as a WorkManager background job to handle large photo libraries
- Rate limiting: batch photos with a delay to stay within Gemini API quotas

---

### Google Photos Integration

- OAuth 2.0 via Firebase Auth + Google Photos scope (`photoslibrary.readonly`)
- Users can choose to scan:
  - All photos in their library
  - Specific albums (e.g. "Home items")
  - Individual selected photos
- Photos are never downloaded fully — Gemini receives the Google Photos base URL or resized thumbnail bytes

---

### Search

- Full-text search (FTS5 in Room) over `label`, `description`, `tags`, `category`
- Filter by `Location`
- Results display as a photo grid with item labels overlaid
- Tapping a result shows the full photo + all identified items in that photo

---

### App Screens

1. **Sign-In** — Google Sign-In button
2. **Home / Dashboard** — scan status, quick search bar, recent items
3. **Scan** — select Google Photos albums or trigger full-library scan; progress indicator
4. **Inventory** — scrollable grid of all scanned items, filterable by category/location
5. **Search Results** — photo grid matching the query
6. **Photo Detail** — full photo + list of Gemini-identified items, location tag editor
7. **Locations** — manage named locations (rooms, storage areas)
8. **Settings** — account, re-scan, clear data

---

### Build & Development Notes

- Min SDK: 26 (Android 8.0)
- Target SDK: 35
- Secrets (API keys) stored via `local.properties` + `BuildConfig`, never committed
- Required keys: `GEMINI_API_KEY`, Firebase `google-services.json`
- Run `./gradlew test` for unit tests; `./gradlew connectedAndroidTest` for instrumented tests
