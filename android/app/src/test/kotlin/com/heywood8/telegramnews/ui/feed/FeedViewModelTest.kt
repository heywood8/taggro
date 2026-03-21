package com.heywood8.telegramnews.ui.feed

import com.heywood8.telegramnews.FakeSharedPreferences
import com.heywood8.telegramnews.data.local.UserPreferencesRepository
import com.heywood8.telegramnews.data.local.dao.MessageDao
import com.heywood8.telegramnews.data.local.dao.ReadMessageDao
import com.heywood8.telegramnews.data.local.entity.MessageEntity
import com.heywood8.telegramnews.data.local.entity.ReadMessageEntity
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.MediaType
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import com.heywood8.telegramnews.domain.usecase.FilterUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val messageState = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val readIdsState = MutableStateFlow<List<Long>>(emptyList())
    private val subscriptionState = MutableStateFlow<List<Subscription>>(emptyList())

    private val fakeMessageDao = object : MessageDao {
        override fun observeAll(): Flow<List<MessageEntity>> = messageState
        override fun observeByChannel(channel: String): Flow<List<MessageEntity>> =
            flowOf(messageState.value.filter { it.channel == channel })
        override suspend fun insertAll(messages: List<MessageEntity>) {
            val existing = messageState.value.associateBy { it.id }.toMutableMap()
            messages.forEach { existing[it.id] = it }
            messageState.value = existing.values.toList()
        }
        override suspend fun pruneChannel(channel: String) = Unit
    }

    private val fakeReadMessageDao = object : ReadMessageDao {
        override suspend fun markRead(entry: ReadMessageEntity) = Unit
        override suspend fun markReadUpTo(timestamp: Long) {
            val toMark = messageState.value.filter { it.timestamp <= timestamp }.map { it.id }
            readIdsState.value = (readIdsState.value + toMark).distinct()
        }
        override suspend fun markChannelRead(channel: String) {
            val toMark = messageState.value.filter { it.channel == channel }.map { it.id }
            readIdsState.value = (readIdsState.value + toMark).distinct()
        }
        override suspend fun markAllRead() {
            readIdsState.value = messageState.value.map { it.id }
        }
        override fun observeReadIds(): Flow<List<Long>> = readIdsState
        override fun observeUnreadCount(channel: String): Flow<Int> = flowOf(0)
        override fun observeTotalUnreadCount(): Flow<Int> = flowOf(0)
    }

    private val fakeLocalRepo = object : LocalRepository {
        override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> = subscriptionState
        override suspend fun addSubscription(userId: Long, channel: String) = Unit
        override suspend fun removeSubscription(userId: Long, channel: String) = Unit
        override suspend fun setMode(userId: Long, channel: String, mode: String) = Unit
        override suspend fun addKeyword(userId: Long, channel: String, keyword: String) = Unit
        override suspend fun removeKeyword(userId: Long, channel: String, keyword: String) = Unit
        override suspend fun getKeywords(userId: Long, channel: String): List<String> = emptyList()
        override suspend fun getActiveChannels(): List<String> = emptyList()
        override suspend fun getLastSeen(channel: String): Long = 0L
        override suspend fun updateLastSeen(channel: String, messageId: Long) = Unit
        override suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean) = Unit
    }

    private val fakeTelegramRepo = object : TelegramRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override fun observeNewMessages(channels: List<String>): Flow<Message> = emptyFlow()
        override suspend fun fetchMessagesSince(channel: String, afterMessageId: Long): List<Message> = emptyList()
        override suspend fun searchChannel(query: String): List<Channel> = emptyList()
        override suspend fun downloadFile(fileId: Int): String? = null
    }

    private lateinit var viewModel: FeedViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FeedViewModel(
            localRepo = fakeLocalRepo,
            telegramRepo = fakeTelegramRepo,
            filterUseCase = FilterUseCase(),
            messageDao = fakeMessageDao,
            readMessageDao = fakeReadMessageDao,
            userPrefs = UserPreferencesRepository(FakeSharedPreferences()),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filteredMessages is empty initially`() = runTest {
        advanceUntilIdle()
        assertTrue(viewModel.filteredMessages.value.isEmpty())
    }

    @Test
    fun `filteredMessages shows text messages without grouping`() = runTest {
        messageState.value = listOf(
            MessageEntity(2L, "chan", "Chan", "World", 200L),
            MessageEntity(1L, "chan", "Chan", "Hello", 100L),
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.filteredMessages.value.size)
    }

    @Test
    fun `filteredMessages groups album photos into single entry`() = runTest {
        subscriptionState.value = listOf(Subscription("chan", "all", emptyList(), true, includePhotos = true))
        messageState.value = listOf(
            MessageEntity(1L, "chan", "Chan", "", 100L, mediaType = MediaType.PHOTO, photoFileId = 10, mediaAlbumId = 99L),
            MessageEntity(2L, "chan", "Chan", "", 200L, mediaType = MediaType.PHOTO, photoFileId = 11, mediaAlbumId = 99L),
        )
        advanceUntilIdle()
        val result = viewModel.filteredMessages.value
        assertEquals(1, result.size)
        assertEquals(2, result[0].photoFileIds.size)
        assertTrue(result[0].photoFileIds.containsAll(listOf(10, 11)))
    }

    @Test
    fun `filteredMessages keeps separate entries for different albums`() = runTest {
        subscriptionState.value = listOf(Subscription("chan", "all", emptyList(), true, includePhotos = true))
        messageState.value = listOf(
            MessageEntity(1L, "chan", "Chan", "", 100L, mediaType = MediaType.PHOTO, photoFileId = 10, mediaAlbumId = 1L),
            MessageEntity(2L, "chan", "Chan", "", 200L, mediaType = MediaType.PHOTO, photoFileId = 11, mediaAlbumId = 2L),
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.filteredMessages.value.size)
    }

    @Test
    fun `filteredMessages merges album caption from second photo when first has none`() = runTest {
        subscriptionState.value = listOf(Subscription("chan", "all", emptyList(), true, includePhotos = true))
        // Ordered descending by timestamp: photo2 first (t=200), photo1 second (t=100)
        messageState.value = listOf(
            MessageEntity(2L, "chan", "Chan", "Caption", 200L, mediaType = MediaType.PHOTO, photoFileId = 11, mediaAlbumId = 5L),
            MessageEntity(1L, "chan", "Chan", "", 100L, mediaType = MediaType.PHOTO, photoFileId = 10, mediaAlbumId = 5L),
        )
        advanceUntilIdle()
        val result = viewModel.filteredMessages.value
        assertEquals(1, result.size)
        assertEquals("Caption", result[0].text)
    }

    @Test
    fun `filteredMessages does not override existing caption when merging album`() = runTest {
        subscriptionState.value = listOf(Subscription("chan", "all", emptyList(), true, includePhotos = true))
        messageState.value = listOf(
            MessageEntity(2L, "chan", "Chan", "First caption", 200L, mediaType = MediaType.PHOTO, photoFileId = 11, mediaAlbumId = 5L),
            MessageEntity(1L, "chan", "Chan", "Second caption", 100L, mediaType = MediaType.PHOTO, photoFileId = 10, mediaAlbumId = 5L),
        )
        advanceUntilIdle()
        // First-processed message (higher timestamp = first in desc order) wins
        assertEquals("First caption", viewModel.filteredMessages.value[0].text)
    }

    @Test
    fun `filteredMessages non-album photo gets single-element photoFileIds list`() = runTest {
        subscriptionState.value = listOf(Subscription("chan", "all", emptyList(), true, includePhotos = true))
        messageState.value = listOf(
            MessageEntity(1L, "chan", "Chan", "", 100L, mediaType = MediaType.PHOTO, photoFileId = 42)
        )
        advanceUntilIdle()
        val result = viewModel.filteredMessages.value
        assertEquals(1, result.size)
        assertEquals(listOf(42), result[0].photoFileIds)
    }

    @Test
    fun `filteredMessages filters by selected channel`() = runTest {
        messageState.value = listOf(
            MessageEntity(1L, "chan1", "Chan1", "Hello", 100L),
            MessageEntity(2L, "chan2", "Chan2", "World", 200L),
        )
        viewModel.selectChannel("chan1")
        advanceUntilIdle()
        val result = viewModel.filteredMessages.value
        assertEquals(1, result.size)
        assertEquals("chan1", result[0].channel)
    }

    @Test
    fun `filteredMessages shows all channels when none selected`() = runTest {
        messageState.value = listOf(
            MessageEntity(1L, "chan1", "Chan1", "Hello", 100L),
            MessageEntity(2L, "chan2", "Chan2", "World", 200L),
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.filteredMessages.value.size)
    }

    @Test
    fun `filteredMessages applies read state from readIds`() = runTest {
        messageState.value = listOf(MessageEntity(42L, "chan", "Chan", "Text", 100L))
        readIdsState.value = listOf(42L)
        advanceUntilIdle()
        assertTrue(viewModel.filteredMessages.value[0].isRead)
    }

    @Test
    fun `filteredMessages unread message has isRead false`() = runTest {
        messageState.value = listOf(MessageEntity(42L, "chan", "Chan", "Text", 100L))
        readIdsState.value = emptyList()
        advanceUntilIdle()
        assertFalse(viewModel.filteredMessages.value[0].isRead)
    }

    @Test
    fun `filteredMessages excludes photo-only messages when includePhotos is false`() = runTest {
        subscriptionState.value = listOf(Subscription("chan", "all", emptyList(), true, includePhotos = false))
        messageState.value = listOf(
            MessageEntity(1L, "chan", "Chan", "", 100L, mediaType = MediaType.PHOTO, photoFileId = 10)
        )
        advanceUntilIdle()
        assertTrue(viewModel.filteredMessages.value.isEmpty())
    }

    @Test
    fun `filteredMessages shows photo with caption even when includePhotos is false`() = runTest {
        subscriptionState.value = listOf(Subscription("chan", "all", emptyList(), true, includePhotos = false))
        messageState.value = listOf(
            MessageEntity(1L, "chan", "Chan", "Some caption", 100L, mediaType = MediaType.PHOTO, photoFileId = 10)
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.filteredMessages.value.size)
    }

    @Test
    fun `filteredMessages shows photo when includePhotos is true`() = runTest {
        subscriptionState.value = listOf(Subscription("chan", "all", emptyList(), true, includePhotos = true))
        messageState.value = listOf(
            MessageEntity(1L, "chan", "Chan", "", 100L, mediaType = MediaType.PHOTO, photoFileId = 10)
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.filteredMessages.value.size)
    }

    @Test
    fun `filteredMessages shows message from unsubscribed channel without photo filtering`() = runTest {
        // No subscription entry for this channel — falls back to includePhotos=false
        subscriptionState.value = emptyList()
        messageState.value = listOf(
            MessageEntity(1L, "chan", "Chan", "Text only", 100L)
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.filteredMessages.value.size)
    }

    @Test
    fun `filteredMessages channelTitle falls back to channel name when blank`() = runTest {
        messageState.value = listOf(MessageEntity(1L, "chan", "", "Text", 100L))
        advanceUntilIdle()
        assertEquals("chan", viewModel.filteredMessages.value[0].channelTitle)
    }

    @Test
    fun `markRead marks messages up to that message timestamp as read`() = runTest {
        messageState.value = listOf(
            MessageEntity(7L, "chan", "Chan", "Text", 500L),
            MessageEntity(8L, "chan", "Chan", "Later", 600L),
        )
        advanceUntilIdle()
        viewModel.markRead(7L)
        advanceUntilIdle()
        assertTrue(readIdsState.value.contains(7L))
        // Message with timestamp 600 should NOT be marked (500 <= 500 only marks id=7)
        assertFalse(readIdsState.value.contains(8L))
    }

    @Test
    fun `markRead does nothing for unknown message id`() = runTest {
        viewModel.markRead(999L)
        advanceUntilIdle()
        assertTrue(readIdsState.value.isEmpty())
    }

    @Test
    fun `markChannelRead marks all messages in channel`() = runTest {
        messageState.value = listOf(
            MessageEntity(1L, "chan", "Chan", "A", 100L),
            MessageEntity(2L, "chan", "Chan", "B", 200L),
            MessageEntity(3L, "other", "Other", "C", 300L),
        )
        advanceUntilIdle()
        viewModel.markChannelRead("chan")
        advanceUntilIdle()
        assertTrue(readIdsState.value.containsAll(listOf(1L, 2L)))
        assertFalse(readIdsState.value.contains(3L))
    }

    @Test
    fun `markAllRead marks all messages as read`() = runTest {
        messageState.value = listOf(
            MessageEntity(1L, "chan1", "Chan1", "A", 100L),
            MessageEntity(2L, "chan2", "Chan2", "B", 200L),
        )
        advanceUntilIdle()
        viewModel.markAllRead()
        advanceUntilIdle()
        assertTrue(readIdsState.value.containsAll(listOf(1L, 2L)))
    }

    @Test
    fun `selectChannel updates selectedChannel state`() = runTest {
        viewModel.selectChannel("mychannel")
        assertEquals("mychannel", viewModel.selectedChannel.value)
        viewModel.selectChannel(null)
        assertNull(viewModel.selectedChannel.value)
    }
}
