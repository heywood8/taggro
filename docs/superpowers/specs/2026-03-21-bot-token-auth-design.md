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

Inject into `BuildConfig` in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "TELEGRAM_BOT_TOKEN", "\"$botToken\"")
```

Pattern matches existing `TELEGRAM_API_ID` / `TELEGRAM_API_HASH` handling.

### 2. TDLib Auth Flow (`TelegramRepositoryImpl`)

Current behavior when `AuthorizationStateWaitPhoneNumber` arrives: emit `AuthState.WaitingForPhone` and wait for user input.

New behavior: immediately call

```kotlin
api.sendFunctionAsync(TdApi.CheckAuthenticationBotToken(BuildConfig.TELEGRAM_BOT_TOKEN))
```

TDLib then transitions directly to `AuthorizationStateReady` → `AuthState.LoggedIn`. No user interaction required.

No other changes to the `authorizationStateFlow` collector.

### 3. AuthState Simplification

Remove three states that are now unreachable:
- `AuthState.WaitingForPhone`
- `AuthState.WaitingForCode`
- `AuthState.WaitingForPassword`

Retained states:
- `AuthState.Unknown` — TDLib not yet ready; show loading spinner
- `AuthState.LoggedIn` — authenticated; show feed
- `AuthState.LoggedOut` — TDLib signalled logout (edge case / error recovery)

### 4. Auth UI Removal

Delete:
- `ui/auth/AuthScreen.kt`
- `ui/auth/AuthViewModel.kt`

Update `ui/navigation/AppNavHost.kt`:
- Remove auth navigation destination
- On `AuthState.Unknown`: show a full-screen loading spinner
- On `AuthState.LoggedIn`: show main content (feed)
- On `AuthState.LoggedOut`: show an error/retry state (no login form)

### 5. Channel Access Behaviour

| Operation | Works without bot membership? |
|---|---|
| `SearchPublicChat` | Yes |
| `GetChatHistory` (public channels) | Yes |
| `UpdateNewMessage` | Only if bot is a member |
| `DownloadFile` | Yes |

Real-time `UpdateNewMessage` updates will not arrive for channels where the bot is not a member. The existing `SyncWorker` polling (every 15 min via `GetChatHistory`) provides coverage for all subscribed public channels regardless of membership. Behaviour is unchanged from the polling perspective.

If real-time updates are desired for a channel, the bot must be added to that channel as a subscriber — this is an operational concern, not a code change.

## Files Changed

| File | Action |
|---|---|
| `android/local.properties` | Add `TELEGRAM_BOT_TOKEN` |
| `android/app/build.gradle.kts` | Inject `BuildConfig.TELEGRAM_BOT_TOKEN` |
| `data/telegram/TelegramRepositoryImpl.kt` | Replace phone auth with bot token auth |
| `domain/model/AuthState.kt` | Remove 3 unreachable states |
| `ui/auth/AuthScreen.kt` | Delete |
| `ui/auth/AuthViewModel.kt` | Delete |
| `ui/navigation/AppNavHost.kt` | Remove auth destination, add spinner for Unknown |

## Testing

- Build succeeds with bot token in `local.properties`
- App launches without showing any login screen
- Feed loads and displays messages from subscribed channels
- Adding a new channel (search + subscribe) works correctly
- `SyncWorker` completes a sync cycle without errors
