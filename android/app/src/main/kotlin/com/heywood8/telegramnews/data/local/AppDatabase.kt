package com.heywood8.telegramnews.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.heywood8.telegramnews.data.local.dao.*
import com.heywood8.telegramnews.data.local.entity.*

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN channelTitle TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS read_messages (messageId INTEGER NOT NULL, PRIMARY KEY(messageId))"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN include_photos INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        SubscriptionEntity::class,
        KeywordEntity::class,
        MessageEntity::class,
        LastSeenEntity::class,
        ReadMessageEntity::class,
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun keywordDao(): KeywordDao
    abstract fun messageDao(): MessageDao
    abstract fun lastSeenDao(): LastSeenDao
    abstract fun readMessageDao(): ReadMessageDao
}
