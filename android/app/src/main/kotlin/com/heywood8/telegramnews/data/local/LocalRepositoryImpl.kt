package com.heywood8.telegramnews.data.local

import com.heywood8.telegramnews.data.local.dao.KeywordDao
import com.heywood8.telegramnews.data.local.dao.LastSeenDao
import com.heywood8.telegramnews.data.local.dao.MessageDao
import com.heywood8.telegramnews.data.local.dao.SubscriptionDao
import com.heywood8.telegramnews.data.local.entity.KeywordEntity
import com.heywood8.telegramnews.data.local.entity.LastSeenEntity
import com.heywood8.telegramnews.data.local.entity.SubscriptionEntity
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val keywordDao: KeywordDao,
    private val messageDao: MessageDao,
    private val lastSeenDao: LastSeenDao
) : LocalRepository {

    override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> =
        subscriptionDao.observeByUser(userId).map { entities ->
            entities.map { entity ->
                Subscription(
                    channel = entity.channel,
                    mode = entity.mode,
                    keywords = emptyList(),
                    active = entity.active,
                    includePhotos = entity.includePhotos,
                )
            }
        }

    override suspend fun addSubscription(userId: Long, channel: String) {
        subscriptionDao.insert(SubscriptionEntity(userId = userId, channel = channel))
        lastSeenDao.upsert(LastSeenEntity(channel = channel, messageId = 0L))
    }

    override suspend fun removeSubscription(userId: Long, channel: String) {
        subscriptionDao.delete(userId, channel)
        keywordDao.deleteAll(userId, channel)
    }

    override suspend fun setMode(userId: Long, channel: String, mode: String) {
        subscriptionDao.setMode(userId, channel, mode)
    }

    override suspend fun addKeyword(userId: Long, channel: String, keyword: String) {
        keywordDao.insert(KeywordEntity(userId = userId, channel = channel, keyword = keyword))
    }

    override suspend fun removeKeyword(userId: Long, channel: String, keyword: String) {
        keywordDao.delete(userId, channel, keyword)
    }

    override suspend fun getKeywords(userId: Long, channel: String): List<String> =
        keywordDao.getKeywords(userId, channel)

    override suspend fun getActiveChannels(): List<String> =
        subscriptionDao.getActiveChannels()

    override suspend fun getLastSeen(channel: String): Long =
        lastSeenDao.getLastSeen(channel) ?: 0L

    override suspend fun updateLastSeen(channel: String, messageId: Long) {
        lastSeenDao.upsert(LastSeenEntity(channel = channel, messageId = messageId))
    }

    override suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean) {
        subscriptionDao.setIncludePhotos(userId, channel, value)
    }
}
