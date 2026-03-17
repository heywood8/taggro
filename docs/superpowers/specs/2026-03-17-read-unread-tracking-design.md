# Read/Unread State Tracking — Design Spec

**Issue:** #18
**Date:** 2026-03-17
**Status:** Approved

---

## Overview

Add read/unread state tracking to the feed. Messages auto-mark as read after 0.5 seconds of continuous visibility on screen. Read state persists across app restarts via Room. Read cards dim to 60% opacity. Filter chips show per-channel unread counts; long-pressing a chip marks that channel's messages as read.

---

## Data Layer

### New entity: `ReadMessageEntity`

```kotlin
@Entity(tableName = "read_messages")
data class ReadMessageEntity(@PrimaryKey val messageId: Long)
```

Stored in a dedicated table, decoupled from `messages`. Re-fetching messages via `insertAll(OnConflictStrategy.REPLACE)` does not affect this table, preserving read state across refreshes.

### New DAO: `ReadMessageDao`

```kotlin
@Dao
interface ReadMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markRead(entry: ReadMessageEntity)

    @Query("INSERT OR IGNORE INTO read_messages (messageId) SELECT id FROM messages WHERE channel = :channel")
    suspend fun markChannelRead(channel: String)

    @Query("INSERT OR IGNORE INTO read_messages (messageId) SELECT id FROM messages")
    suspend fun markAllRead()

    @Query("SELECT messageId FROM read_messages")
    fun observeReadIds(): Flow<Set<Long>>

    @Query("SELECT COUNT(*) FROM messages WHERE channel = :channel AND id NOT IN (SELECT messageId FROM read_messages)")
    fun observeUnreadCount(channel: String): Flow<Int>
}
```

All write operations are idempotent (insert-or-ignore). No error surface needed.

### DB migration: v2 → v3

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS read_messages (messageId INTEGER NOT NULL, PRIMARY KEY(messageId))")
    }
}
```

`AppDatabase` bumped to `version = 3`, `MIGRATION_2_3` registered in `DatabaseModule`.

### Domain model update

`Message` gains `val isRead: Boolean = false`.

---

## ViewModel

`FeedViewModel` changes:

- **`filteredMessages`**: `combine` of `messageDao.observeAll()` + `readMessageDao.observeReadIds()` → `Message` with `isRead` set from the read ID set.
- **`unreadCounts: StateFlow<Map<String, Int>>`**: derived from `readMessageDao.observeUnreadCount(channel)` for each active subscription, combined into a map.
- **`markRead(id: Long)`**: `viewModelScope.launch { readMessageDao.markRead(ReadMessageEntity(id)) }` — fire-and-forget.
- **`markChannelRead(channel: String)`**: calls `readMessageDao.markChannelRead(channel)`.
- **`markAllRead()`**: calls `readMessageDao.markAllRead()`.

`ReadMessageDao` injected into `FeedViewModel` via Hilt (added to `DatabaseModule`).

---

## Scroll-Based Read Detection (FeedScreen)

A `LaunchedEffect(lazyListState)` loop in `FeedScreen` polls every 200ms:

1. Maintains a `MutableMap<Long, Long>` (`messageId → firstVisibleTimestampMs`) in local state.
2. On each 200ms tick, reads `lazyListState.layoutInfo.visibleItemsInfo` to get currently visible item keys (message IDs).
3. Adds newly visible IDs to the map with the current timestamp; removes IDs that left visibility.
4. For any ID in the map where `now - firstVisible >= 500ms` and `message.isRead == false`, calls `viewModel.markRead(id)`.

This approach avoids any additional dependencies and works entirely within existing Compose/coroutine primitives.

---

## UI

### FeedItem — read dimming

```kotlin
ElevatedCard(
    modifier = Modifier
        .fillMaxWidth()
        .alpha(if (message.isRead) 0.6f else 1f),
    ...
)
```

No other layout changes.

### Filter chips — unread count badges

Chip label: `"${sub.channel} (${count})"` when `count > 0`, otherwise `sub.channel`. "All" chip shows total unread across all channels.

### Long-press chips — mark channel read

Wrap each `FilterChip` with `Modifier.combinedClickable(onLongClick = { ... })` from `androidx.compose.foundation` (already a transitive dependency — no new imports needed):

- Long-press on a channel chip → `viewModel.markChannelRead(sub.channel)`
- Long-press on "All" chip → `viewModel.markAllRead()`

---

## Testing

### Instrumented (`DatabaseTest`)

- Insert messages, mark one read → `observeReadIds()` returns it.
- `markChannelRead("channel")` → all messages for that channel appear in read set.
- Re-insert a message via `insertAll(REPLACE)` → read entry in `read_messages` is preserved.

### Manual

- Scroll past a card for 0.5s → card dims to 60% opacity.
- Long-press a channel chip → unread count clears, all cards in that channel dim.
- Kill and relaunch app → read state preserved.

---

## Files Affected

| File | Change |
|------|--------|
| `data/local/entity/ReadMessageEntity.kt` | New file |
| `data/local/dao/ReadMessageDao.kt` | New file |
| `data/local/AppDatabase.kt` | Add entity, bump version, add MIGRATION_2_3 |
| `di/DatabaseModule.kt` | Expose `ReadMessageDao` |
| `domain/model/Message.kt` | Add `isRead: Boolean = false` |
| `ui/feed/FeedViewModel.kt` | Inject dao, add read flows and mark functions |
| `ui/feed/FeedScreen.kt` | Dwell-time loop, alpha modifier, badge labels, long-press chips |
| `androidTest/DatabaseTest.kt` | Add read-state test cases |
