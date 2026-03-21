# Bot Token Authentication — Design Spec

**Date:** 2026-03-21
**Status:** Approved

## Problem

The app currently requires users to authenticate via Telegram phone number, verification code, and optional 2FA password. This is friction for a first-time user who only wants to read public news channels. All content the app reads is from public Telegram channels, which do not require a user account.

## Goal

Remove the user login flow entirely. The app authenticates silently using a Telegram bot token at startup and goes straight to the feed.

## Non-Goals

- Private channel support (requires user auth; out of scope)
- Runtime bot token management (token is a build-time constant)
- Backend/server migration (deferred; this is Option 1 of a two-option analysis)

## Design

### 1. Credentials

Add `TELEGRAM_BOT_TOKEN` to `android/local.properties` (gitignored):

```
TELEGRAM_BOT_TOKEN=123456:ABC-your-bot-token-here
```

Update `app/build.gradle.kts` to inject the token and add explicit build-time failure for all three credentials (removing the current silent-default behaviour):

```kotlin
val apiId = localProperties.getProperty("TELEGRAM_API_ID")
    ?: error("TELEGRAM_API_ID not set in local.properties")
val apiHash = localProperties.getProperty("TELEGRAM_API_HASH")
    ?: error("TELEGRAM_API_HASH not set in local.properties")
val botToken = localProperties.getProperty("TELEGRAM_BOT_TOKEN")
    ?: error("TELEGRAM_BOT_TOKEN not set in local.properties")

buildConfigField("int", "TELEGRAM_API_ID", apiId)          // int, not quoted
buildConfigField("String", "TELEGRAM_API_HASH", "\"$apiHash\"")
buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"$botToken\"")
```

### 2. TDLib Auth Flow (`TelegramRepositoryImpl`)

`TelegramRepositoryImpl` has two concurrent collectors:

1. **`initTdlib()` private collector** — handles `AuthorizationStateClosed` and `AuthorizationStateLoggingOut` (see §3). This is where auth side effects must live.
2. **`authState` mapped flow** — a synchronous `.map {}` transform; cannot call `suspend` functions. Maps TDLib states to `AuthState` values only.

**New field:**

```kotlin
private val botTokenSent = AtomicBoolean(false)
private val _authError = MutableStateFlow<String?>(null)
```

**`authState` exposed flow** is updated to `combine` the TDLib state with `_authError`:

```kotlin
val authState: StateFlow<AuthState> = combine(
    api.authorizationStateFlow(),
    _authError
) { tdState, error ->
    if (error != null) return@combine AuthState.Error(error)
    when (tdState) {
        is TdApi.AuthorizationStateReady         -> AuthState.LoggedIn
        is TdApi.AuthorizationStateWaitPhoneNumber -> AuthState.Unknown   // transitional
        is TdApi.AuthorizationStateClosed        -> AuthState.Unknown
        is TdApi.AuthorizationStateLoggingOut    -> AuthState.LoggedOut   // retained defensively
        else                                      -> AuthState.Unknown
    }
}.stateIn(scope, SharingStarted.Eagerly, AuthState.Unknown)
```

`_authError` takes priority: any non-null error value immediately overrides TDLib state.

**Private collector in `initTdlib()`** — add `WaitPhoneNumber` branch:

```kotlin
is TdApi.AuthorizationStateWaitPhoneNumber -> {
    if (botTokenSent.getAndSet(true)) return@collect   // guard: fire once only
    scope.launch(CoroutineExceptionHandler { _, t ->
        _authError.value = t.message ?: "Bot token authentication failed"
    }) {
        api.sendFunctionAsync(TdApi.CheckAuthenticationBotToken(BuildConfig.TELEGRAM_BOT_TOKEN))
    }
}

is TdApi.AuthorizationStateClosed -> {
    if (botTokenSent.get()) return@collect   // token already sent; do not re-init after failure
    api.attachClient()
    sendTdlibParameters()
}
```

**Loop prevention:** `botTokenSent` is set to `true` before the token call fires. The `Closed` handler checks `botTokenSent.get()` — a single, race-free guard — and skips re-init if the token was already sent. This is simpler than relying on `_authError`, which could have a timing dependency.

### 3. AuthState Simplification

Remove three states that are now unreachable:
- `AuthState.WaitingForPhone`
- `AuthState.WaitingForCode`
- `AuthState.WaitingForPassword`

Add one new state:
- `AuthState.Error(message: String)` — unrecoverable auth failure (bad bot token)

Retained states:
- `AuthState.Unknown` — TDLib not yet ready; show loading spinner
- `AuthState.LoggedIn` — authenticated; show feed
- `AuthState.LoggedOut` — retained defensively in the `authState` map for `AuthorizationStateLoggingOut`; shown as spinner. With `logOut()` removed, this should not occur in normal operation.

The private collector's `LoggingOut` handler is removed (was a no-op already). The `LoggingOut` branch in the `authState` combine remains, mapping to `AuthState.LoggedOut` as a defensive fallback.

### 4. Auth UI Removal

Delete:
- `ui/auth/AuthScreen.kt`
- `ui/auth/AuthViewModel.kt`

Update `ui/navigation/AppNavHost.kt`. The new top-level composable handles auth state directly — not via navigation — so there is no `Auth` back-stack entry and the system back button exits the activity:

```
AppNavHost root (top-level when, not a NavHost destination):
  Error              → FullScreenErrorMessage(message)   // no retry, back exits app
  Unknown, LoggedOut → FullScreenLoadingSpinner()
  LoggedIn           → MainNavGraph (feed, channels, settings, subscriptions)
```

`NavRoutes.Auth` is deleted from `NavRoutes.kt`.

### 5. Settings Screen — Sign Out Removal

Remove from `SettingsScreen`:
- The `var showLogoutDialog by remember { mutableStateOf(false) }` state variable
- The "Sign out" `ListItem`
- The `AlertDialog` block driven by `showLogoutDialog`
- The `"Account"` section header label
- The `Divider()` between the Account and Display sections (line ~62 — the one immediately following the Sign Out `ListItem`, not the one between Display and Sync)

Remove from `SettingsViewModel`:
- `logOut()` method

### 6. TelegramRepository Interface Cleanup

Remove from `domain/repository/TelegramRepository.kt`:
- `sendPhoneNumber(phone: String)`
- `sendCode(code: String)`
- `sendPassword(password: String)`
- `logOut()`
- `isLoggedIn()` (no remaining callers)

Remove corresponding implementations from `TelegramRepositoryImpl`.

### 7. Channel Access Behaviour

| Operation | Works without bot membership? |
|---|---|
| `SearchPublicChat` | Yes |
| `GetChatHistory` (public channels) | Yes |
| `UpdateNewMessage` | Only if bot is a member |
| `DownloadFile` | Yes |

Real-time `UpdateNewMessage` updates will not arrive for channels where the bot is not a member. The existing `SyncWorker` polling (every 15 min via `GetChatHistory`) provides coverage for all subscribed public channels regardless of membership.

If real-time updates are desired for a channel, the bot must be added as a subscriber — operational concern, not a code change.

## Files Changed

| File | Action |
|---|---|
| `android/local.properties` | Add `TELEGRAM_BOT_TOKEN` |
| `android/app/build.gradle.kts` | Add `TELEGRAM_BOT_TOKEN` field; add `error()` guard for all 3 credentials |
| `data/telegram/TelegramRepositoryImpl.kt` | Add `botTokenSent`, `_authError`; update `authState` to `combine`; add `WaitPhoneNumber` branch; update `Closed` handler; remove `sendPhoneNumber`, `sendCode`, `sendPassword`, `logOut`, `isLoggedIn`; remove `LoggingOut` handler |
| `domain/repository/TelegramRepository.kt` | Remove `sendPhoneNumber`, `sendCode`, `sendPassword`, `logOut`, `isLoggedIn` |
| `domain/model/AuthState.kt` | Remove 3 unreachable states; add `Error(message)` |
| `ui/auth/AuthScreen.kt` | Delete |
| `ui/auth/AuthViewModel.kt` | Delete |
| `ui/navigation/AppNavHost.kt` | Replace nav-graph auth routing with top-level `when(authState)` |
| `ui/navigation/NavRoutes.kt` | Remove `Auth` route |
| `ui/settings/SettingsScreen.kt` | Remove logout dialog, `showLogoutDialog`, Sign out `ListItem`, Account header, and the Divider between Account and Display sections |
| `ui/settings/SettingsViewModel.kt` | Remove `logOut()` |

## Testing

Manual verification:
- Build fails with a clear `error()` message when any of the three credentials is absent from `local.properties`
- Build succeeds with all credentials present
- App launches and goes directly to feed without showing any login screen
- Feed loads and displays messages from subscribed channels
- Adding a new public channel (search + subscribe) works correctly
- `SyncWorker` completes a sync cycle without errors
- Settings screen no longer shows a Sign out button or Account section
- App shows a permanent error screen (back press exits; no retry) when built with `TELEGRAM_BOT_TOKEN=invalid` — requires a dedicated test build

Unit test (new):
- Verify that after `botTokenSent` is set to `true`, subsequent `AuthorizationStateClosed` events in the private collector do not call `initTdlib()` again (loop prevention guard)
