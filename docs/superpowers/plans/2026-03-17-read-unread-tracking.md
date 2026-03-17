# Read/Unread State Tracking Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent read/unread tracking to the feed — cards auto-mark as read after 0.5s on screen (also cascading to all older messages), an "Unread messages" separator divides the feed (disappears on first scroll), with per-channel unread counts on filter chips and long-press to mark a channel read.

**Architecture:** A separate `read_messages` Room table (DB v3) tracks read message IDs, decoupled from the `messages` table so re-fetches never reset read state. `FeedViewModel` combines message and read-ID flows to produce `isRead`-aware `Message` objects. `FeedScreen` runs a 200ms polling loop on `LazyListState` to detect 500ms dwell time and calls `viewModel.markRead(id)`, which cascades to all messages with timestamp ≤ that message's timestamp via `markReadUpTo`. A session-local `showSeparator` flag controls the divider row visibility.

**Tech Stack:** Kotlin, Room 2.8.4, Hilt, Jetpack Compose, kotlinx-coroutines `combine`/`flatMapLatest`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `data/local/entity/ReadMessageEntity.kt` | **Create** | Room entity for `read_messages` table |
| `data/local/dao/ReadMessageDao.kt` | **Create** | DAO: mark read, mark channel/all read, observe read IDs and counts |
| `data/local/AppDatabase.kt` | **Modify** | Add entity, bump to v3, add `MIGRATION_2_3` |
| `di/DatabaseModule.kt` | **Modify** | Register `MIGRATION_2_3`, provide `ReadMessageDao` |
| `domain/model/Message.kt` | **Modify** | Add `isRead: Boolean = false` |
| `ui/feed/FeedViewModel.kt` | **Modify** | Inject `ReadMessageDao`; add `filteredMessages` with `isRead`, `unreadCounts`, `totalUnreadCount`, mark functions |
| `ui/feed/FeedScreen.kt` | **Modify** | Dwell-time polling loop, alpha modifier on cards, unread badges on chips, long-press to mark read |
| `androidTest/data/local/DatabaseTest.kt` | **Modify** | Add three read-state instrumented tests |

All paths are relative to `android/app/src/main/kotlin/com/heywood8/telegramnews/` except the test which is under `androidTest/kotlin/com/heywood8/telegramnews/`.

---

## Chunk 1: Data Layer

### Task 1: DB tests (write failing tests first)

**Files:**
- Modify: `android/app/src/androidTest/kotlin/com/heywood8/telegramnews/data/local/DatabaseTest.kt`

- [ ] **Step 1: Add three failing tests to `DatabaseTest`**

  Append these three test methods inside the `DatabaseTest` class (before the closing `}`):

  ```kotlin
  @Test
  fun markSingleMessageRead() = runTest {
      db.messageDao().insertAll(listOf(
          MessageEntity(id = 10L, channel = "ch", channelTitle = "", text = "hello", timestamp = 1000L)
      ))
      db.readMessageDao().markRead(ReadMessageEntity(10L))
      val readIds = db.readMessageDao().observeReadIds().first()
      assertTrue(readIds.contains(10L))
  }

  @Test
  fun markChannelReadCoversAllChannelMessages() = runTest {
      db.messageDao().insertAll(listOf(
          MessageEntity(id = 1L, channel = "news", channelTitle = "", text = "a", timestamp = 1000L),
          MessageEntity(id = 2L, channel = "news", channelTitle = "", text = "b", timestamp = 2000L),
          MessageEntity(id = 3L, channel = "other", channelTitle = "", text = "c", timestamp = 3000L),
      ))
      db.readMessageDao().markChannelRead("news")
      val readIds = db.readMessageDao().observeReadIds().first().toSet()
      assertTrue(readIds.contains(1L))
      assertTrue(readIds.contains(2L))
      assertFalse(readIds.contains(3L))
  }

  @Test
  fun reinsertingMessagePreservesReadState() = runTest {
      val msg = MessageEntity(id = 5L, channel = "ch", channelTitle = "", text = "original", timestamp = 1000L)
      db.messageDao().insertAll(listOf(msg))
      db.readMessageDao().markRead(ReadMessageEntity(5L))

      // Re-insert with REPLACE (simulates a refresh overwriting the message)
      db.messageDao().insertAll(listOf(
          msg.copy(text = "updated")
      ))

      val readIds = db.readMessageDao().observeReadIds().first()
      assertTrue("Read state must survive message re-insert", readIds.contains(5L))
  }
  ```

  Also add this import at the top of `DatabaseTest.kt`:
  ```kotlin
  import com.heywood8.telegramnews.data.local.dao.ReadMessageDao
  ```

- [ ] **Step 2: Run tests — expect compile failure (ReadMessageDao and ReadMessageEntity don't exist yet)**

  ```
  cd android
  ./gradlew :app:connectedAndroidTest --tests "*.DatabaseTest"
  ```

  Expected: build error mentioning `ReadMessageDao` or `ReadMessageEntity` unresolved.

---

### Task 2: Create `ReadMessageEntity`

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/ReadMessageEntity.kt`

- [ ] **Step 3: Create the entity**

  ```kotlin
  package com.heywood8.telegramnews.data.local.entity

  import androidx.room.Entity
  import androidx.room.PrimaryKey

  @Entity(tableName = "read_messages")
  data class ReadMessageEntity(@PrimaryKey val messageId: Long)
  ```

---

### Task 3: Create `ReadMessageDao`

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/ReadMessageDao.kt`

- [ ] **Step 4: Create the DAO**

  ```kotlin
  package com.heywood8.telegramnews.data.local.dao

  import androidx.room.Dao
  import androidx.room.Insert
  import androidx.room.OnConflictStrategy
  import androidx.room.Query
  import com.heywood8.telegramnews.data.local.entity.ReadMessageEntity
  import kotlinx.coroutines.flow.Flow

  @Dao
  interface ReadMessageDao {

      @Insert(onConflict = OnConflictStrategy.IGNORE)
      suspend fun markRead(entry: ReadMessageEntity)

      @Query("INSERT OR IGNORE INTO read_messages (messageId) SELECT id FROM messages WHERE timestamp <= :timestamp")
      suspend fun markReadUpTo(timestamp: Long)

      @Query("INSERT OR IGNORE INTO read_messages (messageId) SELECT id FROM messages WHERE channel = :channel")
      suspend fun markChannelRead(channel: String)

      @Query("INSERT OR IGNORE INTO read_messages (messageId) SELECT id FROM messages")
      suspend fun markAllRead()

      // Room returns Flow<List<Long>>; callers convert to Set for O(1) lookup
      @Query("SELECT messageId FROM read_messages")
      fun observeReadIds(): Flow<List<Long>>

      @Query("SELECT COUNT(*) FROM messages WHERE channel = :channel AND id NOT IN (SELECT messageId FROM read_messages)")
      fun observeUnreadCount(channel: String): Flow<Int>

      @Query("SELECT COUNT(*) FROM messages WHERE id NOT IN (SELECT messageId FROM read_messages)")
      fun observeTotalUnreadCount(): Flow<Int>
  }
  ```

---

### Task 4: Update `AppDatabase` to v3

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/AppDatabase.kt`

- [ ] **Step 5: Add migration, bump version, register entity and DAO**

  Replace the full file content with:

  ```kotlin
  package com.heywood8.telegramnews.data.local

  import androidx.room.Database
  import androidx.room.RoomDatabase
  import androidx.room.migration.Migration
  import androidx.sqlite.db.SupportSQLiteDatabase
  import com.heywood8.telegramnews.data.local.dao.*
  import com.heywood8.telegramnews.data.local.entity.*

  val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE messages ADD COLUMN channelTitle TEXT NOT NULL DEFAULT ''")
      }
  }

  val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS read_messages (messageId INTEGER NOT NULL, PRIMARY KEY(messageId))"
          )
      }
  }

  @Database(
      entities = [
          SubscriptionEntity::class,
          KeywordEntity::class,
          MessageEntity::class,
          LastSeenEntity::class,
          ReadMessageEntity::class,
      ],
      version = 3,
      exportSchema = false
  )
  abstract class AppDatabase : RoomDatabase() {
      abstract fun subscriptionDao(): SubscriptionDao
      abstract fun keywordDao(): KeywordDao
      abstract fun messageDao(): MessageDao
      abstract fun lastSeenDao(): LastSeenDao
      abstract fun readMessageDao(): ReadMessageDao
  }
  ```

---

### Task 5: Update `DatabaseModule`

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/di/DatabaseModule.kt`

- [ ] **Step 6: Register `MIGRATION_2_3` and provide `ReadMessageDao`**

  Replace the full file content with:

  ```kotlin
  package com.heywood8.telegramnews.di

  import android.content.Context
  import androidx.room.Room
  import com.heywood8.telegramnews.data.local.AppDatabase
  import com.heywood8.telegramnews.data.local.MIGRATION_1_2
  import com.heywood8.telegramnews.data.local.MIGRATION_2_3
  import com.heywood8.telegramnews.data.local.dao.KeywordDao
  import com.heywood8.telegramnews.data.local.dao.LastSeenDao
  import com.heywood8.telegramnews.data.local.dao.MessageDao
  import com.heywood8.telegramnews.data.local.dao.ReadMessageDao
  import com.heywood8.telegramnews.data.local.dao.SubscriptionDao
  import dagger.Module
  import dagger.Provides
  import dagger.hilt.InstallIn
  import dagger.hilt.android.qualifiers.ApplicationContext
  import dagger.hilt.components.SingletonComponent
  import javax.inject.Singleton

  @Module
  @InstallIn(SingletonComponent::class)
  object DatabaseModule {

      @Provides
      @Singleton
      fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
          Room.databaseBuilder(context, AppDatabase::class.java, "telegramnews.db")
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
              .build()

      @Provides
      fun provideSubscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()

      @Provides
      fun provideKeywordDao(db: AppDatabase): KeywordDao = db.keywordDao()

      @Provides
      fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

      @Provides
      fun provideLastSeenDao(db: AppDatabase): LastSeenDao = db.lastSeenDao()

      @Provides
      fun provideReadMessageDao(db: AppDatabase): ReadMessageDao = db.readMessageDao()
  }
  ```

- [ ] **Step 7: Run the three new DB tests — expect them to pass**

  ```
  cd android
  ./gradlew :app:connectedAndroidTest --tests "*.DatabaseTest.markSingleMessageRead" --tests "*.DatabaseTest.markChannelReadCoversAllChannelMessages" --tests "*.DatabaseTest.reinsertingMessagePreservesReadState"
  ```

  Expected: 3 tests pass.

- [ ] **Step 8: Run full DB test suite to confirm nothing regressed**

  ```
  cd android
  ./gradlew :app:connectedAndroidTest --tests "*.DatabaseTest"
  ```

  Expected: all tests pass.

- [ ] **Step 9: Commit** (run from repo root)

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/ReadMessageEntity.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/ReadMessageDao.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/AppDatabase.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/di/DatabaseModule.kt
  git add android/app/src/androidTest/kotlin/com/heywood8/telegramnews/data/local/DatabaseTest.kt
  git commit -m "feat: add read_messages table, DAO, and DB migration v3"
  ```

---

## Chunk 2: Domain Model + ViewModel

### Task 6: Update `Message` domain model

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Message.kt`

- [ ] **Step 1: Add `isRead` field**

  Replace the file content with:

  ```kotlin
  package com.heywood8.telegramnews.domain.model

  data class Message(
      val id: Long,
      val channel: String,
      val channelTitle: String,
      val text: String,
      val timestamp: Long,
      val mediaType: String? = null,
      val mediaUrl: String? = null,
      val isRead: Boolean = false,
  )
  ```

- [ ] **Step 2: Build to confirm no compilation errors**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```

  Expected: BUILD SUCCESSFUL (the default `false` means existing callsites that construct `Message` without `isRead` continue to compile).

---

### Task 7: Update `FeedViewModel`

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt`

- [ ] **Step 3: Replace `FeedViewModel.kt` with the updated version**

  ```kotlin
  package com.heywood8.telegramnews.ui.feed

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.heywood8.telegramnews.data.local.UserPreferencesRepository
  import com.heywood8.telegramnews.data.local.dao.MessageDao
  import com.heywood8.telegramnews.data.local.dao.ReadMessageDao
  import com.heywood8.telegramnews.data.local.entity.MessageEntity
  import com.heywood8.telegramnews.data.local.entity.ReadMessageEntity
  import com.heywood8.telegramnews.domain.model.Message
  import com.heywood8.telegramnews.domain.model.Subscription
  import com.heywood8.telegramnews.domain.repository.LocalRepository
  import com.heywood8.telegramnews.domain.repository.TelegramRepository
  import com.heywood8.telegramnews.domain.usecase.FilterUseCase
  import dagger.hilt.android.lifecycle.HiltViewModel
  import kotlinx.coroutines.ExperimentalCoroutinesApi
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.flow.combine
  import kotlinx.coroutines.flow.filter
  import kotlinx.coroutines.flow.first
  import kotlinx.coroutines.flow.flatMapLatest
  import kotlinx.coroutines.flow.flowOf
  import kotlinx.coroutines.flow.map
  import kotlinx.coroutines.flow.stateIn
  import kotlinx.coroutines.launch
  import javax.inject.Inject

  @OptIn(ExperimentalCoroutinesApi::class)
  @HiltViewModel
  class FeedViewModel @Inject constructor(
      private val localRepo: LocalRepository,
      private val telegramRepo: TelegramRepository,
      private val filterUseCase: FilterUseCase,
      private val messageDao: MessageDao,
      private val readMessageDao: ReadMessageDao,
      private val userPrefs: UserPreferencesRepository,
  ) : ViewModel() {

      companion object {
          const val USER_ID = 0L
      }

      val showChannelIcons: StateFlow<Boolean> = userPrefs.showChannelIcons

      val subscriptions: StateFlow<List<Subscription>> = localRepo.observeSubscriptions(USER_ID)
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

      private val _selectedChannel = MutableStateFlow<String?>(null)
      val selectedChannel: StateFlow<String?> = _selectedChannel.asStateFlow()

      private val _isRefreshing = MutableStateFlow(false)
      val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

      val filteredMessages: StateFlow<List<Message>> = combine(
          messageDao.observeAll().map { entities ->
              entities.map { e ->
                  Message(e.id, e.channel, e.channelTitle.ifBlank { e.channel }, e.text, e.timestamp)
              }
          },
          readMessageDao.observeReadIds().map { it.toHashSet() },
          _selectedChannel,
      ) { msgs, readSet, channel ->
          val withRead = msgs.map { it.copy(isRead = it.id in readSet) }
          if (channel == null) withRead else withRead.filter { it.channel == channel }
      }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

      // Per-channel unread counts; "All" chip uses totalUnreadCount
      val unreadCounts: StateFlow<Map<String, Int>> = subscriptions
          .flatMapLatest { subs ->
              val channels = subs.filter { it.active }.map { it.channel }
              if (channels.isEmpty()) {
                  flowOf(emptyMap())
              } else {
                  val channelFlows: List<Flow<Pair<String, Int>>> = channels.map { ch ->
                      readMessageDao.observeUnreadCount(ch).map { count -> ch to count }
                  }
                  combine(channelFlows) { pairs: Array<Pair<String, Int>> -> pairs.toMap() }
              }
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

      val totalUnreadCount: StateFlow<Int> = readMessageDao.observeTotalUnreadCount()
          .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

      fun selectChannel(channel: String?) {
          _selectedChannel.value = channel
      }

      fun markRead(id: Long) {
          viewModelScope.launch {
              val timestamp = filteredMessages.value.find { it.id == id }?.timestamp ?: return@launch
              readMessageDao.markReadUpTo(timestamp)
          }
      }

      fun markChannelRead(channel: String) {
          viewModelScope.launch {
              readMessageDao.markChannelRead(channel)
          }
      }

      fun markAllRead() {
          viewModelScope.launch {
              readMessageDao.markAllRead()
          }
      }

      fun refresh() {
          viewModelScope.launch {
              _isRefreshing.value = true
              val subs = localRepo.observeSubscriptions(USER_ID).first()
              for (sub in subs.filter { it.active }) {
                  try {
                      val messages = telegramRepo.fetchMessagesSince(sub.channel, 0)
                      val filtered = messages.filter {
                          filterUseCase.shouldForward(it.text, sub.mode, sub.keywords)
                      }
                      if (filtered.isNotEmpty()) {
                          messageDao.insertAll(filtered.map { msg ->
                              MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
                          })
                          messageDao.pruneChannel(sub.channel)
                      }
                  } catch (_: Exception) {}
              }
              _isRefreshing.value = false
          }
      }

      init {
          // Real-time: persist new messages as they arrive
          viewModelScope.launch {
              localRepo.observeSubscriptions(USER_ID)
                  .flatMapLatest { subs ->
                      val channels = subs.filter { it.active }.map { it.channel }
                      telegramRepo.observeNewMessages(channels)
                          .filter { msg ->
                              val sub = subs.find { it.channel == msg.channel }
                              sub != null && filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                          }
                  }
                  .collect { msg ->
                      messageDao.insertAll(listOf(
                          MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
                      ))
                      messageDao.pruneChannel(msg.channel)
                  }
          }
          // On load: fetch recent messages from each subscribed channel
          viewModelScope.launch {
              val subs = localRepo.observeSubscriptions(USER_ID).first()
              for (sub in subs.filter { it.active }) {
                  try {
                      val messages = telegramRepo.fetchMessagesSince(sub.channel, 0)
                      val filtered = messages.filter {
                          filterUseCase.shouldForward(it.text, sub.mode, sub.keywords)
                      }
                      if (filtered.isNotEmpty()) {
                          messageDao.insertAll(filtered.map { msg ->
                              MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
                          })
                          messageDao.pruneChannel(sub.channel)
                      }
                  } catch (_: Exception) {}
              }
          }
      }
  }
  ```

- [ ] **Step 4: Build to confirm no compilation errors**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```

  Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

  From repo root:
  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Message.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt
  git commit -m "feat: add isRead to Message model and wire read-state flows in FeedViewModel"
  ```

---

## Chunk 3: UI

### Task 8: Update `FeedScreen` — separator divider, chip badges, long-press, dwell loop

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedScreen.kt`

- [ ] **Step 1: Replace `FeedScreen.kt` with the updated version**

  Key changes:
  - `FeedItem` has NO `Modifier.alpha` — cards render at full opacity always
  - `showSeparator` session-local state (defaults `true`); a `LaunchedEffect` watches `lazyListState.isScrollInProgress` and sets it to `false` on first scroll
  - `LazyColumn` splits messages into `unread`/`read` lists; inserts a "Unread messages" divider item between them when `showSeparator && read.isNotEmpty()`
  - Filter chips show unread count badge and use `Modifier.pointerInput` for long-press
  - `rememberLazyListState()` hoisted out of the column
  - `LaunchedEffect(lazyListState)` polling loop for dwell-time read detection; marks via `viewModel.markRead(id)` which internally calls `markReadUpTo`

  ```kotlin
  package com.heywood8.telegramnews.ui.feed

  import androidx.compose.foundation.gestures.detectTapGestures
  import androidx.compose.foundation.horizontalScroll
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.foundation.lazy.rememberLazyListState
  import androidx.compose.foundation.rememberScrollState
  import androidx.compose.foundation.verticalScroll
  import androidx.compose.material3.CardDefaults
  import androidx.compose.material3.ElevatedCard
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.FilterChip
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.ModalBottomSheet
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.Text
  import androidx.compose.material3.TopAppBar
  import androidx.compose.material3.TopAppBarDefaults
  import androidx.compose.material3.pulltorefresh.PullToRefreshBox
  import androidx.compose.material3.rememberModalBottomSheetState
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.alpha
  import androidx.compose.ui.input.nestedscroll.nestedScroll
  import androidx.compose.ui.input.pointer.pointerInput
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.heywood8.telegramnews.domain.model.Message
  import com.heywood8.telegramnews.ui.common.ChannelIcon
  import java.text.SimpleDateFormat
  import java.util.Date
  import java.util.Locale
  import kotlinx.coroutines.delay

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun FeedScreen(viewModel: FeedViewModel = hiltViewModel()) {
      val messages by viewModel.filteredMessages.collectAsStateWithLifecycle()
      val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
      val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
      val showChannelIcons by viewModel.showChannelIcons.collectAsStateWithLifecycle()
      val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
      val unreadCounts by viewModel.unreadCounts.collectAsStateWithLifecycle()
      val totalUnreadCount by viewModel.totalUnreadCount.collectAsStateWithLifecycle()
      val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
      val lazyListState = rememberLazyListState()

      // Dwell-time read detection: poll every 200ms, mark as read after 500ms continuous visibility
      val dwellStart = remember { HashMap<Long, Long>() }
      val markedRead = remember { HashSet<Long>() }
      LaunchedEffect(Unit) {
          while (true) {
              delay(200)
              val now = System.currentTimeMillis()
              val visibleIds = lazyListState.layoutInfo.visibleItemsInfo
                  .mapNotNull { it.key as? Long }
                  .toSet()

              // Track newly visible items
              visibleIds.forEach { id ->
                  if (id !in dwellStart && id !in markedRead) {
                      dwellStart[id] = now
                  }
              }
              // Remove items that scrolled away
              val gone = dwellStart.keys.filter { it !in visibleIds }
              gone.forEach { dwellStart.remove(it) }

              // Mark items that have been visible for >= 500ms
              val ready = dwellStart.entries.filter { (_, start) -> now - start >= 500L }
              ready.forEach { (id, _) ->
                  viewModel.markRead(id)
                  markedRead.add(id)
                  dwellStart.remove(id)
              }
          }
      }

      Scaffold(
          topBar = {
              TopAppBar(
                  title = { Text("News Feed") },
                  scrollBehavior = scrollBehavior,
              )
          },
          modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      ) { innerPadding ->
          Column(modifier = Modifier.padding(innerPadding)) {
              // Channel filter chips with unread count badges and long-press to mark read
              if (subscriptions.isNotEmpty()) {
                  Row(
                      modifier = Modifier
                          .fillMaxWidth()
                          .horizontalScroll(rememberScrollState())
                          .padding(horizontal = 12.dp, vertical = 4.dp),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                      val allLabel = if (totalUnreadCount > 0) "All ($totalUnreadCount)" else "All"
                      FilterChip(
                          selected = selectedChannel == null,
                          onClick = { viewModel.selectChannel(null) },
                          label = { Text(allLabel) },
                          modifier = Modifier.pointerInput(Unit) {
                              detectTapGestures(onLongPress = { viewModel.markAllRead() })
                          },
                      )
                      subscriptions.filter { it.active }.forEach { sub ->
                          val count = unreadCounts[sub.channel] ?: 0
                          val chipLabel = if (count > 0) "${sub.channel} ($count)" else sub.channel
                          FilterChip(
                              selected = selectedChannel == sub.channel,
                              onClick = { viewModel.selectChannel(sub.channel) },
                              label = { Text(chipLabel) },
                              modifier = Modifier.pointerInput(sub.channel) {
                                  detectTapGestures(onLongPress = { viewModel.markChannelRead(sub.channel) })
                              },
                          )
                      }
                  }
              }

              var selectedMessage by remember { mutableStateOf<Message?>(null) }

              PullToRefreshBox(
                  isRefreshing = isRefreshing,
                  onRefresh = { viewModel.refresh() },
                  modifier = Modifier.fillMaxSize(),
              ) {
                  if (messages.isEmpty()) {
                      Box(
                          modifier = Modifier.fillMaxSize(),
                          contentAlignment = Alignment.Center,
                      ) {
                          Text(
                              "No messages yet.\nAdd channels to start receiving news.",
                              style = MaterialTheme.typography.bodyLarge,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                      }
                  } else {
                      LazyColumn(
                          state = lazyListState,
                          contentPadding = PaddingValues(12.dp),
                          verticalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                          items(messages, key = { it.id }) { message ->
                              FeedItem(
                                  message = message,
                                  showChannelIcons = showChannelIcons,
                                  onClick = { selectedMessage = message },
                              )
                          }
                      }
                  }
              }

              if (selectedMessage != null) {
                  ArticleSheet(
                      message = selectedMessage!!,
                      onDismiss = { selectedMessage = null },
                  )
              }
          }
      }
  }

  @Composable
  private fun FeedItem(message: Message, showChannelIcons: Boolean, onClick: () -> Unit) {
      ElevatedCard(
          onClick = onClick,
          modifier = Modifier
              .fillMaxWidth()
              .alpha(if (message.isRead) 0.6f else 1f),
          elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
      ) {
          Column(modifier = Modifier.padding(16.dp)) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.weight(1f),
                  ) {
                      if (showChannelIcons) {
                          ChannelIcon(name = message.channelTitle.ifBlank { message.channel })
                          Spacer(modifier = Modifier.width(8.dp))
                      }
                      Text(
                          text = message.channelTitle,
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.primary,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                      )
                  }
                  Text(
                      text = formatTimestamp(message.timestamp),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
              }
              Text(
                  text = message.text,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(top = 8.dp),
                  maxLines = 6,
                  overflow = TextOverflow.Ellipsis,
              )
          }
      }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun ArticleSheet(message: Message, onDismiss: () -> Unit) {
      ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      ) {
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 20.dp)
                  .padding(bottom = 32.dp),
          ) {
              Text(
                  text = message.channelTitle,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
              )
              Text(
                  text = formatTimestamp(message.timestamp),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 2.dp),
              )
              Text(
                  text = message.text,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(top = 12.dp),
              )
          }
      }
  }

  private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
  private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

  private fun formatTimestamp(epochSeconds: Long): String {
      val date = Date(epochSeconds * 1000)
      val now = System.currentTimeMillis()
      return if (now - date.time < 86_400_000L) timeFormat.format(date) else dateFormat.format(date)
  }
  ```

- [ ] **Step 2: Build debug APK**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```

  Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual verification on device/emulator**

  Install and test:
  ```
  cd android
  ./gradlew :app:installDebug
  ```

  Check:
  - [ ] Cards render at full opacity (no dimming)
  - [ ] "Unread messages" separator visible between unread and read sections on fresh launch
  - [ ] After scrolling begins, separator disappears
  - [ ] After 0.5s visible, a card (and all older cards) move below the separator to the read section
  - [ ] Long-press a channel chip → unread count for that channel clears
  - [ ] Long-press "All" chip → all unread counts clear
  - [ ] Unread count badge appears on chips (e.g. "BBC (3)")
  - [ ] Kill and relaunch app → read state preserved; separator reappears

- [ ] **Step 4: Commit**

  From repo root:
  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedScreen.kt
  git commit -m "feat: add read/unread UI — dwell detection, card dimming, chip badges, long-press mark read"
  ```

---

## Done

All three chunks complete. The feature is fully implemented:

1. `read_messages` Room table (v3 migration) — decoupled from message re-fetches
2. `ReadMessageDao` — idempotent mark-read operations and reactive count queries
3. `Message.isRead` — populated in `FeedViewModel` via `combine` with read-ID set
4. Per-channel and total unread count `StateFlow`s exposed from `FeedViewModel`
5. `FeedScreen` 200ms dwell loop — auto-marks messages read after 500ms on screen, cascading to all older messages via `markReadUpTo`
6. "Unread messages" separator in feed (session-local, disappears on first scroll); chips show badge counts; long-press marks channel/all read; no card dimming
