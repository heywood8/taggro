# Android Redesign — Design

**Date:** 2026-03-16

## Overview

Redesign the existing Python Telegram bot as a standalone native Android application. The app reads public (and user-joined) Telegram channels directly via TDLib, applies per-channel keyword filtering, and presents messages in a unified news feed with a channel filter sidebar. No server, no bot token — the user logs in with their own Telegram account.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Platform | Native Kotlin (Android 12+, API 31) | Material You dynamic theming, modern APIs |
| Telegram access | TDLib via td-ktx | Official library, real-time updates, full history |
| Auth | User account (phone number) | Full channel access, on-device session |
| UI | Unified feed + channel filter drawer | Core value: aggregate multiple channels |
| Storage | Room (config + message cache) | SQLite ORM, offline reading, instant load |
| Min SDK | API 31 (Android 12) | Material You, ~85% device coverage |

## Architecture

Follows Google's recommended UDF-based layered architecture with three layers and unidirectional data flow.

```
┌──────────────────────────────────────────────┐
│              UI Layer                         │
│  Jetpack Compose screens                      │
│  ViewModels expose StateFlow<UiState>         │
│  Events flow UP → ViewModel                   │
│  State flows DOWN → Composables               │
└────────────────┬─────────────────────────────┘
                 │ StateFlow / sealed Events
┌────────────────▼─────────────────────────────┐
│           Domain Layer (Use Cases)            │
│  Pure Kotlin — no Android dependencies        │
│  FeedUseCase, SubscriptionUseCase             │
│  FilterUseCase (ports existing filters.py)    │
└────────────────┬─────────────────────────────┘
                 │
┌────────────────▼─────────────────────────────┐
│              Data Layer                       │
│                                               │
│  TelegramRepository    LocalRepository        │
│  (td-ktx / TDLib)      (Room DB)              │
└──────────────────────────────────────────────┘
```

**Key tech stack:**
- UI: Jetpack Compose + Material3 (Material Expressive, M3 1.4+)
- State: `StateFlow<UiState>` in ViewModels, UDF pattern
- TDLib wrapper: `td-ktx` (tdlibx/td-ktx) — Flows + suspend functions
- DI: Hilt
- Navigation: NavigationSuiteScaffold (adaptive) + Navigation Component
- Async: Coroutines + Flow throughout
- Background sync: WorkManager (15-min periodic task)

## Screens & Navigation

```
SplashScreen
    │
    ├── AuthScreen (phone → SMS code → optional 2FA)
    │
    └── MainScreen (NavigationSuiteScaffold)
            ├── FeedScreen  ◄── default destination
            │     ├── ModalNavigationDrawer (channel filter)
            │     └── ArticleDetailScreen
            └── SettingsScreen
                    └── ChannelSearchScreen
                            └── ChannelSettingsSheet (ModalBottomSheet)
```

**Adaptive navigation (NavigationSuiteScaffold):**
- Phone portrait → bottom navigation bar
- Tablet / landscape → navigation rail
- Large tablet → permanent navigation drawer

## UI Components

| Component | Material3 Component | Notes |
|---|---|---|
| Main nav | `NavigationSuiteScaffold` | Adaptive: bar / rail / drawer |
| Channel filter | `ModalNavigationDrawer` | Inside FeedScreen, filter icon triggers |
| Feed card | `ElevatedCard` | Material Expressive motion defaults |
| Channel settings | `ModalBottomSheet` | Mode selector + keyword chips |
| Auth steps | `Scaffold` + `LinearProgressIndicator` | 3-step: phone → code → 2FA |
| Channel search | `SearchBar` (M3) | Live search as user types |
| Keywords | `InputChip` / `FilterChip` | Removable, add via text field |

## Data Flow

```
TDLib (td-ktx Flow: updateNewMessage)
    │
    ▼
TelegramRepository
    │  filters to subscribed channels only
    ▼
FeedUseCase  ◄──── LocalRepository (subscriptions, keywords, mode)
    │  applies FilterUseCase (all / include / exclude)
    ▼
FeedViewModel (StateFlow<FeedUiState>)
    │
    ▼
FeedScreen (Compose LazyColumn)
```

**Message cache:** Room stores last 500 messages per channel. Feed loads from Room instantly on open; TDLib syncs in background.

**Background sync:** WorkManager periodic task wakes TDLib every 15 minutes when app is backgrounded, fetches missed messages, writes to Room cache.

## Database Schema (Room)

```
subscriptions (user_id*, channel*, mode, active)
keywords      (user_id*, channel*, keyword*)
messages      (id*, channel, text, timestamp, media_type, media_url)
last_seen     (channel*, message_id)
```

Mirrors the existing Python bot SQLite schema for conceptual consistency.

## Filtering Logic

Ports `filters.py` directly to Kotlin `FilterUseCase`:

| Mode | Behavior |
|---|---|
| `all` | Forward every message |
| `include` | Forward only if any keyword matches (case-insensitive substring) |
| `exclude` | Forward unless any keyword matches |

Mode can only be set to `include`/`exclude` when ≥1 keyword exists. Removing all keywords resets mode to `all`.

## Error Handling

| Scenario | Behavior |
|---|---|
| Phone number invalid | Inline error on AuthScreen |
| SMS code wrong / expired | Retry prompt, resend option after 60s |
| Network unavailable | Show cached messages, offline banner |
| Channel not found | Error snackbar, subscription not saved |
| TDLib session expired | Silent re-auth on next open |
| Message load failure | Retry button in feed |
| User revokes session externally | Redirect to AuthScreen |

## Project Structure

```
app/src/main/
├── data/
│   ├── local/          # Room DB, DAOs, entities, migrations
│   ├── telegram/       # TDLib client, TelegramRepository
│   └── model/          # Data-layer models
├── domain/
│   ├── usecase/        # FeedUseCase, FilterUseCase, SubscriptionUseCase
│   └── model/          # Domain models: Channel, Message, Subscription
├── ui/
│   ├── auth/           # AuthScreen, AuthViewModel
│   ├── feed/           # FeedScreen, FeedViewModel, FilterDrawer
│   ├── channels/       # ChannelSearchScreen, ChannelSettingsSheet
│   ├── settings/       # SettingsScreen
│   └── components/     # Shared composables: FeedItem, ChipRow
├── di/                 # Hilt modules (TelegramModule, DatabaseModule)
├── work/               # WorkManager background sync worker
└── MainActivity.kt
```

## Testing Strategy

- `domain/usecase/FilterUseCase` — JUnit 5 unit tests, ports all cases from `test_filters.py`
- `domain/usecase/FeedUseCase`, `SubscriptionUseCase` — JUnit 5 with fake repositories
- `data/local/` — Room in-memory DB tests (JUnit 4 + coroutines test)
- `ui/` — Compose UI tests with `ComposeTestRule` for key flows (auth, add channel, filter)
- `TelegramRepository` — unit tested with fake TDLib client

## Out of Scope (for now)

- iOS support
- Private channel access beyond what the user's account can already see
- Push notifications (WorkManager polling is sufficient for v1)
- Other news sources (RSS, etc.)
