package com.heywood8.telegramnews.di

import android.content.Context
import androidx.room.Room
import com.heywood8.telegramnews.data.local.AppDatabase
import com.heywood8.telegramnews.data.local.MIGRATION_1_2
import com.heywood8.telegramnews.data.local.MIGRATION_2_3
import com.heywood8.telegramnews.data.local.MIGRATION_3_4
import com.heywood8.telegramnews.data.local.dao.KeywordDao
import com.heywood8.telegramnews.data.local.dao.LastSeenDao
import com.heywood8.telegramnews.data.local.dao.MessageDao
import com.heywood8.telegramnews.data.local.dao.ReadMessageDao
import com.heywood8.telegramnews.data.local.dao.SubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "telegramnews.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideSubscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideKeywordDao(db: AppDatabase): KeywordDao = db.keywordDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideLastSeenDao(db: AppDatabase): LastSeenDao = db.lastSeenDao()

    @Provides
    fun provideReadMessageDao(db: AppDatabase): ReadMessageDao = db.readMessageDao()
}
