package com.heywood8.telegramnews.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    @ColumnInfo(name = "media_album_id")
    val mediaAlbumId: Long? = null,
)
