package com.heywood8.telegramnews.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_messages")
data class ReadMessageEntity(@PrimaryKey val messageId: Long)
