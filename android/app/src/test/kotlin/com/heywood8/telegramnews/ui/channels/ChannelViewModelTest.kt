package com.heywood8.telegramnews.ui.channels

import com.heywood8.telegramnews.FakeSharedPreferences
import com.heywood8.telegramnews.data.local.UserPreferencesRepository
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import com.heywood8.telegramnews.domain.usecase.FilterUseCase
import com.heywood8.telegramnews.domain.usecase.SubscriptionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
class ChannelViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val subscriptionState = MutableStateFlow<List<Subscription>>(emptyList())
    private var searchResult: List<Channel> = emptyList()
    private var searchThrows: Exception? = null

    private val fakeLocalRepo = object : LocalRepository {
        val subs = mutableMapOf<String, Subscription>()
        val keywords = mutableMapOf<String, MutableList<String>>()
        var includePhotosValues = mutableMapOf<String, Boolean>()

        override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> = subscriptionState
        override suspend fun addSubscription(userId: Long, channel: String) {
            val sub = Subscription(channel, "all", emptyList(), true)
            subs[channel] = sub
            subscriptionState.value = subs.values.toList()
        }
        override suspend fun removeSubscription(userId: Long, channel: String) {
            subs.remove(channel)
            keywords.remove(channel)
            subscriptionState.value = subs.values.toList()
        }
        override suspend fun setMode(userId: Long, channel: String, mode: String) {
            subs[channel] = subs[channel]!!.copy(mode = mode)
            subscriptionState.value = subs.values.toList()
        }
        override suspend fun addKeyword(userId: Long, channel: String, keyword: String) {
            keywords.getOrPut(channel) { mutableListOf() }.add(keyword)
        }
        override suspend fun removeKeyword(userId: Long, channel: String, keyword: String) {
            keywords[channel]?.remove(keyword)
        }
        override suspend fun getKeywords(userId: Long, channel: String): List<String> =
            keywords[channel] ?: emptyList()
        override suspend fun getActiveChannels(): List<String> =
            subs.values.filter { it.active }.map { it.channel }
        override suspend fun getLastSeen(channel: String): Long = 0L
        override suspend fun updateLastSeen(channel: String, messageId: Long) = Unit
        override suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean) {
            includePhotosValues[channel] = value
        }
    }

    private val fakeTelegramRepo = object : TelegramRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override fun observeNewMessages(channels: List<String>): Flow<Message> = emptyFlow()
        override suspend fun fetchMessagesSince(channel: String, afterMessageId: Long): List<Message> = emptyList()
        override suspend fun searchChannel(query: String): List<Channel> {
            searchThrows?.let { throw it }
            return searchResult
        }
        override suspend fun downloadFile(fileId: Int): String? = null
    }

    private lateinit var viewModel: ChannelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChannelViewModel(
            localRepo = fakeLocalRepo,
            telegramRepo = fakeTelegramRepo,
            subscriptionUseCase = SubscriptionUseCase(fakeLocalRepo, FilterUseCase()),
            userPrefs = UserPreferencesRepository(FakeSharedPreferences()),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search with blank query clears results without network call`() = runTest {
        searchResult = listOf(Channel("foo", "Foo", 100))
        viewModel.search("   ")
        advanceUntilIdle()
        assertTrue(viewModel.searchResults.value.isEmpty())
        assertFalse(viewModel.searching.value)
    }

    @Test
    fun `search returns results from telegramRepo`() = runTest {
        searchResult = listOf(Channel("testchan", "Test Chan", 500))
        viewModel.search("test")
        advanceUntilIdle()
        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals("testchan", viewModel.searchResults.value[0].username)
        assertFalse(viewModel.searching.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `search sets error when telegramRepo throws`() = runTest {
        searchThrows = RuntimeException("Network error")
        viewModel.search("query")
        advanceUntilIdle()
        assertEquals("Network error", viewModel.error.value)
        assertFalse(viewModel.searching.value)
    }

    @Test
    fun `search clears previous error before new search`() = runTest {
        searchThrows = RuntimeException("First error")
        viewModel.search("query")
        advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        searchThrows = null
        searchResult = listOf(Channel("chan", "Chan", 10))
        viewModel.search("query2")
        advanceUntilIdle()
        assertNull(viewModel.error.value)
    }

    @Test
    fun `subscribe calls addSubscription and clears searchResults`() = runTest {
        searchResult = listOf(Channel("chan", "Chan", 100))
        viewModel.search("chan")
        advanceUntilIdle()
        assertEquals(1, viewModel.searchResults.value.size)

        viewModel.subscribe("chan")
        advanceUntilIdle()
        assertTrue(viewModel.searchResults.value.isEmpty())
        assertTrue(fakeLocalRepo.subs.containsKey("chan"))
    }

    @Test
    fun `unsubscribe removes subscription and clears selectedChannel`() = runTest {
        fakeLocalRepo.subs["chan"] = Subscription("chan", "all", emptyList(), true)
        subscriptionState.value = fakeLocalRepo.subs.values.toList()
        viewModel.selectSubscription(Subscription("chan", "all", emptyList(), true))
        advanceUntilIdle()
        assertNotNull(viewModel.selectedSub.value)

        viewModel.unsubscribe("chan")
        advanceUntilIdle()
        assertFalse(fakeLocalRepo.subs.containsKey("chan"))
        assertNull(viewModel.selectedSub.value)
    }

    @Test
    fun `selectedSub derives from subscriptions and selectedChannel`() = runTest {
        val sub = Subscription("chan", "all", emptyList(), true)
        subscriptionState.value = listOf(sub)
        advanceUntilIdle()
        assertNull(viewModel.selectedSub.value)

        viewModel.selectSubscription(sub)
        advanceUntilIdle()
        assertEquals("chan", viewModel.selectedSub.value?.channel)
    }

    @Test
    fun `selectedSub updates live when subscription changes`() = runTest {
        val sub = Subscription("chan", "all", emptyList(), true)
        subscriptionState.value = listOf(sub)
        viewModel.selectSubscription(sub)
        advanceUntilIdle()
        assertEquals("all", viewModel.selectedSub.value?.mode)

        // Mutate subscription state (e.g. keyword added from another path)
        subscriptionState.value = listOf(sub.copy(mode = "include", keywords = listOf("kw")))
        advanceUntilIdle()
        assertEquals("include", viewModel.selectedSub.value?.mode)
        assertEquals(listOf("kw"), viewModel.selectedSub.value?.keywords)
    }

    @Test
    fun `selectedSub is null when selectSubscription called with null`() = runTest {
        val sub = Subscription("chan", "all", emptyList(), true)
        subscriptionState.value = listOf(sub)
        viewModel.selectSubscription(sub)
        advanceUntilIdle()
        assertNotNull(viewModel.selectedSub.value)

        viewModel.selectSubscription(null)
        advanceUntilIdle()
        assertNull(viewModel.selectedSub.value)
    }

    @Test
    fun `clearError sets error to null`() = runTest {
        searchThrows = RuntimeException("err")
        viewModel.search("q")
        advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        viewModel.clearError()
        assertNull(viewModel.error.value)
    }

    @Test
    fun `setMode delegates to subscriptionUseCase`() = runTest {
        fakeLocalRepo.subs["chan"] = Subscription("chan", "all", listOf("kw"), true)
        fakeLocalRepo.keywords["chan"] = mutableListOf("kw")
        viewModel.setMode("chan", "include")
        advanceUntilIdle()
        assertEquals("include", fakeLocalRepo.subs["chan"]?.mode)
    }

    @Test
    fun `setIncludePhotos delegates to localRepo`() = runTest {
        viewModel.setIncludePhotos("chan", true)
        advanceUntilIdle()
        assertTrue(fakeLocalRepo.includePhotosValues["chan"] == true)
    }

    @Test
    fun `addKeyword delegates to subscriptionUseCase`() = runTest {
        fakeLocalRepo.subs["chan"] = Subscription("chan", "all", emptyList(), true)
        viewModel.addKeyword("chan", "news")
        advanceUntilIdle()
        assertTrue(fakeLocalRepo.keywords["chan"]?.contains("news") == true)
    }

    @Test
    fun `removeKeyword delegates to subscriptionUseCase`() = runTest {
        fakeLocalRepo.subs["chan"] = Subscription("chan", "include", listOf("news"), true)
        fakeLocalRepo.keywords["chan"] = mutableListOf("news")
        viewModel.removeKeyword("chan", "news")
        advanceUntilIdle()
        assertTrue(fakeLocalRepo.keywords["chan"]?.isEmpty() == true)
    }
}
