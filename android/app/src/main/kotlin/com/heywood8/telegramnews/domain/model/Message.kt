package com.heywood8.telegramnews.domain.model

data class Message(
    val id: Long,
    val channel: String,
    val channelTitle: String,
    val text: String,
    val timestamp: Long,
    val mediaType: String? = null,
    val mediaUrl: String? = null,
    val photoFileId: Int? = null,
    val isRead: Boolean = false,
    val mediaAlbumId: Long? = null,
    // All photo file IDs in the album (populated when grouping; single-photo = one entry)
    val photoFileIds: List<Int> = emptyList(),
)
