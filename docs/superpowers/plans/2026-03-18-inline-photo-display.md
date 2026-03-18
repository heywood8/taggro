# Inline Photo Display Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Download and render actual Telegram photos inline in feed cards (layout user-configurable) and full-width in the ArticleSheet detail view.

**Architecture:** Photo file IDs are extracted at message-parse time and stored in Room. At display time the UI triggers an on-demand TDLib file download via `produceState`, then loads the local file path with Coil `AsyncImage`. A `PhotoLayout` enum in SharedPreferences controls whether inline thumbnails appear above, below, or to the left of the message text.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room (migrations), TDLib (`api.sendFunctionAsync`), Hilt, Coil 3 (`io.coil-kt.coil3:coil-compose` + `coil-android` 3.1.0)

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `domain/model/MediaType.kt` | `MediaType.PHOTO` constant |
| Create | `domain/model/PhotoLayout.kt` | `PhotoLayout` enum (ABOVE/BELOW/LEFT) |
| Modify | `domain/model/Message.kt` | Add `photoFileId: Int?` |
| Modify | `data/local/entity/MessageEntity.kt` | Add `photoFileId: Int?` column |
| Modify | `data/local/AppDatabase.kt` | Add `MIGRATION_3_4`, bump version 3→4 |
| Modify | `di/DatabaseModule.kt` | Register `MIGRATION_3_4` |
| Modify | `domain/repository/TelegramRepository.kt` | Add `downloadFile` |
| Modify | `data/telegram/TelegramRepositoryImpl.kt` | `extractPhotoFileId`, `extractMediaType`, `downloadFile`, fix both fetch methods |
| Modify | `ui/feed/FeedViewModel.kt` | Read path, 3 write paths, shouldForward bypass, `photoLayout`, `getPhotoPath` |
| Modify | `worker/SyncWorker.kt` | Propagate `photoFileId` + `mediaType` in `MessageEntity` |
| Modify | `data/local/UserPreferencesRepository.kt` | Add `photoLayout` preference |
| Modify | `ui/settings/SettingsViewModel.kt` | Expose `photoLayout` |
| Modify | `ui/settings/SettingsScreen.kt` | Segmented button for photo layout |
| Modify | `android/app/build.gradle.kts` | Add Coil 3 dependency |
| Modify | `ui/feed/FeedScreen.kt` | `FeedItem` photo rendering, `ArticleSheet` photo rendering, wiring |
| Create | `test/.../PhotoDisplayDataLayerTest.kt` | Unit tests: shouldForward bypass predicate |

All paths are relative to `android/app/src/main/kotlin/com/heywood8/telegramnews/` unless noted.

---

## Chunk 1: Foundation — Domain Models, DB, TelegramRepository

### Task 1: MediaType constant + Message + MessageEntity + DB migration

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/MediaType.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Message.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/MessageEntity.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/AppDatabase.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/di/DatabaseModule.kt`

- [ ] **Step 1: Create MediaType constant**

  Create `domain/model/MediaType.kt`:
  ```kotlin
  package com.heywood8.telegramnews.domain.model

  object MediaType {
      const val PHOTO = "photo"
  }
  ```

- [ ] **Step 2: Add `photoFileId` to Message**

  In `domain/model/Message.kt`, add the field after `mediaUrl`:
  ```kotlin
  data class Message(
      val id: Long,
      val channel: String,
      val channelTitle: String,
      val text: String,
      val timestamp: Long,
      val mediaType: String? = null,
      val mediaUrl: String? = null,
      val photoFileId: Int? = null,
      val isRead: Boolean = false,
  )
  ```

- [ ] **Step 3: Add `photoFileId` to MessageEntity**

  In `data/local/entity/MessageEntity.kt`, add after `mediaUrl`. The current file has no trailing comma on `val mediaUrl: String? = null` — add the comma, then append the new field. Final result:
  ```kotlin
  @Entity(tableName = "messages")
  data class MessageEntity(
      @PrimaryKey val id: Long,
      val channel: String,
      val channelTitle: String,
      val text: String,
      val timestamp: Long,
      val mediaType: String? = null,
      val mediaUrl: String? = null,
      @ColumnInfo(name = "photo_file_id")
      val photoFileId: Int? = null,
  )
  ```

  Add the missing import at the top: `import androidx.room.ColumnInfo`

- [ ] **Step 4: Add MIGRATION_3_4 and bump DB version**

  In `data/local/AppDatabase.kt`, add after `MIGRATION_2_3` and change `version = 3` to `version = 4`:
  ```kotlin
  val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE messages ADD COLUMN photo_file_id INTEGER")
      }
  }

  @Database(
      entities = [...],
      version = 4,
      exportSchema = false
  )
  ```

- [ ] **Step 5: Register MIGRATION_3_4 in DatabaseModule**

  In `di/DatabaseModule.kt`, find the `.addMigrations(...)` call and add `MIGRATION_3_4`. Also add the import `import com.heywood8.telegramnews.data.local.MIGRATION_3_4`. The result should look like:
  ```kotlin
  .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
  ```

- [ ] **Step 6: Build to verify no compile errors**

  Run from `android/` directory:
  ```
  ./gradlew :app:assembleDebug
  ```
  Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/MediaType.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Message.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/MessageEntity.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/AppDatabase.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/di/DatabaseModule.kt
  git commit -m "feat: add photoFileId to Message/MessageEntity, DB migration 3→4"
  ```

---

### Task 2: TelegramRepository + TelegramRepositoryImpl

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/TelegramRepository.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TelegramRepositoryImpl.kt`

- [ ] **Step 1: Add `downloadFile` to TelegramRepository interface**

  In `domain/repository/TelegramRepository.kt`, add:
  ```kotlin
  suspend fun downloadFile(fileId: Int): String?
  ```

- [ ] **Step 2: Add helpers and `downloadFile` to TelegramRepositoryImpl**

  In `data/telegram/TelegramRepositoryImpl.kt`, add these private helpers before `extractText`:
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

  Add the `MediaType` import at the top:
  ```kotlin
  import com.heywood8.telegramnews.domain.model.MediaType
  ```

  Add `downloadFile` implementation before `extractText`:
  ```kotlin
  override suspend fun downloadFile(fileId: Int): String? = try {
      val file = api.sendFunctionAsync(TdApi.DownloadFile(fileId, 1, 0L, 0L, true))
      if (file.local.isDownloadingCompleted) file.local.path else null
  } catch (_: Exception) { null }
  ```
  Note: `offset` and `limit` are `Long` in TDLib — use `0L`, not `0`.

- [ ] **Step 3: Fix `fetchMessagesSince` to include photos**

  Replace the current body of `fetchMessagesSince` (lines 152–168 of current file):
  ```kotlin
  override suspend fun fetchMessagesSince(
      channel: String,
      afterMessageId: Long
  ): List<Message> {
      return try {
          val chat = api.sendFunctionAsync(TdApi.SearchPublicChat(channel))
          val result = api.sendFunctionAsync(TdApi.GetChatHistory(chat.id, afterMessageId, 0, 50, false))
          result.messages?.mapNotNull { msg ->
              val text = extractText(msg.content, chat.title)
              val mediaType = extractMediaType(msg.content)
              val photoFileId = extractPhotoFileId(msg.content)
              // Drop non-photo messages with no text
              if (text.isBlank() && mediaType != MediaType.PHOTO) return@mapNotNull null
              Message(
                  id = msg.id,
                  channel = channel,
                  channelTitle = chat.title,
                  text = text,
                  timestamp = msg.date.toLong(),
                  mediaType = mediaType,
                  photoFileId = photoFileId,
              )
          } ?: emptyList()
      } catch (e: Exception) {
          emptyList()
      }
  }
  ```

- [ ] **Step 4: Fix `observeNewMessages` to include photos**

  Replace the inner `.collect` block in `observeNewMessages` (currently lines 129–145):
  ```kotlin
  api.getUpdatesFlowOfType<TdApi.UpdateNewMessage>()
      .filter { it.message.chatId in chatIdToUsername }
      .collect { update ->
          val msg = update.message
          val username = chatIdToUsername[msg.chatId] ?: return@collect
          val title = chatIdToTitle[msg.chatId] ?: username
          val text = extractText(msg.content, title)
          val mediaType = extractMediaType(msg.content)
          val photoFileId = extractPhotoFileId(msg.content)
          if (text.isNotBlank() || mediaType == MediaType.PHOTO) {
              send(
                  Message(
                      id = msg.id,
                      channel = username,
                      channelTitle = title,
                      text = text,
                      timestamp = msg.date.toLong(),
                      mediaType = mediaType,
                      photoFileId = photoFileId,
                  )
              )
          }
      }
  ```

- [ ] **Step 5: Build to verify**

  ```
  ./gradlew :app:assembleDebug
  ```
  Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/TelegramRepository.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TelegramRepositoryImpl.kt
  git commit -m "feat: extract photo file ID and media type from TDLib messages"
  ```

---

## Chunk 2: ViewModels, SyncWorker, Settings

### Task 3: FeedViewModel — write paths + SyncWorker

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/worker/SyncWorker.kt`

- [ ] **Step 1: Add MediaType import to FeedViewModel**

  Add at the top of `FeedViewModel.kt`:
  ```kotlin
  import com.heywood8.telegramnews.domain.model.MediaType
  ```

- [ ] **Step 2: Fix the `refresh()` write path**

  In `FeedViewModel.refresh()` (around line 119), replace:
  ```kotlin
  val filtered = messages.filter {
      filterUseCase.shouldForward(it.text, sub.mode, sub.keywords)
  }
  if (filtered.isNotEmpty()) {
      messageDao.insertAll(filtered.map { msg ->
          MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
      })
  ```
  with:
  ```kotlin
  val filtered = messages.filter { msg ->
      (msg.mediaType == MediaType.PHOTO && msg.text.isBlank()) ||
          filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
  }
  if (filtered.isNotEmpty()) {
      messageDao.insertAll(filtered.map { msg ->
          MessageEntity(
              id = msg.id,
              channel = msg.channel,
              channelTitle = msg.channelTitle,
              text = msg.text,
              timestamp = msg.timestamp,
              mediaType = msg.mediaType,
              photoFileId = msg.photoFileId,
          )
      })
  ```

- [ ] **Step 3: Fix the real-time collect write path**

  In the `init` block's real-time collect, replace:
  ```kotlin
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
  ```
  with:
  ```kotlin
  localRepo.observeSubscriptions(USER_ID)
      .flatMapLatest { subs ->
          val channels = subs.filter { it.active }.map { it.channel }
          telegramRepo.observeNewMessages(channels)
              .filter { msg ->
                  val sub = subs.find { it.channel == msg.channel }
                  sub != null && (
                      (msg.mediaType == MediaType.PHOTO && msg.text.isBlank()) ||
                          filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                  )
              }
      }
      .collect { msg ->
          messageDao.insertAll(listOf(
              MessageEntity(
                  id = msg.id,
                  channel = msg.channel,
                  channelTitle = msg.channelTitle,
                  text = msg.text,
                  timestamp = msg.timestamp,
                  mediaType = msg.mediaType,
                  photoFileId = msg.photoFileId,
              )
          ))
  ```

- [ ] **Step 4: Fix the on-load write path**

  In the `init` block's on-load section, replace:
  ```kotlin
  val filtered = messages.filter {
      filterUseCase.shouldForward(it.text, sub.mode, sub.keywords)
  }
  if (filtered.isNotEmpty()) {
      messageDao.insertAll(filtered.map { msg ->
          MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
      })
  ```
  with:
  ```kotlin
  val filtered = messages.filter { msg ->
      (msg.mediaType == MediaType.PHOTO && msg.text.isBlank()) ||
          filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
  }
  if (filtered.isNotEmpty()) {
      messageDao.insertAll(filtered.map { msg ->
          MessageEntity(
              id = msg.id,
              channel = msg.channel,
              channelTitle = msg.channelTitle,
              text = msg.text,
              timestamp = msg.timestamp,
              mediaType = msg.mediaType,
              photoFileId = msg.photoFileId,
          )
      })
  ```

- [ ] **Step 5: Fix the read path in `filteredMessages`**

  Replace line 61 in `FeedViewModel.kt`:
  ```kotlin
  // before:
  Message(e.id, e.channel, e.channelTitle.ifBlank { e.channel }, e.text, e.timestamp)
  // after:
  Message(
      id = e.id,
      channel = e.channel,
      channelTitle = e.channelTitle.ifBlank { e.channel },
      text = e.text,
      timestamp = e.timestamp,
      mediaType = e.mediaType,
      photoFileId = e.photoFileId,
  )
  ```

- [ ] **Step 6: Fix SyncWorker MessageEntity constructor**

  In `worker/SyncWorker.kt`, replace the `MessageEntity(...)` constructor call (lines 31–38):
  ```kotlin
  MessageEntity(
      id = msg.id,
      channel = msg.channel,
      channelTitle = msg.channelTitle,
      text = msg.text,
      timestamp = msg.timestamp,
      mediaType = msg.mediaType,
      photoFileId = msg.photoFileId,
  )
  ```

- [ ] **Step 7: Build and run unit tests**

  ```
  ./gradlew :app:assembleDebug
  ./gradlew :app:test
  ```
  Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/worker/SyncWorker.kt
  git commit -m "feat: propagate photoFileId/mediaType through FeedViewModel and SyncWorker write/read paths"
  ```

---

### Task 4: FeedViewModel — photoLayout + getPhotoPath

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt`

- [ ] **Step 1: Add PhotoLayout import and expose photoLayout + getPhotoPath**

  Add import:
  ```kotlin
  import com.heywood8.telegramnews.domain.model.PhotoLayout
  ```

  Add to `FeedViewModel` body, after `showChannelIcons`:
  ```kotlin
  val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout

  suspend fun getPhotoPath(fileId: Int): String? = telegramRepo.downloadFile(fileId)
  ```

  Note: `userPrefs` and `telegramRepo` are already injected — no new constructor parameters needed.

- [ ] **Step 2: Skip build — proceed directly to Task 5**

  Do NOT run the build here. `UserPreferencesRepository.photoLayout` doesn't exist yet (added in Task 5). Commit both Task 4 and Task 5 together at the end of Task 5 Step 6.

---

### Task 5: PhotoLayout enum + UserPreferencesRepository + SettingsViewModel + SettingsScreen

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/PhotoLayout.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/UserPreferencesRepository.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create PhotoLayout enum**

  Create `domain/model/PhotoLayout.kt`:
  ```kotlin
  package com.heywood8.telegramnews.domain.model

  enum class PhotoLayout { ABOVE, BELOW, LEFT }
  ```

- [ ] **Step 2: Add photoLayout to UserPreferencesRepository**

  In `data/local/UserPreferencesRepository.kt`, add `PhotoLayout` import and the preference:
  ```kotlin
  import com.heywood8.telegramnews.domain.model.PhotoLayout
  ```

  Add the constant **inside the existing `companion object { }` block**, after `KEY_SHOW_CHANNEL_ICONS`:
  ```kotlin
  companion object {
      private const val KEY_SHOW_CHANNEL_ICONS = "show_channel_icons"
      private const val KEY_PHOTO_LAYOUT = "photo_layout"  // add this line
  }
  ```

  Add the backing StateFlow and public accessor in the class body:
  ```kotlin
  private val _photoLayout = MutableStateFlow(
      PhotoLayout.valueOf(
          prefs.getString(KEY_PHOTO_LAYOUT, PhotoLayout.ABOVE.name) ?: PhotoLayout.ABOVE.name
      )
  )
  val photoLayout: StateFlow<PhotoLayout> = _photoLayout.asStateFlow()

  fun setPhotoLayout(layout: PhotoLayout) {
      prefs.edit().putString(KEY_PHOTO_LAYOUT, layout.name).apply()
      _photoLayout.value = layout
  }
  ```

- [ ] **Step 3: Expose photoLayout in SettingsViewModel**

  In `ui/settings/SettingsViewModel.kt`, add import and two members:
  ```kotlin
  import com.heywood8.telegramnews.domain.model.PhotoLayout
  ```

  ```kotlin
  val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout

  fun setPhotoLayout(layout: PhotoLayout) {
      userPrefs.setPhotoLayout(layout)
  }
  ```

- [ ] **Step 4: Add photo layout row to SettingsScreen**

  In `ui/settings/SettingsScreen.kt`, add these imports:
  ```kotlin
  import androidx.compose.material3.SegmentedButton
  import androidx.compose.material3.SegmentedButtonDefaults
  import androidx.compose.material3.SingleChoiceSegmentedButtonRow
  import com.heywood8.telegramnews.domain.model.PhotoLayout
  ```

  Collect the new state at the **top of the `SettingsScreen` composable body**, alongside the existing `val showChannelIcons by ...` line:
  ```kotlin
  val showChannelIcons by viewModel.showChannelIcons.collectAsStateWithLifecycle()
  val photoLayout by viewModel.photoLayout.collectAsStateWithLifecycle()  // add here
  ```

  Add a new `ListItem` in the Display section, after the channel icons toggle and before the `Divider()`:
  ```kotlin
  ListItem(
      headlineContent = { Text("Photo layout") },
      supportingContent = { Text("Where to show inline photo thumbnails") },
      modifier = Modifier.fillMaxWidth(),
      trailingContent = {
          SingleChoiceSegmentedButtonRow {
              PhotoLayout.entries.forEachIndexed { index, layout ->
                  SegmentedButton(
                      selected = photoLayout == layout,
                      onClick = { viewModel.setPhotoLayout(layout) },
                      shape = SegmentedButtonDefaults.itemShape(
                          index = index,
                          count = PhotoLayout.entries.size,
                      ),
                      label = {
                          Text(
                              when (layout) {
                                  PhotoLayout.ABOVE -> "Above"
                                  PhotoLayout.BELOW -> "Below"
                                  PhotoLayout.LEFT -> "Left"
                              }
                          )
                      },
                  )
              }
          }
      },
  )
  ```

- [ ] **Step 5: Build and run unit tests**

  ```
  ./gradlew :app:assembleDebug
  ./gradlew :app:test
  ```
  Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 6: Commit Tasks 4 + 5 together**

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/PhotoLayout.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/UserPreferencesRepository.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsViewModel.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsScreen.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt
  git commit -m "feat: photo layout preference and settings UI"
  ```

---

## Chunk 3: UI — Coil, FeedScreen, ArticleSheet

### Task 6: Coil dependency + FeedScreen photo rendering

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedScreen.kt`

- [ ] **Step 1: Add Coil 3 dependency**

  In `android/app/build.gradle.kts`, add inside the `dependencies { }` block:
  ```kotlin
  implementation("io.coil-kt.coil3:coil-compose:3.1.0")
  implementation("io.coil-kt.coil3:coil-android:3.1.0")
  ```

- [ ] **Step 2: Sync gradle and verify build**

  ```
  ./gradlew :app:assembleDebug
  ```
  Expected: BUILD SUCCESSFUL (Coil downloaded and on classpath)

- [ ] **Step 3: Add photo rendering to `FeedItem`**

  In `ui/feed/FeedScreen.kt`, add imports:
  ```kotlin
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.size
  import androidx.compose.runtime.produceState
  import androidx.compose.ui.layout.ContentScale
  import coil3.compose.AsyncImage
  import com.heywood8.telegramnews.domain.model.PhotoLayout
  import java.io.File
  ```

  Update `FeedItem` signature to add two parameters:
  ```kotlin
  @Composable
  private fun FeedItem(
      message: Message,
      showChannelIcons: Boolean,
      photoLayout: PhotoLayout,
      getPhotoPath: suspend (Int) -> String?,
      onClick: () -> Unit,
  )
  ```

  Inside `FeedItem`, after the opening `ElevatedCard { Column { ... } }` but before the header `Row`, add:

  ```kotlin
  // Resolve photo path on-demand
  val photoPath by produceState<String?>(null, message.photoFileId) {
      value = message.photoFileId?.let { getPhotoPath(it) }
  }
  ```

  Then replace the existing `Column(modifier = Modifier.padding(16.dp)) { ... }` body with layout-aware rendering. The current body is:
  ```kotlin
  Column(modifier = Modifier.padding(16.dp)) {
      Row(/* header row */) { ... }
      Text(text = message.text, ...)
  }
  ```

  Replace with:
  ```kotlin
  Column(modifier = Modifier.padding(16.dp)) {
      Row(/* keep existing header row exactly as-is */) { ... }

      when {
          photoPath != null && photoLayout == PhotoLayout.ABOVE -> {
              AsyncImage(
                  model = File(photoPath!!),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(200.dp)
                      .padding(top = 8.dp),
              )
              Text(
                  text = message.text,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(top = 8.dp),
                  maxLines = 6,
                  overflow = TextOverflow.Ellipsis,
              )
          }
          photoPath != null && photoLayout == PhotoLayout.BELOW -> {
              Text(
                  text = message.text,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(top = 8.dp),
                  maxLines = 6,
                  overflow = TextOverflow.Ellipsis,
              )
              AsyncImage(
                  model = File(photoPath!!),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(200.dp)
                      .padding(top = 8.dp),
              )
          }
          photoPath != null && photoLayout == PhotoLayout.LEFT -> {
              Row(
                  modifier = Modifier.padding(top = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                  AsyncImage(
                      model = File(photoPath!!),
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.size(80.dp),
                  )
                  Column {
                      Text(
                          text = message.text,
                          style = MaterialTheme.typography.bodyMedium,
                          maxLines = 6,
                          overflow = TextOverflow.Ellipsis,
                      )
                  }
              }
          }
          else -> {
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
  ```


- [ ] **Step 4: Wire photoLayout and getPhotoPath in FeedScreen**

  In the `FeedScreen` composable, add:
  ```kotlin
  val photoLayout by viewModel.photoLayout.collectAsStateWithLifecycle()
  val getPhotoPath: suspend (Int) -> String? = { viewModel.getPhotoPath(it) }
  ```

  Update **both** `FeedItem` call sites to pass the new parameters:
  ```kotlin
  // unread list:
  FeedItem(
      message = message,
      showChannelIcons = showChannelIcons,
      photoLayout = photoLayout,
      getPhotoPath = getPhotoPath,
      onClick = { selectedMessage = message },
  )
  // read list (identical parameter set):
  FeedItem(
      message = message,
      showChannelIcons = showChannelIcons,
      photoLayout = photoLayout,
      getPhotoPath = getPhotoPath,
      onClick = { selectedMessage = message },
  )
  ```

- [ ] **Step 5: Add photo to ArticleSheet**

  Update `ArticleSheet` signature:
  ```kotlin
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun ArticleSheet(
      message: Message,
      getPhotoPath: suspend (Int) -> String?,
      onDismiss: () -> Unit,
  )
  ```

  Inside `ArticleSheet`, after the channel title and timestamp `Text` calls, add:
  ```kotlin
  val photoPath by produceState<String?>(null, message.photoFileId) {
      value = message.photoFileId?.let { getPhotoPath(it) }
  }
  if (photoPath != null) {
      AsyncImage(
          model = File(photoPath!!),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier
              .fillMaxWidth()
              .height(300.dp)
              .padding(top = 12.dp),
      )
  }
  ```

  Update the `ArticleSheet` call site in `FeedScreen`:
  ```kotlin
  ArticleSheet(
      message = selectedMessage!!,
      getPhotoPath = getPhotoPath,
      onDismiss = { selectedMessage = null },
  )
  ```

- [ ] **Step 6: Build and run unit tests**

  ```
  ./gradlew :app:assembleDebug
  ./gradlew :app:test
  ```
  Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 7: Commit**

  ```
  git add android/app/build.gradle.kts
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedScreen.kt
  git commit -m "feat: render inline photo thumbnails and full photo in ArticleSheet using Coil"
  ```
