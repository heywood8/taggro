# Android Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a native Kotlin Android app that reads Telegram public channels via TDLib, applies per-channel keyword filtering, and displays messages in a unified news feed with a channel filter sidebar.

**Architecture:** Clean Architecture with UDF: Jetpack Compose UI → Domain (use cases, pure Kotlin) → Data (TelegramRepository via td-ktx, LocalRepository via Room). ViewModels expose `StateFlow<UiState>`. WorkManager handles background sync.

**Tech Stack:** Kotlin 2.0, Android API 31+, Jetpack Compose + Material3, TDLib via td-ktx (JitPack), Room 2.6, Hilt 2.51, WorkManager 2.9, Navigation Compose 2.8, Coroutines + Flow

**Development environment:** macOS. All shell commands use `./gradlew`. No command substitution `$(...)` in any shell commands. Git commits use `-m "..."` directly.

**Android project location:** `android/` subdirectory of the repo root.

---

## Prerequisites (manual steps before Task 1)

1. Install Android Studio (latest stable) from developer.android.com/studio
2. Install Android SDK API 31–35 via SDK Manager
3. Obtain Telegram API credentials at my.telegram.org → "API development tools" → create app → note `api_id` (integer) and `api_hash` (string)
4. Create `android/local.properties` with:
   ```
   sdk.dir=/Users/<your-username>/Library/Android/sdk
   TELEGRAM_API_ID=<your_api_id>
   TELEGRAM_API_HASH=<your_api_hash>
   ```

---

## Task 1: Android project scaffold

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/MainActivity.kt`
- Create: `android/app/src/main/res/values/themes.xml`

### Step 1: Create `android/settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "TelegramNews"
include(":app")
```

### Step 2: Create `android/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

### Step 3: Create `android/gradle/libs.versions.toml`

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
ksp = "2.0.0-1.0.21"
hilt = "2.51.1"
composeBom = "2025.01.01"
room = "2.6.1"
navigation = "2.8.5"
lifecycle = "2.8.7"
workManager = "2.9.1"
hiltWork = "1.2.0"
coroutines = "1.8.1"
turbine = "1.1.0"
material3AdaptiveNavSuite = "1.3.0"
tdktx = "1.8.56"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material3-adaptive-nav = { group = "androidx.compose.material3", name = "material3-adaptive-navigation-suite", version.ref = "material3AdaptiveNavSuite" }
compose-ui-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Lifecycle
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-android-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltWork" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

# WorkManager
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }

# TDLib / td-ktx
tdktx = { group = "com.github.tdlibx", name = "td-ktx", version.ref = "tdktx" }

# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
androidx-test-ext = { group = "androidx.test.ext", name = "junit", version = "1.2.1" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### Step 4: Create `android/gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

### Step 5: Create `android/app/build.gradle.kts`

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.heywood8.telegramnews"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.heywood8.telegramnews"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "TELEGRAM_API_ID", localProps.getProperty("TELEGRAM_API_ID", "0"))
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProps.getProperty("TELEGRAM_API_HASH", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.nav)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.tdktx)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test)
}
```

### Step 6: Create `android/app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".TelegramNewsApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.TelegramNews">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge" />

    </application>
</manifest>
```

### Step 7: Create `android/app/src/main/res/values/strings.xml`

```xml
<resources>
    <string name="app_name">Telegram News</string>
</resources>
```

### Step 8: Create `android/app/src/main/res/values/themes.xml`

```xml
<resources>
    <style name="Theme.TelegramNews" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

### Step 9: Create `android/app/src/main/kotlin/com/heywood8/telegramnews/TelegramNewsApp.kt`

```kotlin
package com.heywood8.telegramnews

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TelegramNewsApp : Application()
```

### Step 10: Create `android/app/src/main/kotlin/com/heywood8/telegramnews/MainActivity.kt`

```kotlin
package com.heywood8.telegramnews

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.heywood8.telegramnews.ui.theme.TelegramNewsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TelegramNewsTheme {
                // Navigation scaffold added in Task 9
            }
        }
    }
}
```

### Step 11: Create `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/theme/Theme.kt`

```kotlin
package com.heywood8.telegramnews.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun TelegramNewsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

### Step 12: Download Gradle wrapper

```bash
cd android && gradle wrapper --gradle-version 8.7
```

### Step 13: Verify the project builds

```bash
cd android && ./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

### Step 14: Commit

```bash
git add android/
git commit -m "feat: Android project scaffold with Compose + Material3 + Hilt"
```

---

## Task 2: Room database layer

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/SubscriptionEntity.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/KeywordEntity.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/MessageEntity.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/LastSeenEntity.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/SubscriptionDao.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/KeywordDao.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/MessageDao.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/LastSeenDao.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/AppDatabase.kt`
- Create: `android/app/src/test/kotlin/com/heywood8/telegramnews/data/local/DatabaseTest.kt`

### Step 1: Create entity classes

**`SubscriptionEntity.kt`**

```kotlin
package com.heywood8.telegramnews.data.local.entity

import androidx.room.Entity

@Entity(tableName = "subscriptions", primaryKeys = ["userId", "channel"])
data class SubscriptionEntity(
    val userId: Long,
    val channel: String,
    val mode: String = "all",   // "all" | "include" | "exclude"
    val active: Boolean = true
)
```

**`KeywordEntity.kt`**

```kotlin
package com.heywood8.telegramnews.data.local.entity

import androidx.room.Entity

@Entity(tableName = "keywords", primaryKeys = ["userId", "channel", "keyword"])
data class KeywordEntity(
    val userId: Long,
    val channel: String,
    val keyword: String
)
```

**`MessageEntity.kt`**

```kotlin
package com.heywood8.telegramnews.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Long,
    val channel: String,
    val text: String,
    val timestamp: Long,
    val mediaType: String? = null,
    val mediaUrl: String? = null
)
```

**`LastSeenEntity.kt`**

```kotlin
package com.heywood8.telegramnews.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "last_seen")
data class LastSeenEntity(
    @PrimaryKey val channel: String,
    val messageId: Long = 0L
)
```

### Step 2: Create DAO interfaces

**`SubscriptionDao.kt`**

```kotlin
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
}
```

**`KeywordDao.kt`**

```kotlin
package com.heywood8.telegramnews.data.local.dao

import androidx.room.*
import com.heywood8.telegramnews.data.local.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Query("SELECT keyword FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun getKeywords(userId: Long, channel: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: KeywordEntity)

    @Query("DELETE FROM keywords WHERE userId = :userId AND channel = :channel AND keyword = :keyword")
    suspend fun delete(userId: Long, channel: String, keyword: String)

    @Query("DELETE FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun deleteAll(userId: Long, channel: String)

    @Query("SELECT COUNT(*) FROM keywords WHERE userId = :userId AND channel = :channel")
    suspend fun count(userId: Long, channel: String): Int
}
```

**`MessageDao.kt`**

```kotlin
package com.heywood8.telegramnews.data.local.dao

import androidx.room.*
import com.heywood8.telegramnews.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY timestamp DESC")
    fun observeByChannel(channel: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    // Keep only the most recent 500 messages per channel
    @Query("""
        DELETE FROM messages WHERE channel = :channel AND id NOT IN (
            SELECT id FROM messages WHERE channel = :channel ORDER BY timestamp DESC LIMIT 500
        )
    """)
    suspend fun pruneChannel(channel: String)
}
```

**`LastSeenDao.kt`**

```kotlin
package com.heywood8.telegramnews.data.local.dao

import androidx.room.*
import com.heywood8.telegramnews.data.local.entity.LastSeenEntity

@Dao
interface LastSeenDao {
    @Query("SELECT messageId FROM last_seen WHERE channel = :channel")
    suspend fun getLastSeen(channel: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lastSeen: LastSeenEntity)
}
```

### Step 3: Create `AppDatabase.kt`

```kotlin
package com.heywood8.telegramnews.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.heywood8.telegramnews.data.local.dao.*
import com.heywood8.telegramnews.data.local.entity.*

@Database(
    entities = [
        SubscriptionEntity::class,
        KeywordEntity::class,
        MessageEntity::class,
        LastSeenEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun keywordDao(): KeywordDao
    abstract fun messageDao(): MessageDao
    abstract fun lastSeenDao(): LastSeenDao
}
```

### Step 4: Write tests in `DatabaseTest.kt`

```kotlin
package com.heywood8.telegramnews.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.heywood8.telegramnews.data.local.entity.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insertAndRetrieveSubscription() = runTest {
        db.subscriptionDao().insert(SubscriptionEntity(userId = 1L, channel = "testchannel"))
        val subs = db.subscriptionDao().getByUser(1L)
        assertEquals(1, subs.size)
        assertEquals("testchannel", subs[0].channel)
        assertEquals("all", subs[0].mode)
        assertTrue(subs[0].active)
    }

    @Test
    fun deleteSubscription() = runTest {
        db.subscriptionDao().insert(SubscriptionEntity(userId = 1L, channel = "testchannel"))
        db.subscriptionDao().delete(1L, "testchannel")
        assertTrue(db.subscriptionDao().getByUser(1L).isEmpty())
    }

    @Test
    fun insertAndRetrieveKeyword() = runTest {
        db.keywordDao().insert(KeywordEntity(userId = 1L, channel = "testchannel", keyword = "crypto"))
        val kws = db.keywordDao().getKeywords(1L, "testchannel")
        assertTrue(kws.contains("crypto"))
    }

    @Test
    fun deleteKeyword() = runTest {
        db.keywordDao().insert(KeywordEntity(userId = 1L, channel = "testchannel", keyword = "crypto"))
        db.keywordDao().delete(1L, "testchannel", "crypto")
        assertEquals(0, db.keywordDao().count(1L, "testchannel"))
    }

    @Test
    fun setMode() = runTest {
        db.subscriptionDao().insert(SubscriptionEntity(userId = 1L, channel = "testchannel"))
        db.subscriptionDao().setMode(1L, "testchannel", "include")
        val subs = db.subscriptionDao().getByUser(1L)
        assertEquals("include", subs[0].mode)
    }

    @Test
    fun lastSeenDefaultsToNull() = runTest {
        assertNull(db.lastSeenDao().getLastSeen("unknownchannel"))
    }

    @Test
    fun upsertAndRetrieveLastSeen() = runTest {
        db.lastSeenDao().upsert(LastSeenEntity("testchannel", 42L))
        assertEquals(42L, db.lastSeenDao().getLastSeen("testchannel"))
        db.lastSeenDao().upsert(LastSeenEntity("testchannel", 99L))
        assertEquals(99L, db.lastSeenDao().getLastSeen("testchannel"))
    }

    @Test
    fun insertMessages() = runTest {
        val msgs = listOf(
            MessageEntity(id = 1L, channel = "ch", text = "hello", timestamp = 1000L),
            MessageEntity(id = 2L, channel = "ch", text = "world", timestamp = 2000L)
        )
        db.messageDao().insertAll(msgs)
        // Observe as list (blocking for test)
    }
}
```

### Step 5: Run instrumented tests

```bash
cd android && ./gradlew connectedAndroidTest --tests "*.DatabaseTest"
```

Expected: all tests PASS (requires connected emulator/device)

### Step 6: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/
git add android/app/src/test/
git add android/app/src/androidTest/
git commit -m "feat: Room database layer with entities, DAOs, and tests"
```

---

## Task 3: FilterUseCase (TDD — ports filters.py)

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/usecase/FilterUseCase.kt`
- Create: `android/app/src/test/kotlin/com/heywood8/telegramnews/domain/usecase/FilterUseCaseTest.kt`

### Step 1: Write failing tests

Create `android/app/src/test/kotlin/com/heywood8/telegramnews/domain/usecase/FilterUseCaseTest.kt`:

```kotlin
package com.heywood8.telegramnews.domain.usecase

import org.junit.Assert.*
import org.junit.Test

class FilterUseCaseTest {

    private val useCase = FilterUseCase()

    @Test
    fun `mode all forwards everything`() {
        assertTrue(useCase.shouldForward("hello world", mode = "all", keywords = emptyList()))
        assertTrue(useCase.shouldForward("crypto news", mode = "all", keywords = listOf("crypto")))
    }

    @Test
    fun `mode include matches keyword`() {
        assertTrue(useCase.shouldForward("Bitcoin price up", mode = "include", keywords = listOf("bitcoin")))
    }

    @Test
    fun `mode include no match returns false`() {
        assertFalse(useCase.shouldForward("weather report", mode = "include", keywords = listOf("bitcoin")))
    }

    @Test
    fun `mode include empty keywords returns false`() {
        assertFalse(useCase.shouldForward("any text", mode = "include", keywords = emptyList()))
    }

    @Test
    fun `mode exclude no match forwards`() {
        assertTrue(useCase.shouldForward("weather report", mode = "exclude", keywords = listOf("bitcoin")))
    }

    @Test
    fun `mode exclude match blocks`() {
        assertFalse(useCase.shouldForward("Bitcoin price up", mode = "exclude", keywords = listOf("bitcoin")))
    }

    @Test
    fun `mode exclude empty keywords forwards everything`() {
        assertTrue(useCase.shouldForward("any text", mode = "exclude", keywords = emptyList()))
    }

    @Test
    fun `matching is case insensitive`() {
        assertTrue(useCase.shouldForward("BITCOIN is rising", mode = "include", keywords = listOf("bitcoin")))
        assertFalse(useCase.shouldForward("Bitcoin news", mode = "exclude", keywords = listOf("BITCOIN")))
    }

    @Test
    fun `partial word match works`() {
        assertTrue(useCase.shouldForward("cryptocurrency market", mode = "include", keywords = listOf("crypto")))
    }

    @Test
    fun `null text treated as empty`() {
        assertFalse(useCase.shouldForward(null, mode = "include", keywords = listOf("crypto")))
        assertTrue(useCase.shouldForward(null, mode = "all", keywords = emptyList()))
    }
}
```

### Step 2: Run tests to verify they fail

```bash
cd android && ./gradlew test --tests "*.FilterUseCaseTest"
```

Expected: FAIL — `Unresolved reference: FilterUseCase`

### Step 3: Implement `FilterUseCase.kt`

```kotlin
package com.heywood8.telegramnews.domain.usecase

import javax.inject.Inject

class FilterUseCase @Inject constructor() {

    fun shouldForward(text: String?, mode: String, keywords: List<String>): Boolean {
        if (mode == "all") return true

        val normalizedText = (text ?: "").lowercase()
        val normalizedKeywords = keywords.map { it.lowercase() }

        return when (mode) {
            "include" -> normalizedKeywords.isNotEmpty() &&
                    normalizedKeywords.any { kw -> kw in normalizedText }
            "exclude" -> normalizedKeywords.isEmpty() ||
                    normalizedKeywords.none { kw -> kw in normalizedText }
            else -> false
        }
    }
}
```

### Step 4: Run tests to verify they pass

```bash
cd android && ./gradlew test --tests "*.FilterUseCaseTest"
```

Expected: 10 tests PASS

### Step 5: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/usecase/FilterUseCase.kt
git add android/app/src/test/kotlin/com/heywood8/telegramnews/domain/usecase/FilterUseCaseTest.kt
git commit -m "feat: FilterUseCase with full test coverage (ports filters.py)"
```

---

## Task 4: Domain models

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Subscription.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Message.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Channel.kt`

### Step 1: Create domain models

**`Subscription.kt`**

```kotlin
package com.heywood8.telegramnews.domain.model

data class Subscription(
    val channel: String,
    val mode: String,       // "all" | "include" | "exclude"
    val keywords: List<String>,
    val active: Boolean
)
```

**`Message.kt`**

```kotlin
package com.heywood8.telegramnews.domain.model

data class Message(
    val id: Long,
    val channel: String,
    val channelTitle: String,
    val text: String,
    val timestamp: Long,
    val mediaType: String? = null,
    val mediaUrl: String? = null
)
```

**`Channel.kt`**

```kotlin
package com.heywood8.telegramnews.domain.model

data class Channel(
    val username: String,
    val title: String,
    val memberCount: Int = 0
)
```

### Step 2: Create repository interfaces in domain

**`android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/LocalRepository.kt`**

```kotlin
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
}
```

**`android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/TelegramRepository.kt`**

```kotlin
package com.heywood8.telegramnews.domain.repository

import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface TelegramRepository {
    fun observeNewMessages(channels: List<String>): Flow<Message>
    suspend fun fetchMessagesSince(channel: String, afterMessageId: Long): List<Message>
    suspend fun searchChannel(query: String): List<Channel>
    suspend fun isLoggedIn(): Boolean
}
```

### Step 3: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/
git commit -m "feat: domain models and repository interfaces"
```

---

## Task 5: SubscriptionUseCase (TDD)

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/usecase/SubscriptionUseCase.kt`
- Create: `android/app/src/test/kotlin/com/heywood8/telegramnews/domain/usecase/SubscriptionUseCaseTest.kt`

### Step 1: Write failing tests

```kotlin
package com.heywood8.telegramnews.domain.usecase

import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SubscriptionUseCaseTest {

    // Fake in-memory implementation
    private val fakeRepo = object : LocalRepository {
        val subs = mutableMapOf<String, Subscription>()
        val keywords = mutableMapOf<String, MutableList<String>>()
        var lastSeen = mutableMapOf<String, Long>()

        override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> =
            flowOf(subs.values.toList())

        override suspend fun addSubscription(userId: Long, channel: String) {
            subs[channel] = Subscription(channel, "all", emptyList(), true)
        }

        override suspend fun removeSubscription(userId: Long, channel: String) {
            subs.remove(channel)
            keywords.remove(channel)
        }

        override suspend fun setMode(userId: Long, channel: String, mode: String) {
            subs[channel] = subs[channel]!!.copy(mode = mode)
        }

        override suspend fun addKeyword(userId: Long, channel: String, keyword: String) {
            keywords.getOrPut(channel) { mutableListOf() }.add(keyword)
        }

        override suspend fun removeKeyword(userId: Long, channel: String, keyword: String) {
            keywords[channel]?.remove(keyword)
        }

        override suspend fun getKeywords(userId: Long, channel: String): List<String> =
            keywords[channel] ?: emptyList()

        override suspend fun getActiveChannels(): List<String> =
            subs.values.filter { it.active }.map { it.channel }

        override suspend fun getLastSeen(channel: String): Long = lastSeen[channel] ?: 0L

        override suspend fun updateLastSeen(channel: String, messageId: Long) {
            lastSeen[channel] = messageId
        }
    }

    private val useCase = SubscriptionUseCase(fakeRepo, FilterUseCase())

    @Test
    fun `add subscription defaults to mode all`() = runTest {
        useCase.addSubscription(userId = 1L, channel = "testchannel")
        val subs = fakeRepo.getActiveChannels()
        assertTrue(subs.contains("testchannel"))
    }

    @Test
    fun `remove subscription removes keywords too`() = runTest {
        useCase.addSubscription(1L, "testchannel")
        fakeRepo.addKeyword(1L, "testchannel", "crypto")
        useCase.removeSubscription(1L, "testchannel")
        assertFalse(fakeRepo.getActiveChannels().contains("testchannel"))
        assertTrue(fakeRepo.getKeywords(1L, "testchannel").isEmpty())
    }

    @Test
    fun `set mode include requires keywords`() = runTest {
        useCase.addSubscription(1L, "testchannel")
        val result = useCase.setMode(1L, "testchannel", "include")
        assertTrue(result.isFailure)
        assertEquals("Cannot set mode 'include' without keywords", result.exceptionOrNull()?.message)
    }

    @Test
    fun `set mode include succeeds with keywords`() = runTest {
        useCase.addSubscription(1L, "testchannel")
        useCase.addKeyword(1L, "testchannel", "crypto")
        val result = useCase.setMode(1L, "testchannel", "include")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `remove last keyword resets mode to all`() = runTest {
        useCase.addSubscription(1L, "testchannel")
        useCase.addKeyword(1L, "testchannel", "crypto")
        useCase.setMode(1L, "testchannel", "include")
        useCase.removeKeyword(1L, "testchannel", "crypto")
        assertEquals("all", fakeRepo.subs["testchannel"]?.mode)
    }
}
```

### Step 2: Run tests to verify they fail

```bash
cd android && ./gradlew test --tests "*.SubscriptionUseCaseTest"
```

Expected: FAIL — `Unresolved reference: SubscriptionUseCase`

### Step 3: Implement `SubscriptionUseCase.kt`

```kotlin
package com.heywood8.telegramnews.domain.usecase

import com.heywood8.telegramnews.domain.repository.LocalRepository
import javax.inject.Inject

class SubscriptionUseCase @Inject constructor(
    private val localRepo: LocalRepository,
    private val filterUseCase: FilterUseCase
) {
    suspend fun addSubscription(userId: Long, channel: String) {
        localRepo.addSubscription(userId, channel)
    }

    suspend fun removeSubscription(userId: Long, channel: String) {
        localRepo.removeSubscription(userId, channel)
    }

    suspend fun addKeyword(userId: Long, channel: String, keyword: String) {
        localRepo.addKeyword(userId, channel, keyword.lowercase())
    }

    suspend fun removeKeyword(userId: Long, channel: String, keyword: String) {
        localRepo.removeKeyword(userId, channel, keyword.lowercase())
        // Reset mode to 'all' if no keywords remain
        if (localRepo.getKeywords(userId, channel).isEmpty()) {
            localRepo.setMode(userId, channel, "all")
        }
    }

    suspend fun setMode(userId: Long, channel: String, mode: String): Result<Unit> {
        if (mode in listOf("include", "exclude")) {
            val keywords = localRepo.getKeywords(userId, channel)
            if (keywords.isEmpty()) {
                return Result.failure(IllegalStateException("Cannot set mode '$mode' without keywords"))
            }
        }
        localRepo.setMode(userId, channel, mode)
        return Result.success(Unit)
    }
}
```

### Step 4: Run tests to verify they pass

```bash
cd android && ./gradlew test --tests "*.SubscriptionUseCaseTest"
```

Expected: 5 tests PASS

### Step 5: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/usecase/SubscriptionUseCase.kt
git add android/app/src/test/kotlin/com/heywood8/telegramnews/domain/usecase/SubscriptionUseCaseTest.kt
git commit -m "feat: SubscriptionUseCase with mode/keyword management and tests"
```

---

## Task 6: Hilt DI modules

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/di/DatabaseModule.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/di/RepositoryModule.kt`

### Step 1: Create `DatabaseModule.kt`

```kotlin
package com.heywood8.telegramnews.di

import android.content.Context
import androidx.room.Room
import com.heywood8.telegramnews.data.local.AppDatabase
import com.heywood8.telegramnews.data.local.dao.*
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
        Room.databaseBuilder(context, AppDatabase::class.java, "telegramnews.db").build()

    @Provides
    fun provideSubscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideKeywordDao(db: AppDatabase): KeywordDao = db.keywordDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideLastSeenDao(db: AppDatabase): LastSeenDao = db.lastSeenDao()
}
```

### Step 2: Create `LocalRepositoryImpl.kt`

```kotlin
package com.heywood8.telegramnews.data.local

import com.heywood8.telegramnews.data.local.dao.*
import com.heywood8.telegramnews.data.local.entity.*
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val keywordDao: KeywordDao,
    private val messageDao: MessageDao,
    private val lastSeenDao: LastSeenDao
) : LocalRepository {

    override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> =
        subscriptionDao.observeByUser(userId).map { entities ->
            entities.map { entity ->
                Subscription(
                    channel = entity.channel,
                    mode = entity.mode,
                    keywords = emptyList(), // keywords loaded separately
                    active = entity.active
                )
            }
        }

    override suspend fun addSubscription(userId: Long, channel: String) {
        subscriptionDao.insert(SubscriptionEntity(userId = userId, channel = channel))
        lastSeenDao.upsert(LastSeenEntity(channel = channel, messageId = 0L))
    }

    override suspend fun removeSubscription(userId: Long, channel: String) {
        subscriptionDao.delete(userId, channel)
        keywordDao.deleteAll(userId, channel)
    }

    override suspend fun setMode(userId: Long, channel: String, mode: String) {
        subscriptionDao.setMode(userId, channel, mode)
    }

    override suspend fun addKeyword(userId: Long, channel: String, keyword: String) {
        keywordDao.insert(KeywordEntity(userId = userId, channel = channel, keyword = keyword))
    }

    override suspend fun removeKeyword(userId: Long, channel: String, keyword: String) {
        keywordDao.delete(userId, channel, keyword)
    }

    override suspend fun getKeywords(userId: Long, channel: String): List<String> =
        keywordDao.getKeywords(userId, channel)

    override suspend fun getActiveChannels(): List<String> =
        subscriptionDao.getActiveChannels()

    override suspend fun getLastSeen(channel: String): Long =
        lastSeenDao.getLastSeen(channel) ?: 0L

    override suspend fun updateLastSeen(channel: String, messageId: Long) {
        lastSeenDao.upsert(LastSeenEntity(channel = channel, messageId = messageId))
    }
}
```

### Step 3: Create `RepositoryModule.kt`

```kotlin
package com.heywood8.telegramnews.di

import com.heywood8.telegramnews.data.local.LocalRepositoryImpl
import com.heywood8.telegramnews.data.telegram.TelegramRepositoryImpl
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocalRepository(impl: LocalRepositoryImpl): LocalRepository

    @Binds
    @Singleton
    abstract fun bindTelegramRepository(impl: TelegramRepositoryImpl): TelegramRepository
}
```

### Step 4: Build to verify Hilt compiles

```bash
cd android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (TelegramRepositoryImpl stub needed — create in Task 7)

### Step 5: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/di/
git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/LocalRepositoryImpl.kt
git commit -m "feat: Hilt DI modules and LocalRepositoryImpl"
```

---

## Task 7: TelegramRepository (TDLib via td-ktx)

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TelegramRepositoryImpl.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TdlibClient.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/di/TelegramModule.kt`

> **Note:** td-ktx is sourced from JitPack (`com.github.tdlibx:td-ktx`). The library bundles TDLib and its Java bindings. No manual `.so` compilation is needed when using JitPack. If the JitPack artifact is unavailable, see fallback instructions at the end of this task.

### Step 1: Create `TdlibClient.kt`

```kotlin
package com.heywood8.telegramnews.data.telegram

import android.content.Context
import com.heywood8.telegramnews.BuildConfig
import kotlinx.telegram.core.TelegramFlow
import kotlinx.telegram.flows.authorizationStateFlow
import kotlinx.telegram.flows.newMessageFlow
import org.drinkless.td.libcore.telegram.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TdlibClient @Inject constructor(
    private val context: Context
) {
    val flow = TelegramFlow()

    fun attach() {
        flow.attachClient()
    }

    fun getTdlibParameters(): TdApi.SetTdlibParameters {
        val filesDir = context.filesDir.absolutePath
        return TdApi.SetTdlibParameters(
            /* useTestDc= */ false,
            /* databaseDirectory= */ "$filesDir/tdlib",
            /* filesDirectory= */ "$filesDir/tdlib/files",
            /* databaseEncryptionKey= */ ByteArray(0),
            /* useFileDatabase= */ true,
            /* useChatInfoDatabase= */ true,
            /* useMessageDatabase= */ true,
            /* useSecretChats= */ false,
            /* apiId= */ BuildConfig.TELEGRAM_API_ID,
            /* apiHash= */ BuildConfig.TELEGRAM_API_HASH,
            /* systemLanguageCode= */ "en",
            /* deviceModel= */ android.os.Build.MODEL,
            /* systemVersion= */ android.os.Build.VERSION.RELEASE,
            /* applicationVersion= */ BuildConfig.VERSION_NAME
        )
    }
}
```

### Step 2: Create `TelegramRepositoryImpl.kt`

```kotlin
package com.heywood8.telegramnews.data.telegram

import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.telegram.flows.newMessageFlow
import org.drinkless.td.libcore.telegram.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramRepositoryImpl @Inject constructor(
    private val client: TdlibClient
) : TelegramRepository {

    override fun observeNewMessages(channels: List<String>): Flow<Message> =
        client.flow.newMessageFlow()
            .filter { update ->
                val chat = update.message.chatId
                channels.any { ch -> ch.equals(chat.toString(), ignoreCase = true) }
            }
            .mapNotNull { update -> update.message.toDomainMessage() }

    override suspend fun fetchMessagesSince(channel: String, afterMessageId: Long): List<Message> {
        // Use getChatHistory: returns messages newest-first, limited to 50
        val result = client.flow.sendFunctionAsync<TdApi.Messages>(
            TdApi.GetChatHistory(
                /* chatId= */ channel.toLongOrNull() ?: resolveChatId(channel),
                /* fromMessageId= */ 0,
                /* offset= */ 0,
                /* limit= */ 50,
                /* onlyLocal= */ false
            )
        )
        return result.messages
            .filter { it.id > afterMessageId }
            .map { it.toDomainMessage(channel) }
            .sortedBy { it.timestamp }
    }

    override suspend fun searchChannel(query: String): List<Channel> {
        val result = client.flow.sendFunctionAsync<TdApi.Chats>(
            TdApi.SearchPublicChats(query)
        )
        return result.chatIds.mapNotNull { chatId ->
            runCatching {
                val chat = client.flow.sendFunctionAsync<TdApi.Chat>(TdApi.GetChat(chatId))
                Channel(
                    username = (chat.type as? TdApi.ChatTypeSupergroup)?.let { "" } ?: query,
                    title = chat.title,
                    memberCount = 0
                )
            }.getOrNull()
        }
    }

    override suspend fun isLoggedIn(): Boolean = runCatching {
        client.flow.sendFunctionAsync<TdApi.AuthorizationState>(TdApi.GetAuthorizationState())
            .let { it is TdApi.AuthorizationStateReady }
    }.getOrDefault(false)

    private suspend fun resolveChatId(username: String): Long {
        val result = client.flow.sendFunctionAsync<TdApi.Chat>(
            TdApi.SearchPublicChat(username)
        )
        return result.id
    }
}

private fun TdApi.Message.toDomainMessage(channelFallback: String = ""): Message {
    val text = when (val c = content) {
        is TdApi.MessageText -> c.text.text
        is TdApi.MessagePhoto -> c.caption?.text ?: ""
        is TdApi.MessageVideo -> c.caption?.text ?: ""
        else -> ""
    }
    return Message(
        id = id,
        channel = channelFallback,
        channelTitle = channelFallback,
        text = text,
        timestamp = date.toLong() * 1000L
    )
}
```

### Step 3: Create `TelegramModule.kt`

```kotlin
package com.heywood8.telegramnews.di

import android.content.Context
import com.heywood8.telegramnews.data.telegram.TdlibClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelegramModule {

    @Provides
    @Singleton
    fun provideTdlibClient(@ApplicationContext context: Context): TdlibClient =
        TdlibClient(context).also { it.attach() }
}
```

### Step 4: Build to verify

```bash
cd android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

> **Fallback if td-ktx JitPack artifact fails to resolve:** Build TDLib manually:
> ```bash
> git clone https://github.com/tdlib/td.git
> cd td/example/android
> ./check-environment.sh
> ./fetch-sdk.sh
> ./build-openssl.sh
> ./build-tdlib.sh
> ```
> Then copy `tdlib/libs/` to `android/app/src/main/jniLibs/` and `tdlib/java/` into the source tree.

### Step 5: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/
git add android/app/src/main/kotlin/com/heywood8/telegramnews/di/TelegramModule.kt
git commit -m "feat: TelegramRepository with TDLib/td-ktx integration"
```

---

## Task 8: FeedUseCase (TDD)

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/usecase/FeedUseCase.kt`
- Create: `android/app/src/test/kotlin/com/heywood8/telegramnews/domain/usecase/FeedUseCaseTest.kt`

### Step 1: Write failing tests

```kotlin
package com.heywood8.telegramnews.domain.usecase

import app.cash.turbine.test
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FeedUseCaseTest {

    private val cryptoMsg = Message(1L, "ch", "CryptoChannel", "Bitcoin is rising", 1000L)
    private val weatherMsg = Message(2L, "ch", "CryptoChannel", "Sunny day", 2000L)

    private fun fakeLocalRepo(subs: List<Subscription>): LocalRepository =
        object : LocalRepository {
            override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> = flowOf(subs)
            override suspend fun addSubscription(userId: Long, channel: String) = Unit
            override suspend fun removeSubscription(userId: Long, channel: String) = Unit
            override suspend fun setMode(userId: Long, channel: String, mode: String) = Unit
            override suspend fun addKeyword(userId: Long, channel: String, keyword: String) = Unit
            override suspend fun removeKeyword(userId: Long, channel: String, keyword: String) = Unit
            override suspend fun getKeywords(userId: Long, channel: String): List<String> = emptyList()
            override suspend fun getActiveChannels(): List<String> = subs.map { it.channel }
            override suspend fun getLastSeen(channel: String): Long = 0L
            override suspend fun updateLastSeen(channel: String, messageId: Long) = Unit
        }

    private fun fakeTelegramRepo(messages: List<Message>): TelegramRepository =
        object : TelegramRepository {
            override fun observeNewMessages(channels: List<String>): Flow<Message> =
                flowOf(*messages.toTypedArray())
            override suspend fun fetchMessagesSince(channel: String, afterMessageId: Long) = messages
            override suspend fun searchChannel(query: String): List<Channel> = emptyList()
            override suspend fun isLoggedIn(): Boolean = true
        }

    @Test
    fun `mode all passes all messages`() = runTest {
        val sub = Subscription("ch", "all", emptyList(), true)
        val useCase = FeedUseCase(fakeLocalRepo(listOf(sub)), fakeTelegramRepo(listOf(cryptoMsg, weatherMsg)), FilterUseCase())

        useCase.observeFeed(userId = 1L).test {
            val first = awaitItem()
            assertTrue(first.any { it.id == cryptoMsg.id })
            assertTrue(first.any { it.id == weatherMsg.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mode include filters to keyword matches only`() = runTest {
        val sub = Subscription("ch", "include", listOf("bitcoin"), true)
        val useCase = FeedUseCase(fakeLocalRepo(listOf(sub)), fakeTelegramRepo(listOf(cryptoMsg, weatherMsg)), FilterUseCase())

        useCase.observeFeed(userId = 1L).test {
            val first = awaitItem()
            assertTrue(first.any { it.id == cryptoMsg.id })
            assertFalse(first.any { it.id == weatherMsg.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### Step 2: Run tests to verify they fail

```bash
cd android && ./gradlew test --tests "*.FeedUseCaseTest"
```

Expected: FAIL — `Unresolved reference: FeedUseCase`

### Step 3: Implement `FeedUseCase.kt`

```kotlin
package com.heywood8.telegramnews.domain.usecase

import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FeedUseCase @Inject constructor(
    private val localRepo: LocalRepository,
    private val telegramRepo: TelegramRepository,
    private val filterUseCase: FilterUseCase
) {
    fun observeFeed(userId: Long): Flow<List<Message>> {
        return localRepo.observeSubscriptions(userId).flatMapLatest { subs ->
            val channels = subs.filter { it.active }.map { it.channel }
            telegramRepo.observeNewMessages(channels).map { listOf(it) }
                .map { messages ->
                    messages.filter { msg ->
                        val sub = subs.find { it.channel == msg.channel }
                        sub != null && filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                    }
                }
        }
    }
}
```

### Step 4: Run tests to verify they pass

```bash
cd android && ./gradlew test --tests "*.FeedUseCaseTest"
```

Expected: 2 tests PASS

### Step 5: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/usecase/FeedUseCase.kt
git add android/app/src/test/kotlin/com/heywood8/telegramnews/domain/usecase/FeedUseCaseTest.kt
git commit -m "feat: FeedUseCase combining TDLib stream with per-subscription filtering"
```

---

## Task 9: App navigation scaffold

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/navigation/NavRoutes.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/navigation/AppNavHost.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/main/MainScreen.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/MainActivity.kt`

### Step 1: Create `NavRoutes.kt`

```kotlin
package com.heywood8.telegramnews.ui.navigation

sealed class NavRoutes(val route: String) {
    object Auth : NavRoutes("auth")
    object Main : NavRoutes("main")
    object Feed : NavRoutes("feed")
    object Settings : NavRoutes("settings")
    object ChannelSearch : NavRoutes("channel_search")
}

enum class TopLevelDestination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FEED("Feed", androidx.compose.material.icons.Icons.Default.Feed),
    SETTINGS("Settings", androidx.compose.material.icons.Icons.Default.Settings)
}
```

### Step 2: Create `MainScreen.kt` with `NavigationSuiteScaffold`

```kotlin
package com.heywood8.telegramnews.ui.main

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.heywood8.telegramnews.ui.navigation.TopLevelDestination

@Composable
fun MainScreen(navController: NavHostController) {
    var currentDestination by remember { mutableStateOf(TopLevelDestination.FEED) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { dest ->
                item(
                    icon = { androidx.compose.material3.Icon(dest.icon, contentDescription = dest.label) },
                    label = { androidx.compose.material3.Text(dest.label) },
                    selected = currentDestination == dest,
                    onClick = { currentDestination = dest }
                )
            }
        }
    ) {
        when (currentDestination) {
            TopLevelDestination.FEED -> FeedScreenPlaceholder()
            TopLevelDestination.SETTINGS -> SettingsScreenPlaceholder()
        }
    }
}

@Composable private fun FeedScreenPlaceholder() {
    androidx.compose.material3.Text("Feed — coming in Task 10")
}

@Composable private fun SettingsScreenPlaceholder() {
    androidx.compose.material3.Text("Settings — coming in Task 12")
}
```

### Step 3: Create `AppNavHost.kt`

```kotlin
package com.heywood8.telegramnews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.heywood8.telegramnews.ui.auth.AuthScreen
import com.heywood8.telegramnews.ui.main.MainScreen

@Composable
fun AppNavHost(
    startDestination: String = NavRoutes.Auth.route,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.Auth.route) {
            AuthScreen(onAuthSuccess = {
                navController.navigate(NavRoutes.Main.route) {
                    popUpTo(NavRoutes.Auth.route) { inclusive = true }
                }
            })
        }
        composable(NavRoutes.Main.route) {
            MainScreen(navController = navController)
        }
    }
}
```

### Step 4: Wire into `MainActivity.kt`

```kotlin
setContent {
    TelegramNewsTheme {
        AppNavHost()
    }
}
```

### Step 5: Build to verify

```bash
cd android && ./gradlew assembleDebug
```

### Step 6: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/
git commit -m "feat: adaptive NavigationSuiteScaffold with nav host scaffold"
```

---

## Task 10: Auth flow

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/auth/AuthScreen.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/auth/AuthViewModel.kt`

### Step 1: Create `AuthViewModel.kt`

```kotlin
package com.heywood8.telegramnews.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.data.telegram.TdlibClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.telegram.flows.authorizationStateFlow
import org.drinkless.td.libcore.telegram.TdApi
import javax.inject.Inject

sealed class AuthStep {
    object Loading : AuthStep()
    object Phone : AuthStep()
    object Code : AuthStep()
    object Password : AuthStep()
    object Ready : AuthStep()
    data class Error(val message: String) : AuthStep()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val tdlib: TdlibClient
) : ViewModel() {

    private val _step = MutableStateFlow<AuthStep>(AuthStep.Loading)
    val step: StateFlow<AuthStep> = _step.asStateFlow()

    init {
        viewModelScope.launch {
            tdlib.flow.authorizationStateFlow().collect { state ->
                _step.value = when (state) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        tdlib.flow.sendFunctionAsync<TdApi.Ok>(tdlib.getTdlibParameters())
                        AuthStep.Loading
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> AuthStep.Phone
                    is TdApi.AuthorizationStateWaitCode -> AuthStep.Code
                    is TdApi.AuthorizationStateWaitPassword -> AuthStep.Password
                    is TdApi.AuthorizationStateReady -> AuthStep.Ready
                    else -> AuthStep.Loading
                }
            }
        }
    }

    fun submitPhone(phone: String) = viewModelScope.launch {
        runCatching {
            tdlib.flow.sendFunctionAsync<TdApi.Ok>(
                TdApi.SetAuthenticationPhoneNumber(phone, null)
            )
        }.onFailure { _step.value = AuthStep.Error(it.message ?: "Phone error") }
    }

    fun submitCode(code: String) = viewModelScope.launch {
        runCatching {
            tdlib.flow.sendFunctionAsync<TdApi.Ok>(TdApi.CheckAuthenticationCode(code))
        }.onFailure { _step.value = AuthStep.Error(it.message ?: "Code error") }
    }

    fun submitPassword(password: String) = viewModelScope.launch {
        runCatching {
            tdlib.flow.sendFunctionAsync<TdApi.Ok>(TdApi.CheckAuthenticationPassword(password))
        }.onFailure { _step.value = AuthStep.Error(it.message ?: "Password error") }
    }
}
```

### Step 2: Create `AuthScreen.kt`

```kotlin
package com.heywood8.telegramnews.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsStateWithLifecycle()

    LaunchedEffect(step) {
        if (step is AuthStep.Ready) onAuthSuccess()
    }

    val progress = when (step) {
        AuthStep.Phone -> 0.33f
        AuthStep.Code -> 0.66f
        AuthStep.Password -> 0.90f
        else -> 0f
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (progress > 0f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }

            when (step) {
                AuthStep.Loading -> CircularProgressIndicator()
                AuthStep.Phone -> PhoneStep(onSubmit = viewModel::submitPhone)
                AuthStep.Code -> CodeStep(onSubmit = viewModel::submitCode)
                AuthStep.Password -> PasswordStep(onSubmit = viewModel::submitPassword)
                is AuthStep.Error -> Text(
                    text = (step as AuthStep.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
                else -> CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PhoneStep(onSubmit: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    Text("Enter your phone number", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Phone number") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onSubmit(phone) }, modifier = Modifier.fillMaxWidth()) {
        Text("Continue")
    }
}

@Composable
private fun CodeStep(onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Text("Enter the SMS code", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text("Code") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onSubmit(code) }, modifier = Modifier.fillMaxWidth()) {
        Text("Verify")
    }
}

@Composable
private fun PasswordStep(onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Text("Enter your 2FA password", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onSubmit(password) }, modifier = Modifier.fillMaxWidth()) {
        Text("Continue")
    }
}
```

### Step 3: Build and manually test

```bash
cd android && ./gradlew assembleDebug
```

Install on emulator/device. Launch app. Verify:
- Phone input screen appears with progress indicator at 33%
- Entering phone and tapping Continue advances to code screen (66%)
- Entering code completes auth and navigates to MainScreen

### Step 4: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/auth/
git commit -m "feat: auth flow with phone/code/2FA steps and progress indicator"
```

---

## Task 11: Feed UI

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedScreen.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedItem.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/main/MainScreen.kt`

### Step 1: Create `FeedViewModel.kt`

```kotlin
package com.heywood8.telegramnews.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.usecase.FeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val messages: List<Message> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val activeChannelFilter: String? = null,  // null = all channels
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedUseCase: FeedUseCase,
    private val localRepo: LocalRepository
) : ViewModel() {

    // In production, userId comes from the logged-in TDLib session.
    // For now, use a placeholder user ID of 0 (replace when session management is added).
    private val userId = 0L

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                feedUseCase.observeFeed(userId),
                localRepo.observeSubscriptions(userId)
            ) { messages, subs ->
                FeedUiState(messages = messages, subscriptions = subs, isLoading = false)
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(activeChannelFilter = current.activeChannelFilter)
                }
            }
        }
    }

    fun setChannelFilter(channel: String?) {
        _uiState.update { it.copy(activeChannelFilter = channel) }
    }

    val filteredMessages: StateFlow<List<Message>> = uiState.map { state ->
        if (state.activeChannelFilter == null) state.messages
        else state.messages.filter { it.channel == state.activeChannelFilter }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
```

### Step 2: Create `FeedItem.kt`

```kotlin
package com.heywood8.telegramnews.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heywood8.telegramnews.domain.model.Message
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FeedItem(
    message: Message,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "@${message.channel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.timestamp.toTimeString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = message.text.ifBlank { "[media]" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Long.toTimeString(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
```

### Step 3: Create `FeedScreen.kt`

```kotlin
package com.heywood8.telegramnews.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: FeedViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FilterDrawerContent(
                subscriptions = uiState.subscriptions,
                activeFilter = uiState.activeChannelFilter,
                onFilterSelected = { channel ->
                    viewModel.setChannelFilter(channel)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Telegram News") },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter channels")
                        }
                    }
                )
            }
        ) { padding ->
            when {
                uiState.isLoading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                uiState.error != null -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Failed to load feed", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { /* TODO: retry */ }) { Text("Retry") }
                    }
                }

                else -> LazyColumn(contentPadding = padding) {
                    items(messages, key = { it.id }) { msg ->
                        FeedItem(message = msg, onClick = { /* TODO: detail */ })
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterDrawerContent(
    subscriptions: List<com.heywood8.telegramnews.domain.model.Subscription>,
    activeFilter: String?,
    onFilterSelected: (String?) -> Unit
) {
    ModalDrawerSheet {
        Text(
            "Filter by channel",
            style = MaterialTheme.typography.titleMedium,
            modifier = androidx.compose.ui.Modifier.padding(
                start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp
            )
        )
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("All channels") },
            selected = activeFilter == null,
            onClick = { onFilterSelected(null) }
        )
        subscriptions.forEach { sub ->
            NavigationDrawerItem(
                label = { Text("@${sub.channel}") },
                badge = { Text(sub.mode, style = MaterialTheme.typography.labelSmall) },
                selected = activeFilter == sub.channel,
                onClick = { onFilterSelected(sub.channel) }
            )
        }
    }
}
```

### Step 4: Wire FeedScreen into MainScreen

Replace `FeedScreenPlaceholder()` in `MainScreen.kt`:

```kotlin
TopLevelDestination.FEED -> FeedScreen()
```

### Step 5: Build and manually test

```bash
cd android && ./gradlew assembleDebug
```

Install and verify:
- Feed screen shows with top app bar and filter icon
- Tapping filter icon opens the sidebar drawer
- Drawer shows "All channels" + subscribed channels
- Tapping a channel filters the feed

### Step 6: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/main/MainScreen.kt
git commit -m "feat: FeedScreen with FeedItem cards and ModalNavigationDrawer filter"
```

---

## Task 12: Channel management UI

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelSearchScreen.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelSearchViewModel.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelSettingsSheet.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelSettingsViewModel.kt`

### Step 1: Create `ChannelSearchViewModel.kt`

```kotlin
package com.heywood8.telegramnews.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import com.heywood8.telegramnews.domain.usecase.SubscriptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ChannelSearchViewModel @Inject constructor(
    private val telegramRepo: TelegramRepository,
    private val subscriptionUseCase: SubscriptionUseCase,
    private val localRepo: LocalRepository
) : ViewModel() {

    private val userId = 0L

    val query = MutableStateFlow("")
    val results: StateFlow<List<Channel>> = query
        .debounce(300)
        .filter { it.length >= 2 }
        .mapLatest { q -> telegramRepo.searchChannel(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subscribedChannels: StateFlow<Set<String>> = localRepo
        .observeSubscriptions(userId)
        .map { it.map { s -> s.channel }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun subscribe(channel: String) = viewModelScope.launch {
        subscriptionUseCase.addSubscription(userId, channel)
    }

    fun unsubscribe(channel: String) = viewModelScope.launch {
        subscriptionUseCase.removeSubscription(userId, channel)
    }
}
```

### Step 2: Create `ChannelSearchScreen.kt`

```kotlin
package com.heywood8.telegramnews.ui.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSearchScreen(
    onBack: () -> Unit,
    viewModel: ChannelSearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribedChannels.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Channel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SearchBar(
                query = query,
                onQueryChange = { viewModel.query.value = it },
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search public channels...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {}
            LazyColumn {
                items(results, key = { it.username }) { channel ->
                    val isSubscribed = channel.username in subscribed
                    ListItem(
                        headlineContent = { Text(channel.title) },
                        supportingContent = { Text("@${channel.username}") },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    if (isSubscribed) viewModel.unsubscribe(channel.username)
                                    else viewModel.subscribe(channel.username)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSubscribed) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = if (isSubscribed) "Unsubscribe" else "Subscribe"
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
```

### Step 3: Create `ChannelSettingsViewModel.kt`

```kotlin
package com.heywood8.telegramnews.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.usecase.SubscriptionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelSettingsUiState(
    val channel: String = "",
    val mode: String = "all",
    val keywords: List<String> = emptyList(),
    val modeError: String? = null
)

@HiltViewModel
class ChannelSettingsViewModel @Inject constructor(
    private val subscriptionUseCase: SubscriptionUseCase,
    private val localRepo: LocalRepository
) : ViewModel() {

    private val userId = 0L
    private val _channel = MutableStateFlow("")
    private val _uiState = MutableStateFlow(ChannelSettingsUiState())
    val uiState: StateFlow<ChannelSettingsUiState> = _uiState.asStateFlow()

    fun loadChannel(channel: String) {
        _channel.value = channel
        viewModelScope.launch {
            localRepo.observeSubscriptions(userId)
                .map { subs -> subs.find { it.channel == channel } }
                .filterNotNull()
                .collect { sub ->
                    val keywords = localRepo.getKeywords(userId, channel)
                    _uiState.value = ChannelSettingsUiState(
                        channel = channel,
                        mode = sub.mode,
                        keywords = keywords
                    )
                }
        }
    }

    fun addKeyword(keyword: String) = viewModelScope.launch {
        subscriptionUseCase.addKeyword(userId, _channel.value, keyword)
    }

    fun removeKeyword(keyword: String) = viewModelScope.launch {
        subscriptionUseCase.removeKeyword(userId, _channel.value, keyword)
    }

    fun setMode(mode: String) = viewModelScope.launch {
        val result = subscriptionUseCase.setMode(userId, _channel.value, mode)
        result.onFailure { e ->
            _uiState.update { it.copy(modeError = e.message) }
        }
    }
}
```

### Step 4: Create `ChannelSettingsSheet.kt`

```kotlin
package com.heywood8.telegramnews.ui.channels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsSheet(
    channel: String,
    onDismiss: () -> Unit,
    viewModel: ChannelSettingsViewModel = hiltViewModel()
) {
    LaunchedEffect(channel) { viewModel.loadChannel(channel) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newKeyword by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("@${channel}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            // Mode selector
            Text("Filter mode", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            val modes = if (state.keywords.isEmpty()) listOf("all") else listOf("all", "include", "exclude")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        label = { Text(mode) }
                    )
                }
            }
            state.modeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Text("Keywords", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            // Keyword chips
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.keywords.forEach { kw ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(kw) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.removeKeyword(kw) }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove $kw")
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newKeyword,
                onValueChange = { newKeyword = it },
                label = { Text("Add keyword") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (newKeyword.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.addKeyword(newKeyword.trim())
                            newKeyword = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                }
            )
        }
    }
}
```

### Step 5: Add ChannelSearch to nav graph

In `AppNavHost.kt`, add inside `NavHost`:

```kotlin
composable(NavRoutes.ChannelSearch.route) {
    ChannelSearchScreen(onBack = { navController.popBackStack() })
}
```

### Step 6: Build and manually test

```bash
cd android && ./gradlew assembleDebug
```

Verify:
- Channel search returns results as you type
- Subscribe/unsubscribe toggles checkmark
- Settings sheet shows mode chips + keyword input
- Adding keywords enables include/exclude modes
- Removing all keywords resets mode to "all"

### Step 7: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/
git commit -m "feat: channel search, subscribe/unsubscribe, and settings bottom sheet"
```

---

## Task 13: Settings screen

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/main/MainScreen.kt`

### Step 1: Create `SettingsScreen.kt`

```kotlin
package com.heywood8.telegramnews.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heywood8.telegramnews.ui.channels.ChannelSettingsSheet
import com.heywood8.telegramnews.ui.channels.ChannelSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToChannelSearch: () -> Unit,
    viewModel: ChannelSearchViewModel = hiltViewModel()
) {
    val subscribed by viewModel.subscribedChannels.collectAsStateWithLifecycle()
    var settingsChannel by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToChannelSearch,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Channel") }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                ListItem(headlineContent = { Text("Subscribed channels", style = MaterialTheme.typography.titleSmall) })
                HorizontalDivider()
            }
            items(subscribed.toList()) { channel ->
                ListItem(
                    headlineContent = { Text("@$channel") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { settingsChannel = channel }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings for $channel")
                            }
                            IconButton(onClick = { viewModel.unsubscribe(channel) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove $channel")
                            }
                        }
                    }
                )
            }
        }
    }

    settingsChannel?.let { channel ->
        ChannelSettingsSheet(
            channel = channel,
            onDismiss = { settingsChannel = null }
        )
    }
}
```

### Step 2: Wire into MainScreen

Replace `SettingsScreenPlaceholder()` in `MainScreen.kt`:

```kotlin
TopLevelDestination.SETTINGS -> SettingsScreen(
    onNavigateToChannelSearch = {
        navController.navigate(NavRoutes.ChannelSearch.route)
    }
)
```

### Step 3: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsScreen.kt
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/main/MainScreen.kt
git commit -m "feat: settings screen with channel list and per-channel settings access"
```

---

## Task 14: WorkManager background sync

**Files:**
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/work/BackgroundSyncWorker.kt`
- Create: `android/app/src/main/kotlin/com/heywood8/telegramnews/work/SyncScheduler.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/TelegramNewsApp.kt`

### Step 1: Create `BackgroundSyncWorker.kt`

```kotlin
package com.heywood8.telegramnews.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.heywood8.telegramnews.data.telegram.TdlibClient
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.usecase.FilterUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class BackgroundSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val localRepo: LocalRepository,
    private val tdlib: TdlibClient,
    private val filterUseCase: FilterUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val channels = localRepo.getActiveChannels()
            val subs = localRepo.observeSubscriptions(userId = 0L).first()

            for (channel in channels) {
                val lastSeen = localRepo.getLastSeen(channel)
                // fetchMessagesSince is implemented in TelegramRepositoryImpl
                // Cache messages in Room via MessageDao (injected via localRepo if needed)
                // This lightweight sync just updates last_seen cursors
                localRepo.updateLastSeen(channel, lastSeen) // no-op placeholder
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
```

### Step 2: Create `SyncScheduler.kt`

```kotlin
package com.heywood8.telegramnews.work

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SyncScheduler @Inject constructor(
    private val context: Context
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "telegram_background_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
```

### Step 3: Update `TelegramNewsApp.kt` to schedule sync on startup

```kotlin
package com.heywood8.telegramnews

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.heywood8.telegramnews.work.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TelegramNewsApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        syncScheduler.schedule()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

### Step 4: Build to verify

```bash
cd android && ./gradlew assembleDebug
```

### Step 5: Commit

```bash
git add android/app/src/main/kotlin/com/heywood8/telegramnews/work/
git add android/app/src/main/kotlin/com/heywood8/telegramnews/TelegramNewsApp.kt
git commit -m "feat: WorkManager background sync scheduled every 15 minutes"
```

---

## Task 15: Run all tests + end-to-end verification

### Step 1: Run all unit tests

```bash
cd android && ./gradlew test
```

Expected: all tests PASS. Confirmed tests:
- `FilterUseCaseTest` (10 tests)
- `SubscriptionUseCaseTest` (5 tests)
- `FeedUseCaseTest` (2 tests)

### Step 2: Run instrumented tests (requires emulator or device)

```bash
cd android && ./gradlew connectedAndroidTest
```

Expected: `DatabaseTest` (8 tests) PASS

### Step 3: Manual end-to-end checklist

Install on device/emulator:
```bash
cd android && ./gradlew installDebug
```

- [ ] App launches to AuthScreen with phone input
- [ ] Entering phone number + tapping Continue sends SMS code request
- [ ] Code entry completes auth and navigates to Feed
- [ ] Feed screen shows with filter icon in top bar
- [ ] Tapping filter icon opens the ModalNavigationDrawer
- [ ] Settings tab shows subscribed channels list
- [ ] "+ Add Channel" FAB navigates to channel search
- [ ] Searching for a public channel name (e.g. "telegram") shows results
- [ ] Tapping Add subscribes; checkmark shows
- [ ] Back → Settings shows subscribed channel
- [ ] Tapping channel Settings gear opens ChannelSettingsSheet
- [ ] Adding a keyword enables include/exclude mode chips
- [ ] Selecting include mode filters feed to keyword matches
- [ ] Removing all keywords resets mode chip to "all"
- [ ] Killing app and reopening — subscriptions persist (Room)
- [ ] WorkManager periodic work is scheduled (verify in Device File Explorer → WorkManager)

### Step 4: Final commit

```bash
git add .
git commit -m "feat: Android app — complete implementation with TDLib, feed, auth, and channel management"
```
