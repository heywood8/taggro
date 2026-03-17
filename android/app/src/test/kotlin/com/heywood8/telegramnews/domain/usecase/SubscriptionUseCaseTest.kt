package com.heywood8.telegramnews.domain.usecase

import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SubscriptionUseCaseTest {

    private val fakeRepo = object : LocalRepository {
        val subs = mutableMapOf<String, Subscription>()
        val keywords = mutableMapOf<String, MutableList<String>>()

        override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> =
            flowOf(subs.values.toList())

        override suspend fun addSubscription(userId: Long, channel: String) {
            subs[channel] = Subscription(channel, "all", emptyList(), true)
        }

        override suspend fun removeSubscription(userId: Long, channel: String) {
            subs.remove(channel)
            keywords.remove(channel)
        }

        override suspend fun setMode(userId: Long, channel: String, mode: String) {
            subs[channel] = subs[channel]!!.copy(mode = mode)
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
        override suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean) = Unit
    }

    private val useCase = SubscriptionUseCase(fakeRepo, FilterUseCase())

    @Test
    fun `add subscription defaults to mode all`() = runTest {
        useCase.addSubscription(userId = 1L, channel = "testchannel")
        assertTrue(fakeRepo.getActiveChannels().contains("testchannel"))
    }

    @Test
    fun `remove subscription removes keywords too`() = runTest {
        useCase.addSubscription(1L, "testchannel")
        fakeRepo.addKeyword(1L, "testchannel", "crypto")
        useCase.removeSubscription(1L, "testchannel")
        assertFalse(fakeRepo.getActiveChannels().contains("testchannel"))
        assertTrue(fakeRepo.getKeywords(1L, "testchannel").isEmpty())
    }

    @Test
    fun `set mode include requires keywords`() = runTest {
        useCase.addSubscription(1L, "testchannel")
        val result = useCase.setMode(1L, "testchannel", "include")
        assertTrue(result.isFailure)
        assertEquals("Cannot set mode 'include' without keywords", result.exceptionOrNull()?.message)
    }

    @Test
    fun `set mode include succeeds with keywords`() = runTest {
        useCase.addSubscription(1L, "testchannel")
        useCase.addKeyword(1L, "testchannel", "crypto")
        val result = useCase.setMode(1L, "testchannel", "include")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `remove last keyword resets mode to all`() = runTest {
        useCase.addSubscription(1L, "testchannel")
        useCase.addKeyword(1L, "testchannel", "crypto")
        useCase.setMode(1L, "testchannel", "include")
        useCase.removeKeyword(1L, "testchannel", "crypto")
        assertEquals("all", fakeRepo.subs["testchannel"]?.mode)
    }
}
