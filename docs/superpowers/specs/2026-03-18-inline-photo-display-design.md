# Inline Photo Display — Design Spec

**Date:** 2026-03-18
**Feature:** Render actual Telegram photos inline in feed cards and in full in the detail sheet.

---

## Overview

Currently photo messages are stored in Room with `mediaType = "photo"` but rendered only as a `[Photo]` placeholder. This feature downloads and displays the actual image: a small thumbnail inline in the feed card (layout configurable by the user) and a full-width image in the ArticleSheet detail view.

---

## Section 1 — Data Layer

### Domain model

`Message` gains:
```kotlin
val photoFileId: Int? = null
```

### MessageEntity

`MessageEntity` gains:
```kotlin
@ColumnInfo(name = "photo_file_id")
val photoFileId: Int? = null
```

### DB Migration

`MIGRATION_4_5` (or from current version if per-channel-photo-filter hasn't been merged):
```sql
ALTER TABLE messages ADD COLUMN photo_file_id INTEGER
```

`AppDatabase` version bumped accordingly. `DatabaseModule` updated to include the new migration.

### TelegramRepositoryImpl

New helper extracts the best photo file ID from `TdApi.MessagePhoto`:
- Picks the largest `PhotoSize` with width ≤ 1280px; falls back to the overall largest.
- Returns `Int?` (null for non-photo content).

Both `fetchMessagesSince` and `observeNewMessages` populate `photoFileId` on the resulting `Message`.

New method added to `TelegramRepository` interface and implemented in `TelegramRepositoryImpl`:
```kotlin
suspend fun downloadFile(fileId: Int): String?
```
Calls `api.sendFunctionAsync(TdApi.DownloadFile(fileId, priority = 1, offset = 0, limit = 0, synchronous = true))` and returns `file.local.path` if `file.local.isDownloadingCompleted`, else `null`. Wrapped in try/catch.

---

## Section 2 — Photo Layout Setting

### Enum

```kotlin
// domain/model/PhotoLayout.kt
enum class PhotoLayout { ABOVE, BELOW, LEFT }
```

### UserPreferencesRepository

Adds:
```kotlin
private const val KEY_PHOTO_LAYOUT = "photo_layout"

private val _photoLayout = MutableStateFlow(
    PhotoLayout.valueOf(prefs.getString(KEY_PHOTO_LAYOUT, PhotoLayout.ABOVE.name)!!)
)
val photoLayout: StateFlow<PhotoLayout> = _photoLayout.asStateFlow()

fun setPhotoLayout(layout: PhotoLayout) {
    prefs.edit().putString(KEY_PHOTO_LAYOUT, layout.name).apply()
    _photoLayout.value = layout
}
```

### SettingsViewModel

Exposes:
```kotlin
val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout
fun setPhotoLayout(layout: PhotoLayout) { userPrefs.setPhotoLayout(layout) }
```

### SettingsScreen

New row in the Display section, below the channel icons toggle:
```
Photo layout   [Above] [Below] [Left]
```
Implemented as `SingleChoiceSegmentedButtonRow` (Material3) with three `SegmentedButton` entries.

---

## Section 3 — FeedViewModel

Adds:
```kotlin
val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout

suspend fun getPhotoPath(fileId: Int): String? = telegramRepo.downloadFile(fileId)
```

No other ViewModel changes. Photo download is triggered on demand from the UI composable.

---

## Section 4 — UI

### Dependency

Add to `app/build.gradle.kts`:
```kotlin
implementation("io.coil-kt:coil-compose:2.6.0")
```

### FeedItem

Signature gains:
```kotlin
photoLayout: PhotoLayout,
getPhotoPath: suspend (Int) -> String?,
```

When `message.photoFileId != null`:
```kotlin
val photoPath by produceState<String?>(null, message.photoFileId) {
    value = message.photoFileId?.let { getPhotoPath(it) }
}
```

Layout based on `photoLayout`:
- **ABOVE** — `Column { AsyncImage(full width, max height 200dp, Crop); Text(...) }`
- **BELOW** — `Column { Text(...); AsyncImage(full width, max height 200dp, Crop) }`
- **LEFT** — `Row { AsyncImage(80×80dp, Crop); Column { Text(...) } }`

`AsyncImage` is only rendered when `photoPath != null`. No placeholder/error image — content simply doesn't appear until ready.

### ArticleSheet

Always renders a full-width photo (max height 300dp, `contentScale = Fit`) above the text when `message.photoFileId != null`. Uses the same `produceState` + `AsyncImage` pattern. No layout setting applies here.

### Call sites

`FeedScreen` collects `photoLayout` from the ViewModel and passes it plus a lambda `{ id -> viewModel.getPhotoPath(id) }` to each `FeedItem`.

---

## Out of Scope

- Video/animation/document previews
- Zooming or full-screen photo viewer
- Per-channel photo layout override
- Placeholder shimmer / loading indicators
