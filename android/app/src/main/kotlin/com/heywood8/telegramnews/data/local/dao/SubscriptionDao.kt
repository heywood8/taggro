package com.heywood8.telegramnews.data.local.dao

import androidx.room.*
import com.heywood8.telegramnews.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE userId = :userId")
    fun observeByUser(userId: Long): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE userId = :userId")
    suspend fun getByUser(userId: Long): List<SubscriptionEntity>

    @Query("SELECT DISTINCT channel FROM subscriptions WHERE active = 1")
    suspend fun getActiveChannels(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sub: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE userId = :userId AND channel = :channel")
    suspend fun delete(userId: Long, channel: String)

    @Query("UPDATE subscriptions SET active = :active WHERE userId = :userId")
    suspend fun setActiveForUser(userId: Long, active: Boolean)

    @Query("UPDATE subscriptions SET mode = :mode WHERE userId = :userId AND channel = :channel")
    suspend fun setMode(userId: Long, channel: String, mode: String)

    @Query("SELECT COUNT(*) FROM subscriptions WHERE userId = :userId AND channel = :channel")
    suspend fun count(userId: Long, channel: String): Int

    @Query("UPDATE subscriptions SET include_photos = :value WHERE userId = :userId AND channel = :channel")
    suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean)
}
