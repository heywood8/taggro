# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Shell Command Rules
- Run each command separately.
- Never use command substitution (`$(...)`) in any shell commands.
- Never use compound commands or pipes: no `&&`, `||`, `;`, or `|`.

## Git Commits
- Pass commit messages directly with `-m "..."`. Never use heredoc or command substitution.
- Follow [Conventional Commits](https://www.conventionalcommits.org/) / Release Please format:
  - `feat: <description>` — new feature (triggers minor release)
  - `fix: <description>` — bug fix (triggers patch release)
  - `chore: <description>` — maintenance, deps, tooling (no release)
  - `docs: <description>` — documentation only
  - `refactor: <description>` — code change without feature/fix
  - `test: <description>` — adding or updating tests
  - `perf: <description>` — performance improvement
  - Add `!` after the type (e.g. `feat!:`) or a `BREAKING CHANGE:` footer for breaking changes (triggers major release)
  - Scope is optional: `feat(auth): support phone login`

## Build Commands

All Gradle commands run from `android/`:

```
./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:installDebug           # build + install on connected device/emulator
./gradlew :app:assembleRelease        # build signed release APK (requires keystore)
./gradlew :app:clean                  # clean build outputs (required after credential changes)
./gradlew :app:lint                   # run lint
./gradlew :app:test                   # run unit tests
./gradlew :app:connectedAndroidTest   # run instrumented tests
```

Credentials go in `android/local.properties` (gitignored):
```
TELEGRAM_API_ID=12345
TELEGRAM_API_HASH=abc123...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
RELEASE_STORE_PASSWORD=...
```

## Architecture

Single-module Android app (Kotlin, API 31+, Jetpack Compose + Material3).

### Layer structure
- **`ui/`** — Compose screens + HiltViewModels per feature (`auth/`, `feed/`, `channels/`, `subscriptions/`)
- **`domain/`** — interfaces (`repository/`), models (`model/`), use cases (`usecase/`)
- **`data/`** — implementations: `telegram/TelegramRepositoryImpl`, `local/` (Room DB)
- **`di/`** — Hilt modules (`DatabaseModule`, `RepositoryModule`)
- **`worker/`** — `SyncWorker` (WorkManager + Hilt background sync)

### Key dependencies
- **TDLib** via `td-ktx` 1.8.56 — wraps TDLib native library for Telegram API access
- **Room 2.8.4** — local message persistence, DB version 2 with `MIGRATION_1_2`
- **Hilt 2.56** — DI throughout, including `HiltWorker` for WorkManager
- **WorkManager** — periodic background sync; default initializer disabled in manifest, custom init in `TelegramNewsApplication`

### TDLib critical gotcha
**Never use td-ktx extension functions** (`checkAuthenticationCode`, `searchPublicChat`, `getChatHistory`, `getSupergroup`, `logOut`, `setTdlibParameters`, etc.). They use `sendFunctionLaunch` internally, which fires exceptions on TDLib's `ResponseReceiver` thread — these **escape all try/catch blocks** and crash the app.

Always call TDLib via:
```kotlin
api.sendFunctionAsync(TdApi.CheckAuthenticationCode(code))
api.sendFunctionAsync(TdApi.SearchPublicChat(username))
// etc.
```

Add `CoroutineExceptionHandler` to every `viewModelScope.launch {}` that calls TDLib.

### Auth flow
`TelegramRepositoryImpl.initTdlib()` attaches the TDLib client and collects `authorizationStateFlow`. The collector uses a `CoroutineExceptionHandler` that calls `initTdlib()` again if the collector dies. Auth states map to `AuthState` sealed class; `AuthState.Unknown` means TDLib not yet ready — show spinner, not the phone input.

### Feed persistence
Messages are stored in Room (`messages` table). `FeedViewModel` observes `messageDao.observeAll()` for live updates. On startup, recent messages are fetched via `telegramRepo.fetchMessagesSince(channel, 0)`. Real-time messages arrive via `telegramRepo.observeNewMessages(channels)` and are saved to DB immediately.

### Message text cleaning
`TelegramRepositoryImpl.cleanText()` strips trailing emoji-only lines (channel signatures) and removes emojis via `stripEmojis()` regex covering surrogate pairs, misc symbols, variation selectors, and zero-width joiners.
