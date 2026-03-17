package com.heywood8.telegramnews.domain.repository

import com.heywood8.telegramnews.domain.model.Subscription
import kotlinx.coroutines.flow.Flow

interface LocalRepository {
    fun observeSubscriptions(userId: Long): Flow<List<Subscription>>
    suspend fun addSubscription(userId: Long, channel: String)
    suspend fun removeSubscription(userId: Long, channel: String)
    suspend fun setMode(userId: Long, channel: String, mode: String)
    suspend fun addKeyword(userId: Long, channel: String, keyword: String)
    suspend fun removeKeyword(userId: Long, channel: String, keyword: String)
    suspend fun getKeywords(userId: Long, channel: String): List<String>
    suspend fun getActiveChannels(): List<String>
    suspend fun getLastSeen(channel: String): Long
    suspend fun updateLastSeen(channel: String, messageId: Long)
    suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean)
}
