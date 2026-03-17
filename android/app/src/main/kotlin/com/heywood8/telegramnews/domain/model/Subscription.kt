package com.heywood8.telegramnews.domain.model

data class Subscription(
    val channel: String,
    val mode: String,
    val keywords: List<String>,
    val active: Boolean,
    val includePhotos: Boolean = false,
)
