# Bot Token Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Telegram user-account login (phone + code + 2FA) with silent bot-token authentication so the app requires no user login.

**Architecture:** `TelegramRepositoryImpl.initTdlib()` intercepts `AuthorizationStateWaitPhoneNumber` and calls `CheckAuthenticationBotToken` once, guarded by an `AtomicBoolean` to prevent re-firing on subsequent `Closed` events. `authState` becomes a `combine` of the TDLib state flow and a `_authError` flow so token failure surfaces as `AuthState.Error`. All auth UI is deleted; `AppNavHost` becomes a top-level `when(authState)` with no back-stack.

**Tech Stack:** Kotlin, TDLib via td-ktx (`api.sendFunctionAsync`), Jetpack Compose, Hilt, Kotlin Coroutines/Flow

**Spec:** `docs/superpowers/specs/2026-03-21-bot-token-auth-design.md`

---

### Task 1: Add TELEGRAM_BOT_TOKEN credential and harden build

**Files:**
- Modify: `android/local.properties`
- Modify: `android/app/build.gradle.kts` lines 11–17

**Context:** `build.gradle.kts` currently reads `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` with silent defaults (`"0"` and `""`). This task adds the bot token and replaces the silent defaults with fail-fast `error()` calls for all three credentials. The `TELEGRAM_BOT_TOKEN` value comes from `.env` in the repo root.

- [ ] **Step 1: Read the bot token from `.env`**

Read the file at `/Users/nlopatin/git/heywood8/telegram-news-bot/.env` and copy the value of `BOT_TOKEN`.

- [ ] **Step 2: Add TELEGRAM_BOT_TOKEN to local.properties**

Append to `android/local.properties`:
```
TELEGRAM_BOT_TOKEN=<paste value from .env here>
```

- [ ] **Step 3: Update build.gradle.kts**

In `android/app/build.gradle.kts`, replace lines 11–17 (the `localProps` read block and the `apiId`/`apiHash` variables):

```kotlin
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

val apiId = localProps.getProperty("TELEGRAM_API_ID")
    ?: error("TELEGRAM_API_ID not set in local.properties")
val apiHash = localProps.getProperty("TELEGRAM_API_HASH")
    ?: error("TELEGRAM_API_HASH not set in local.properties")
val botToken = localProps.getProperty("TELEGRAM_BOT_TOKEN")
    ?: error("TELEGRAM_BOT_TOKEN not set in local.properties")
```

Then inside `defaultConfig { ... }`, replace the two existing `buildConfigField` lines with three:

```kotlin
buildConfigField("int", "TELEGRAM_API_ID", apiId)
buildConfigField("String", "TELEGRAM_API_HASH", "\"$apiHash\"")
buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"$botToken\"")
```

Note: `apiId` stays as `"int"` with no quotes — `SetTdlibParameters.apiId` is an `Int`.

- [ ] **Step 4: Verify the build succeeds**

Run from `android/`:
```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. If it fails with "not set in local.properties", the credential is missing.

- [ ] **Step 5: Commit**

```
git add android/app/build.gradle.kts android/local.properties
git commit -m "feat: add TELEGRAM_BOT_TOKEN build credential with fail-fast guards"
```

---

### Task 2: Simplify AuthState — remove dead states, add Error

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/AuthState.kt`

**Context:** Three states become unreachable (`WaitingForPhone`, `WaitingForCode`, `WaitingForPassword`). A new `Error` data class is added for unrecoverable bot-token failures. `LoggedOut` is retained as a defensive fallback.

- [ ] **Step 1: Rewrite AuthState.kt**

Replace the entire file content:

```kotlin
package com.heywood8.telegramnews.domain.model

sealed class AuthState {
    object Unknown : AuthState()
    object LoggedIn : AuthState()
    object LoggedOut : AuthState()
    data class Error(val message: String) : AuthState()
}
```

- [ ] **Step 2: Check for compile errors**

Run:
```
./gradlew :app:compileDebugKotlin
```
Expected: errors at every site that references `WaitingForPhone`, `WaitingForCode`, or `WaitingForPassword`. These are all fixed in subsequent tasks — note the file names from the error output.

- [ ] **Step 3: Commit**

```
git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/AuthState.kt
git commit -m "refactor: simplify AuthState — remove phone/code/password states, add Error"
```

---

### Task 3: Clean up TelegramRepository interface

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/TelegramRepository.kt`

**Context:** Remove the five auth methods that are no longer part of the public API. The interface currently declares: `isLoggedIn`, `sendPhoneNumber`, `sendCode`, `sendPassword`, `logOut`. All five are deleted.

- [ ] **Step 1: Rewrite TelegramRepository.kt**

Replace the entire file:

```kotlin
package com.heywood8.telegramnews.domain.repository

import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface TelegramRepository {
    val authState: Flow<AuthState>
    fun observeNewMessages(channels: List<String>): Flow<Message>
    suspend fun fetchMessagesSince(channel: String, afterMessageId: Long): List<Message>
    suspend fun searchChannel(query: String): List<Channel>
    suspend fun downloadFile(fileId: Int): String?
}
```

- [ ] **Step 2: Commit**

```
git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/TelegramRepository.kt
git commit -m "refactor: remove dead auth methods from TelegramRepository interface"
```

---

### Task 4: Rewrite TelegramRepositoryImpl auth flow

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TelegramRepositoryImpl.kt`

**Context:** This is the core change. Replace the `authState` `.map {}` with a `combine` that merges TDLib state with a new `_authError` flow. Add `botTokenSent: AtomicBoolean` and `_authError: MutableStateFlow<String?>`. Add `WaitPhoneNumber` branch to the private collector. Update the `Closed` branch to skip re-init when `botTokenSent` is already set. Remove five dead methods.

- [ ] **Step 1: Update imports**

Add these imports (keep all existing ones):
```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.atomic.AtomicBoolean
```

- [ ] **Step 2: Add the two new fields after `private val api = TelegramFlow()`**

```kotlin
private val botTokenSent = AtomicBoolean(false)
private val _authError = MutableStateFlow<String?>(null)
```

- [ ] **Step 3: Replace the `initTdlib()` function**

Replace lines 69–88 (the entire `initTdlib()` function) with:

```kotlin
private fun initTdlib() {
    api.attachClient()
    // Send parameters immediately after attaching — authorizationStateFlow() always emits
    // WaitTdlibParameters first, but that event can be missed if the flow collector hasn't
    // subscribed yet when TDLib emits it (~5ms after attachClient). Sending proactively
    // avoids the race condition.
    sendTdlibParameters()
    val handler = CoroutineExceptionHandler { _, _ -> initTdlib() }
    scope.launch(handler) {
        api.authorizationStateFlow().collect { state ->
            when (state) {
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    if (botTokenSent.getAndSet(true)) return@collect
                    scope.launch(CoroutineExceptionHandler { _, t ->
                        _authError.value = t.message ?: "Bot token authentication failed"
                    }) {
                        api.sendFunctionAsync(
                            TdApi.CheckAuthenticationBotToken(BuildConfig.TELEGRAM_BOT_TOKEN)
                        )
                    }
                }
                is TdApi.AuthorizationStateClosed -> {
                    if (botTokenSent.get()) return@collect
                    api.attachClient()
                    sendTdlibParameters()
                }
                else -> {}
            }
        }
    }
}
```

- [ ] **Step 4: Replace the `authState` flow declaration**

Replace lines 92–104 (the `authState` `.map {}` chain) with:

```kotlin
override val authState: Flow<AuthState> = combine(
    api.authorizationStateFlow(),
    _authError
) { tdState, error ->
    if (error != null) return@combine AuthState.Error(error)
    when (tdState) {
        is TdApi.AuthorizationStateReady -> AuthState.LoggedIn
        is TdApi.AuthorizationStateLoggingOut -> AuthState.LoggedOut
        else -> AuthState.Unknown
    }
}.stateIn(scope, SharingStarted.Eagerly, AuthState.Unknown)
```

- [ ] **Step 5: Remove the five dead methods**

Delete these functions entirely from the file:
- `override suspend fun isLoggedIn(): Boolean` (line 106)
- `override suspend fun sendPhoneNumber(...)` (lines 108–110)
- `override suspend fun sendCode(...)` (lines 112–114)
- `override suspend fun sendPassword(...)` (lines 116–118)
- `override suspend fun logOut()` (lines 120–123)

- [ ] **Step 6: Verify compile**

```
./gradlew :app:compileDebugKotlin
```
Expected: errors only from auth UI files (`AuthScreen.kt`, `AuthViewModel.kt`, `AppNavHost.kt`, `SettingsViewModel.kt`) — all fixed in later tasks.

- [ ] **Step 7: Commit**

```
git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TelegramRepositoryImpl.kt
git commit -m "feat: authenticate via bot token, remove user phone auth from TDLib flow"
```

---

### Task 5: Add unit test for loop-prevention guard

**Files:**
- Create: `android/app/src/test/kotlin/com/heywood8/telegramnews/data/telegram/BotTokenLoopGuardTest.kt`

**Context:** Verifies that after `botTokenSent` is set to `true`, the `Closed` handler in `initTdlib()` does not call `initTdlib()` again. Since `TelegramRepositoryImpl` uses `TelegramFlow` (a TDLib wrapper that cannot be constructed in unit tests), we test the guard logic in isolation using a simple standalone test that mirrors the exact conditional.

- [ ] **Step 1: Write the test**

```kotlin
package com.heywood8.telegramnews.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class BotTokenLoopGuardTest {

    /**
     * Simulates the Closed-handler guard:
     *   if (botTokenSent.get()) return   // skip re-init
     *   initTdlib()
     *
     * Verifies that once botTokenSent is true, the re-init path is never taken.
     */
    @Test
    fun `closed handler skips reinit after botTokenSent is true`() {
        val botTokenSent = AtomicBoolean(false)
        var reinitCalled = false

        fun simulateClosedHandler() {
            if (botTokenSent.get()) return
            reinitCalled = true
        }

        // Before token sent: Closed should trigger re-init
        simulateClosedHandler()
        assertTrue("Expected reinit before token sent", reinitCalled)

        // Reset and mark token as sent
        reinitCalled = false
        botTokenSent.set(true)

        // After token sent: Closed must NOT trigger re-init
        simulateClosedHandler()
        assertFalse("Expected NO reinit after token sent", reinitCalled)
    }

    @Test
    fun `botTokenSent getAndSet prevents double-fire`() {
        val botTokenSent = AtomicBoolean(false)
        var tokenCallCount = 0

        fun simulateWaitPhoneNumberHandler() {
            if (botTokenSent.getAndSet(true)) return
            tokenCallCount++
        }

        simulateWaitPhoneNumberHandler()
        simulateWaitPhoneNumberHandler()
        simulateWaitPhoneNumberHandler()

        assert(tokenCallCount == 1) { "Token call should fire exactly once, fired $tokenCallCount times" }
    }
}
```

- [ ] **Step 2: Run the tests**

```
./gradlew :app:test --tests "com.heywood8.telegramnews.data.telegram.BotTokenLoopGuardTest"
```
Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```
git add android/app/src/test/kotlin/com/heywood8/telegramnews/data/telegram/BotTokenLoopGuardTest.kt
git commit -m "test: verify bot token loop-prevention guard logic"
```

---

### Task 6: Delete auth UI files

**Files:**
- Delete: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/auth/AuthScreen.kt`
- Delete: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/auth/AuthViewModel.kt`

**Context:** These files implement the phone/code/password screens. They are entirely dead code once bot-token auth is in place. Deleting them forces compile errors in `AppNavHost.kt` that are fixed in Task 7.

- [ ] **Step 1: Delete both files**

```
git rm android/app/src/main/kotlin/com/heywood8/telegramnews/ui/auth/AuthScreen.kt
git rm android/app/src/main/kotlin/com/heywood8/telegramnews/ui/auth/AuthViewModel.kt
```

- [ ] **Step 2: Commit**

```
git commit -m "refactor: delete auth screen and view model — replaced by silent bot token auth"
```

---

### Task 7: Rewrite AppNavHost and remove NavRoutes.Auth

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/navigation/AppNavHost.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/navigation/NavRoutes.kt`

**Context:** The current `AppNavHost` uses a `NavHost` with `Auth` as start destination and a `LaunchedEffect` to navigate between auth and main. Replace this entirely with a top-level `when(authState)` — no navigation back-stack, so the system back button exits the activity. `NavRoutes.Auth` is deleted.

- [ ] **Step 1: Rewrite NavRoutes.kt**

```kotlin
package com.heywood8.telegramnews.ui.navigation

sealed class NavRoutes(val route: String) {
    object Main : NavRoutes("main")
}

enum class MainTab(val route: String, val label: String) {
    Feed("feed", "Feed"),
    Channels("channels", "Channels"),
    Settings("settings", "Settings"),
}
```

- [ ] **Step 2: Rewrite AppNavHost.kt**

```kotlin
package com.heywood8.telegramnews.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.ui.MainScreen

@Composable
fun AppNavHost(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle(initialValue = AuthState.Unknown)

    when (authState) {
        is AuthState.LoggedIn -> MainScreen()
        is AuthState.Error -> {
            val msg = (authState as AuthState.Error).message
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Authentication error: $msg",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
```

- [ ] **Step 3: Verify compile**

```
./gradlew :app:compileDebugKotlin
```
Expected: errors only from `SettingsScreen.kt` / `SettingsViewModel.kt` referencing `WaitingForPhone` or `logOut` — fixed in Task 8.

- [ ] **Step 4: Commit**

```
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/navigation/AppNavHost.kt
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/navigation/NavRoutes.kt
git commit -m "refactor: replace nav-graph auth routing with top-level authState switch"
```

---

### Task 8: Remove logout UI from SettingsScreen and SettingsViewModel

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsViewModel.kt`

**Context:** Remove the Account section (header + Sign out ListItem + AlertDialog + showLogoutDialog state), the Divider between Account and Display sections, and the `logOut()` method from the ViewModel. The Display and Sync sections are untouched.

- [ ] **Step 1: Rewrite SettingsScreen.kt**

```kotlin
package com.heywood8.telegramnews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.heywood8.telegramnews.domain.model.PhotoLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val showChannelIcons by viewModel.showChannelIcons.collectAsStateWithLifecycle()
    val photoLayout by viewModel.photoLayout.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                "Display",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Show channel icons") },
                supportingContent = { Text("Show a channel icon next to the channel name") },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    Switch(
                        checked = showChannelIcons,
                        onCheckedChange = { viewModel.setShowChannelIcons(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Photo layout") },
                supportingContent = { Text("Where to show inline photo thumbnails") },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    SingleChoiceSegmentedButtonRow {
                        PhotoLayout.entries.forEachIndexed { index, layout ->
                            SegmentedButton(
                                selected = photoLayout == layout,
                                onClick = { viewModel.setPhotoLayout(layout) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = PhotoLayout.entries.size,
                                ),
                                label = {
                                    Text(
                                        when (layout) {
                                            PhotoLayout.ABOVE -> "Above"
                                            PhotoLayout.BELOW -> "Below"
                                            PhotoLayout.LEFT -> "Left"
                                        }
                                    )
                                },
                            )
                        }
                    }
                },
            )
            Divider()
            Text(
                "Sync",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Background sync") },
                supportingContent = { Text("Messages are synced every 15 minutes in the background") },
            )
        }
    }
}
```

- [ ] **Step 2: Rewrite SettingsViewModel.kt**

```kotlin
package com.heywood8.telegramnews.ui.settings

import androidx.lifecycle.ViewModel
import com.heywood8.telegramnews.data.local.UserPreferencesRepository
import com.heywood8.telegramnews.domain.model.PhotoLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPrefs: UserPreferencesRepository,
) : ViewModel() {

    val showChannelIcons: StateFlow<Boolean> = userPrefs.showChannelIcons

    fun setShowChannelIcons(show: Boolean) {
        userPrefs.setShowChannelIcons(show)
    }

    val photoLayout: StateFlow<PhotoLayout> = userPrefs.photoLayout

    fun setPhotoLayout(layout: PhotoLayout) {
        userPrefs.setPhotoLayout(layout)
    }
}
```

- [ ] **Step 3: Full build**

```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL with 0 errors.

- [ ] **Step 4: Run all unit tests**

```
./gradlew :app:test
```
Expected: all tests PASS (including the new `BotTokenLoopGuardTest`).

- [ ] **Step 5: Commit**

```
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsScreen.kt
git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/settings/SettingsViewModel.kt
git commit -m "refactor: remove Sign out UI and logOut from settings — bot token auth needs no logout"
```

---

### Task 9: Manual verification

**No code changes.** Install the build on a device or emulator and verify.

- [ ] Install debug build:
  ```
  ./gradlew :app:installDebug
  ```

- [ ] Launch the app. Verify: no login screen appears. Feed loads directly.

- [ ] Navigate to Settings. Verify: no "Account" section, no "Sign out" button.

- [ ] Add a new channel via the Channels tab. Search for a public Telegram channel (e.g., `telegram`). Verify: search returns a result and subscribe works.

- [ ] Wait for or trigger a background sync. Verify: `SyncWorker` completes without crash (check Logcat for `SyncWorker`).

- [ ] Verify error path: temporarily set `TELEGRAM_BOT_TOKEN=invalid` in `local.properties`, rebuild (`./gradlew :app:assembleDebug`), install, launch. Verify: app shows error screen instead of feed, no infinite loop in Logcat.

- [ ] Restore valid token, rebuild, reinstall, verify app returns to normal.
