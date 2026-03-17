package com.heywood8.telegramnews.domain.usecase

import app.cash.turbine.test
import com.heywood8.telegramnews.domain.model.AuthState
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

class FeedUseCaseTest {

    private val cryptoMsg = Message(1L, "ch", "CryptoChannel", "Bitcoin is rising", 1000L)
    private val weatherMsg = Message(2L, "ch", "CryptoChannel", "Sunny day", 2000L)

    private fun fakeLocalRepo(subs: List<Subscription>): LocalRepository =
        object : LocalRepository {
            override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> = flowOf(subs)
            override suspend fun addSubscription(userId: Long, channel: String) = Unit
            override suspend fun removeSubscription(userId: Long, channel: String) = Unit
            override suspend fun setMode(userId: Long, channel: String, mode: String) = Unit
            override suspend fun addKeyword(userId: Long, channel: String, keyword: String) = Unit
            override suspend fun removeKeyword(userId: Long, channel: String, keyword: String) = Unit
            override suspend fun getKeywords(userId: Long, channel: String): List<String> = emptyList()
            override suspend fun getActiveChannels(): List<String> = subs.map { it.channel }
            override suspend fun getLastSeen(channel: String): Long = 0L
            override suspend fun updateLastSeen(channel: String, messageId: Long) = Unit
            override suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean) = Unit
        }

    private fun fakeTelegramRepo(messages: List<Message>): TelegramRepository =
        object : TelegramRepository {
            override val authState: Flow<AuthState> = flowOf(AuthState.LoggedIn)
            override fun observeNewMessages(channels: List<String>): Flow<Message> =
                flowOf(*messages.toTypedArray())
            override suspend fun fetchMessagesSince(channel: String, afterMessageId: Long) = messages
            override suspend fun searchChannel(query: String): List<Channel> = emptyList()
            override suspend fun isLoggedIn(): Boolean = true
            override suspend fun sendPhoneNumber(phone: String) = Unit
            override suspend fun sendCode(code: String) = Unit
            override suspend fun sendPassword(password: String) = Unit
            override suspend fun logOut() = Unit
        }

    @Test
    fun `mode all passes all messages`() = runTest {
        val sub = Subscription("ch", "all", emptyList(), true)
        val useCase = FeedUseCase(
            fakeLocalRepo(listOf(sub)),
            fakeTelegramRepo(listOf(cryptoMsg, weatherMsg)),
            FilterUseCase()
        )

        useCase.observeFeed(userId = 1L).test {
            val emission = awaitItem()
            assertTrue(emission.any { it.id == cryptoMsg.id })
            assertTrue(emission.any { it.id == weatherMsg.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mode include filters to keyword matches only`() = runTest {
        val sub = Subscription("ch", "include", listOf("bitcoin"), true)
        val useCase = FeedUseCase(
            fakeLocalRepo(listOf(sub)),
            fakeTelegramRepo(listOf(cryptoMsg, weatherMsg)),
            FilterUseCase()
        )

        useCase.observeFeed(userId = 1L).test {
            val emission = awaitItem()
            assertTrue(emission.any { it.id == cryptoMsg.id })
            assertFalse(emission.any { it.id == weatherMsg.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
