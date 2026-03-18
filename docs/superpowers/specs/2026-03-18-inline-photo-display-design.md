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

Current DB version is 4 (version 3→4 was claimed by the per-channel photo filter feature). `MIGRATION_4_5` adds the new column:
```sql
ALTER TABLE messages ADD COLUMN photo_file_id INTEGER
```
`AppDatabase` version bumps 4→5. `DatabaseModule` updated to include `MIGRATION_4_5`.

### MediaType constant

Define a new file `domain/model/MediaType.kt`:
```kotlin
object MediaType {
    const val PHOTO = "photo"
}
```

### TelegramRepositoryImpl — extracting photo info

New helpers:

```kotlin
private fun extractPhotoFileId(content: TdApi.MessageContent): Int? {
    val sizes = (content as? TdApi.MessagePhoto)?.photo?.sizes ?: return null
    val chosen = sizes.filter { it.width <= 1280 }.maxByOrNull { it.width }
        ?: sizes.maxByOrNull { it.width }
    return chosen?.photo?.id
}

private fun extractMediaType(content: TdApi.MessageContent): String? = when (content) {
    is TdApi.MessagePhoto -> MediaType.PHOTO
    else -> null
}
```

Both `fetchMessagesSince` and `observeNewMessages` call both helpers and populate `mediaType` and `photoFileId` on the resulting `Message`.

**Photo-only messages (no caption):** Both methods currently drop messages where `text.isBlank()`. Both guards must be relaxed: if `mediaType == MediaType.PHOTO`, the message is included/emitted regardless of caption.

- In `fetchMessagesSince`: replace `.ifBlank { return@mapNotNull null }` with a check that allows photo messages through even when text is blank.
- In `observeNewMessages`: replace `if (text.isNotBlank())` with `if (text.isNotBlank() || mediaType == MediaType.PHOTO)`.

`mediaUrl: String?` on `Message` and `MessageEntity` is retained as-is (reserved for future use; not populated by this feature).

### FeedViewModel — shouldForward bypass for photos

`FeedViewModel` has three paths that call `filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)` before saving to Room (on-load, refresh, real-time collect). Photo-only messages have blank text and will be silently dropped by this filter unless bypassed. All three paths must pass photo-only messages through unconditionally, analogous to the `observeNewMessages` fix:

```kotlin
val passes = (msg.mediaType == MediaType.PHOTO && msg.text.isBlank()) ||
    filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
if (!passes) return@mapNotNull null  // or equivalent skip
```

### TelegramRepository interface

```kotlin
suspend fun downloadFile(fileId: Int): String?
```

### TelegramRepositoryImpl — downloadFile

```kotlin
override suspend fun downloadFile(fileId: Int): String? = try {
    val file = api.sendFunctionAsync(TdApi.DownloadFile(fileId, 1, 0L, 0L, true))
    if (file.local.isDownloadingCompleted) file.local.path else null
} catch (_: Exception) { null }
```

### FeedViewModel — write path

All `MessageEntity` constructor calls in `FeedViewModel` (on-load, refresh, real-time collect) and in `SyncWorker` must include `photoFileId = msg.photoFileId` and `mediaType = msg.mediaType`.

### FeedViewModel — read path

The `filteredMessages` mapping from `MessageEntity` to `Message` must propagate both `mediaType` and `photoFileId`:
```kotlin
Message(e.id, e.channel, e.channelTitle.ifBlank { e.channel }, e.text, e.timestamp,
        mediaType = e.mediaType, photoFileId = e.photoFileId)
```

---

## Section 2 — Photo Layout Setting

### Enum

```kotlin
// domain/model/PhotoLayout.kt
enum class PhotoLayout { ABOVE, BELOW, LEFT }
```

### UserPreferencesRepository

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

```kotlin
val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout
fun setPhotoLayout(layout: PhotoLayout) { userPrefs.setPhotoLayout(layout) }
```

### SettingsScreen

New row in the Display section, below the channel icons toggle:
```
Photo layout   [Above] [Below] [Left]
```
Implemented as `SingleChoiceSegmentedButtonRow` with three `SegmentedButton` entries (requires Material3 ≥ 1.2.0, which is included in the project's Compose BOM).

---

## Section 3 — FeedViewModel

```kotlin
val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout

suspend fun getPhotoPath(fileId: Int): String? = telegramRepo.downloadFile(fileId)
```

`userPrefs` is already injected in `FeedViewModel`. No new injection needed.

---

## Section 4 — UI

### Dependency

Add to `app/build.gradle.kts`:
```kotlin
implementation("io.coil-kt.coil3:coil-compose:3.1.0")
implementation("io.coil-kt.coil3:coil-android:3.1.0")
```
(Coil 3.x; artifact ID changed from `io.coil-kt` to `io.coil-kt.coil3`. Both artifacts are required: `coil-compose` for `AsyncImage`, `coil-android` for the Android `BitmapDecoder` and file fetcher.)

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

`AsyncImage` is rendered only when `photoPath != null`. No placeholder or error image.

`FeedScreen` passes the new params to **both** `FeedItem` call sites (unread list and read list).

### ArticleSheet

Signature:
```kotlin
private fun ArticleSheet(
    message: Message,
    getPhotoPath: suspend (Int) -> String?,
    onDismiss: () -> Unit,
)
```

Always renders a full-width photo (max height 300dp, `contentScale = Fit`) above the text when `message.photoFileId != null`. Uses the same `produceState` + `AsyncImage` pattern. No layout setting applies here. `ArticleSheet` receives `getPhotoPath` as a parameter from `FeedScreen`.

### FeedScreen call site

```kotlin
val photoLayout by viewModel.photoLayout.collectAsStateWithLifecycle()
val getPhotoPath: suspend (Int) -> String? = { viewModel.getPhotoPath(it) }

// passed to both FeedItem usages and to ArticleSheet
```

---

## SyncWorker

`SyncWorker` calls `telegramRepo.fetchMessagesSince` and constructs `MessageEntity` objects. It is **in scope**: its `MessageEntity` constructor calls must include `photoFileId = msg.photoFileId` and `mediaType = msg.mediaType`.

---

## Out of Scope

- Video/animation/document previews
- Zooming or full-screen photo viewer
- Per-channel photo layout override
- Placeholder shimmer / loading indicators
