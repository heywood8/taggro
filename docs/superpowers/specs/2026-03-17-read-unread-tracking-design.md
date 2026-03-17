# Read/Unread State Tracking — Design Spec

**Issue:** #18
**Date:** 2026-03-17
**Status:** Approved

---

## Overview

Add read/unread state tracking to the feed. Messages auto-mark as read after 0.5 seconds of continuous visibility on screen; marking any message also marks all older messages (lower timestamp) as read automatically. Read state persists across app restarts via Room. An "Unread messages" separator divides unread from read messages in the feed; it disappears once the user starts scrolling. Filter chips show per-channel unread counts; long-pressing a chip marks that channel's messages as read.

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
    // Marks a single message as read (used only for explicit single-item marking)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markRead(entry: ReadMessageEntity)

    // Marks all messages with timestamp <= :timestamp as read (bulk cascade for dwell-time trigger)
    @Query("INSERT OR IGNORE INTO read_messages (messageId) SELECT id FROM messages WHERE timestamp <= :timestamp")
    suspend fun markReadUpTo(timestamp: Long)

    @Query("INSERT OR IGNORE INTO read_messages (messageId) SELECT id FROM messages WHERE channel = :channel")
    suspend fun markChannelRead(channel: String)

    @Query("INSERT OR IGNORE INTO read_messages (messageId) SELECT id FROM messages")
    suspend fun markAllRead()

    // Room returns Flow<List<Long>>; ViewModel converts to Set for O(1) lookup
    @Query("SELECT messageId FROM read_messages")
    fun observeReadIds(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM messages WHERE channel = :channel AND id NOT IN (SELECT messageId FROM read_messages)")
    fun observeUnreadCount(channel: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE id NOT IN (SELECT messageId FROM read_messages)")
    fun observeTotalUnreadCount(): Flow<Int>
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

- **`filteredMessages`**: `combine` of `messageDao.observeAll()` + `readMessageDao.observeReadIds().map { it.toHashSet() }` → `Message` with `isRead` set via `id in readSet`. The Set conversion happens in the ViewModel for O(1) per-message lookup.
- **`unreadCounts: StateFlow<Map<String, Int>>`**: uses `flatMapLatest` on the subscriptions flow to call `observeUnreadCount(channel)` per active channel, then `combine`s the per-channel flows into a `Map<String, Int>`. The "All" chip total comes from `observeTotalUnreadCount()`.
- **`markRead(id: Long)`**: looks up the message's timestamp from `filteredMessages.value`, then calls `readMessageDao.markReadUpTo(timestamp)` — marks that message and all older messages as read. Fire-and-forget.
- **`markChannelRead(channel: String)`**: calls `readMessageDao.markChannelRead(channel)`.
- **`markAllRead()`**: calls `readMessageDao.markAllRead()`.

`ReadMessageDao` injected into `FeedViewModel` via Hilt (added to `DatabaseModule`).

---

## Scroll-Based Read Detection (FeedScreen)

A `LaunchedEffect(lazyListState)` loop in `FeedScreen` polls every 200ms:

1. Maintains a `MutableMap<Long, Long>` (`messageId → firstVisibleTimestampMs`) in local state.
2. On each 200ms tick, reads `lazyListState.layoutInfo.visibleItemsInfo` to get currently visible item keys (message IDs).
3. Adds newly visible IDs to the map with the current timestamp; removes IDs that left visibility.
4. For any ID in the map where `now - firstVisible >= 500ms` and `message.isRead == false`, calls `viewModel.markRead(id)`, which cascades to all older messages via `markReadUpTo`.

This approach avoids any additional dependencies and works entirely within existing Compose/coroutine primitives.

---

## UI

### FeedItem — no visual dimming

Cards are rendered at full opacity regardless of read state. The `isRead` field is still populated (used for separator positioning and unread counts) but does not affect card appearance.

### "Unread messages" separator

A static divider row is inserted into the `LazyColumn` between the last unread message and the first read message. It displays the text "Unread messages" with a secondary style.

The separator is only shown while `showSeparator` is `true` (a `remember { mutableStateOf(true) }` local state). Once the user starts scrolling (`lazyListState.isScrollInProgress` transitions to `true`), `showSeparator` is set to `false` permanently for that session.

```kotlin
// Detect first scroll, hide separator
LaunchedEffect(lazyListState) {
    snapshotFlow { lazyListState.isScrollInProgress }
        .filter { it }
        .first()
    showSeparator = false
}
```

In the `LazyColumn`:
- Items are split into `unread` (not read) and `read` (is read) lists, preserving original order within each group.
- Unread items are emitted first; if `showSeparator && read.isNotEmpty()`, a separator item with `key = "separator"` is inserted; then read items follow.

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

- Scroll past a card for 0.5s → card and all older cards move to the "read" section below the separator.
- Separator disappears once the user starts scrolling.
- Long-press a channel chip → unread count clears for that channel.
- Long-press "All" chip → all unread counts clear.
- Kill and relaunch app → read state preserved; separator is shown again on next launch (session-local state).

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
| `ui/feed/FeedScreen.kt` | Dwell-time loop, separator divider, badge labels, long-press chips (no alpha modifier) |
| `androidTest/DatabaseTest.kt` | Add read-state test cases |
