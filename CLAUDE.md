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
- **No photos stored on our servers** — photos never leave the device to our backend; Gemini receives the image directly from the app
- **No location permission required** — room/location is inferred by Gemini from the visual content of the photo (e.g. "garage shelf", "kitchen drawer")
- **Zero effort after the shot** — user takes a photo, everything else is automatic

### High-Level Flow

```
User opens app camera → takes photo
  → photo sent directly from device to Gemini API (never to our servers)
  → Gemini returns: items identified + inferred room/location
  → only TEXT results stored (labels, descriptions, location, photo reference ID)
  → photo stays on device (device storage / Google Photos)
  → user can search immediately
```

### What We Store (Text Only)

We **never** store the photo. We only store the structured text data Gemini returns:

| Stored         | Not Stored         |
|----------------|--------------------|
| Item labels    | Photo bytes/file   |
| Descriptions   | Photo URL          |
| Tags           | Any image data     |
| Inferred room  |                    |
| Device photo ID (reference only) | |
| Timestamp      |                    |

The device photo ID is a local asset URI (e.g. Android `content://media/...`) so the app can display the photo from the user's own device when showing search results — it is never uploaded.

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

#### `InventoryItem`
| Field        | Type   | Notes                                             |
|--------------|--------|---------------------------------------------------|
| id           | String | Local UUID                                        |
| devicePhotoId| String | Local asset URI — used to display photo from device|
| label        | String | Primary item name (e.g. "Black cordless drill")   |
| category     | String | e.g. "Tools", "Electronics", "Documents"          |
| description  | String | Gemini's natural-language description             |
| inferredRoom | String | Room inferred by Gemini (e.g. "Garage", "Kitchen")|
| tags         | List   | Additional search keywords                        |
| timestamp    | Long   | When the photo was taken                          |

No `Photo` table — photos are never stored or managed by us.
No `Location` table — location is inferred automatically, not user-managed.

---

### Gemini Integration

- Model: `gemini-2.0-flash` (multimodal)
- Photo is sent as compressed JPEG bytes **directly from the device to Gemini** — it does not pass through our servers
- Single prompt asks Gemini to return items AND infer the room from visual context:

**Prompt:**
```
Look at this photo. Identify every distinct item visible.
Also infer what room or storage area this appears to be based on the surroundings.
Return a JSON object in this exact format:
{
  "inferredRoom": "Garage",
  "items": [
    {
      "label": "Cordless drill",
      "category": "Tools",
      "description": "Black and yellow DeWalt cordless drill on a shelf",
      "tags": ["drill", "power tool", "DeWalt", "yellow"]
    }
  ]
}
```

- If the user disagrees with the inferred room, they can correct it with one tap — but this is optional, not required
- Gemini call is made via the Android SDK/Retrofit directly; image bytes are not logged or stored by our app

---

### Camera Flow

- App has a built-in camera screen (CameraX)
- After capture: photo is saved to device (Google Photos if user has backup enabled) and immediately sent to Gemini in the background
- User sees a brief "Scanning..." indicator — can keep taking more photos without waiting
- Results appear in the inventory list once Gemini responds (typically 2–5 seconds)

---

### Search

- Full-text search (FTS5 in Room) over `label`, `description`, `tags`, `category`, `inferredRoom`
- Results display as a photo grid — photos loaded from the device using `devicePhotoId`
- Tapping a result shows the photo (from device) + all identified items

---

### App Screens

1. **Sign-In** — Google Sign-In
2. **Camera** — default/home screen; tap to take a photo
3. **Inventory** — grid of all items, filterable by category or inferred room
4. **Search Results** — photo grid matching the query
5. **Item Detail** — photo (from device) + item list; optional room correction
6. **Settings** — account, clear local data

---

### Build & Development Notes

- Min SDK: 26 (Android 8.0)
- Target SDK: 35
- Secrets stored via `local.properties` + `BuildConfig`, never committed
- Required keys: `GEMINI_API_KEY`, Firebase `google-services.json`
- Run `./gradlew test` for unit tests; `./gradlew connectedAndroidTest` for instrumented tests
