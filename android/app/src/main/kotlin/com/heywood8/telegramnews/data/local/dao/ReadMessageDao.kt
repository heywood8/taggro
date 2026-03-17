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
