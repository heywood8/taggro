package com.heywood8.telegramnews.data.local

import com.heywood8.telegramnews.data.local.dao.KeywordDao
import com.heywood8.telegramnews.data.local.dao.LastSeenDao
import com.heywood8.telegramnews.data.local.dao.MessageDao
import com.heywood8.telegramnews.data.local.dao.SubscriptionDao
import com.heywood8.telegramnews.data.local.entity.KeywordEntity
import com.heywood8.telegramnews.data.local.entity.LastSeenEntity
import com.heywood8.telegramnews.data.local.entity.MessageEntity
import com.heywood8.telegramnews.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocalRepositoryImplTest {

    private val subscriptionState = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    private val keywordState = MutableStateFlow<List<KeywordEntity>>(emptyList())
    private val lastSeenData = mutableMapOf<String, Long>()

    private val fakeSubscriptionDao = object : SubscriptionDao {
        override fun observeByUser(userId: Long): Flow<List<SubscriptionEntity>> = subscriptionState
        override suspend fun getByUser(userId: Long): List<SubscriptionEntity> = subscriptionState.value
        override suspend fun getActiveChannels(): List<String> =
            subscriptionState.value.filter { it.active }.map { it.channel }
        override suspend fun insert(sub: SubscriptionEntity) {
            subscriptionState.value = subscriptionState.value + sub
        }
        override suspend fun delete(userId: Long, channel: String) {
            subscriptionState.value = subscriptionState.value.filter {
                !(it.userId == userId && it.channel == channel)
            }
        }
        override suspend fun setActiveForUser(userId: Long, active: Boolean) {
            subscriptionState.value = subscriptionState.value.map {
                if (it.userId == userId) it.copy(active = active) else it
            }
        }
        override suspend fun setMode(userId: Long, channel: String, mode: String) {
            subscriptionState.value = subscriptionState.value.map {
                if (it.userId == userId && it.channel == channel) it.copy(mode = mode) else it
            }
        }
        override suspend fun count(userId: Long, channel: String): Int =
            subscriptionState.value.count { it.userId == userId && it.channel == channel }
        override suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean) {
            subscriptionState.value = subscriptionState.value.map {
                if (it.userId == userId && it.channel == channel) it.copy(includePhotos = value) else it
            }
        }
    }

    private val fakeKeywordDao = object : KeywordDao {
        override suspend fun getKeywords(userId: Long, channel: String): List<String> =
            keywordState.value.filter { it.userId == userId && it.channel == channel }.map { it.keyword }
        override fun observeByUser(userId: Long): Flow<List<KeywordEntity>> = keywordState
        override suspend fun insert(keyword: KeywordEntity) {
            keywordState.value = keywordState.value + keyword
        }
        override suspend fun delete(userId: Long, channel: String, keyword: String) {
            keywordState.value = keywordState.value.filter {
                !(it.userId == userId && it.channel == channel && it.keyword == keyword)
            }
        }
        override suspend fun deleteAll(userId: Long, channel: String) {
            keywordState.value = keywordState.value.filter {
                !(it.userId == userId && it.channel == channel)
            }
        }
        override suspend fun count(userId: Long, channel: String): Int =
            keywordState.value.count { it.userId == userId && it.channel == channel }
    }

    private val fakeMessageDao = object : MessageDao {
        override fun observeAll(): Flow<List<MessageEntity>> = MutableStateFlow(emptyList())
        override fun observeByChannel(channel: String): Flow<List<MessageEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertAll(messages: List<MessageEntity>) = Unit
        override suspend fun pruneChannel(channel: String) = Unit
    }

    private val fakeLastSeenDao = object : LastSeenDao {
        override suspend fun getLastSeen(channel: String): Long? = lastSeenData[channel]
        override suspend fun upsert(lastSeen: LastSeenEntity) {
            lastSeenData[lastSeen.channel] = lastSeen.messageId
        }
    }

    private lateinit var repo: LocalRepositoryImpl

    @Before
    fun setUp() {
        repo = LocalRepositoryImpl(fakeSubscriptionDao, fakeKeywordDao, fakeMessageDao, fakeLastSeenDao)
    }

    @Test
    fun `observeSubscriptions emits empty list when no subscriptions`() = runTest {
        val result = repo.observeSubscriptions(1L).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `observeSubscriptions maps entities to domain Subscription`() = runTest {
        subscriptionState.value = listOf(SubscriptionEntity(userId = 1L, channel = "chan", mode = "all", active = true))
        val result = repo.observeSubscriptions(1L).first()
        assertEquals(1, result.size)
        assertEquals("chan", result[0].channel)
        assertEquals("all", result[0].mode)
        assertTrue(result[0].active)
        assertTrue(result[0].keywords.isEmpty())
    }

    @Test
    fun `observeSubscriptions attaches keywords to their subscription`() = runTest {
        subscriptionState.value = listOf(SubscriptionEntity(userId = 1L, channel = "chan"))
        keywordState.value = listOf(
            KeywordEntity(userId = 1L, channel = "chan", keyword = "crypto"),
            KeywordEntity(userId = 1L, channel = "chan", keyword = "bitcoin"),
        )
        val result = repo.observeSubscriptions(1L).first()
        assertEquals(1, result.size)
        assertTrue(result[0].keywords.containsAll(listOf("crypto", "bitcoin")))
    }

    @Test
    fun `observeSubscriptions does not attach keywords from other channels`() = runTest {
        subscriptionState.value = listOf(
            SubscriptionEntity(userId = 1L, channel = "chan1"),
            SubscriptionEntity(userId = 1L, channel = "chan2"),
        )
        keywordState.value = listOf(KeywordEntity(userId = 1L, channel = "chan1", keyword = "news"))
        val result = repo.observeSubscriptions(1L).first()
        val chan2 = result.find { it.channel == "chan2" }!!
        assertTrue(chan2.keywords.isEmpty())
    }

    @Test
    fun `observeSubscriptions maps includePhotos field`() = runTest {
        subscriptionState.value = listOf(
            SubscriptionEntity(userId = 1L, channel = "chan", includePhotos = true)
        )
        val result = repo.observeSubscriptions(1L).first()
        assertTrue(result[0].includePhotos)
    }

    @Test
    fun `addSubscription inserts subscription and initializes lastSeen`() = runTest {
        repo.addSubscription(userId = 1L, channel = "testchan")
        assertEquals(1, subscriptionState.value.size)
        assertEquals("testchan", subscriptionState.value[0].channel)
        assertEquals(0L, lastSeenData["testchan"])
    }

    @Test
    fun `removeSubscription deletes subscription and its keywords`() = runTest {
        subscriptionState.value = listOf(SubscriptionEntity(userId = 1L, channel = "chan"))
        keywordState.value = listOf(KeywordEntity(userId = 1L, channel = "chan", keyword = "kw"))
        repo.removeSubscription(userId = 1L, channel = "chan")
        assertTrue(subscriptionState.value.isEmpty())
        assertTrue(keywordState.value.isEmpty())
    }

    @Test
    fun `setMode updates mode in subscriptionDao`() = runTest {
        subscriptionState.value = listOf(SubscriptionEntity(userId = 1L, channel = "chan", mode = "all"))
        repo.setMode(userId = 1L, channel = "chan", mode = "include")
        assertEquals("include", subscriptionState.value[0].mode)
    }

    @Test
    fun `addKeyword inserts into keywordDao`() = runTest {
        repo.addKeyword(userId = 1L, channel = "chan", keyword = "test")
        assertEquals(1, keywordState.value.size)
        assertEquals("test", keywordState.value[0].keyword)
    }

    @Test
    fun `removeKeyword deletes from keywordDao`() = runTest {
        keywordState.value = listOf(KeywordEntity(userId = 1L, channel = "chan", keyword = "test"))
        repo.removeKeyword(userId = 1L, channel = "chan", keyword = "test")
        assertTrue(keywordState.value.isEmpty())
    }

    @Test
    fun `getLastSeen returns 0 when not found`() = runTest {
        assertEquals(0L, repo.getLastSeen("unknown"))
    }

    @Test
    fun `updateLastSeen stores and retrieves value`() = runTest {
        repo.updateLastSeen("chan", 42L)
        assertEquals(42L, repo.getLastSeen("chan"))
    }

    @Test
    fun `setIncludePhotos updates subscription in dao`() = runTest {
        subscriptionState.value = listOf(SubscriptionEntity(userId = 1L, channel = "chan", includePhotos = false))
        repo.setIncludePhotos(userId = 1L, channel = "chan", value = true)
        assertTrue(subscriptionState.value[0].includePhotos)
    }
}
