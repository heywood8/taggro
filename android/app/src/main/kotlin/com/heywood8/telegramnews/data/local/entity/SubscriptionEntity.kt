package com.heywood8.telegramnews.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "subscriptions", primaryKeys = ["userId", "channel"])
data class SubscriptionEntity(
    val userId: Long,
    val channel: String,
    val mode: String = "all",
    val active: Boolean = true,
    @ColumnInfo(name = "include_photos", defaultValue = "0")
    val includePhotos: Boolean = false,
)
