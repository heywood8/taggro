# Changelog

## [0.11.2](https://github.com/heywood8/taggro/compare/v0.11.1...v0.11.2) (2026-03-18)


### Bug Fixes

* photo toggle controls photo visibility, show caption when photos disabled ([#36](https://github.com/heywood8/taggro/issues/36)) ([4642df6](https://github.com/heywood8/taggro/commit/4642df62b7a41dcbb15dc79ed1adb9797051b57f))

## [0.11.1](https://github.com/heywood8/taggro/compare/v0.11.0...v0.11.1) (2026-03-18)


### Bug Fixes

* proactively send TdlibParameters to avoid race condition on init ([#34](https://github.com/heywood8/taggro/issues/34)) ([0423977](https://github.com/heywood8/taggro/commit/042397724928ac627837e8e02fb76c1facde29e5))

## [0.11.0](https://github.com/heywood8/taggro/compare/v0.10.1...v0.11.0) (2026-03-18)


### Features

* inline photo display in feed cards and       article sheet      ([#32](https://github.com/heywood8/taggro/issues/32)) ([d60876e](https://github.com/heywood8/taggro/commit/d60876e5fd88a5fc9882f89fa474823e7b38c197))

## [0.10.1](https://github.com/heywood8/taggro/compare/v0.10.0...v0.10.1) (2026-03-17)


### Bug Fixes

* exclude release please branch from ci ([#29](https://github.com/heywood8/taggro/issues/29)) ([71dab76](https://github.com/heywood8/taggro/commit/71dab76ea8a319d6a9417b75cbadc69a9bf6315e))

## [0.10.0](https://github.com/heywood8/taggro/compare/v0.9.0...v0.10.0) (2026-03-17)


### Features

* per channel photo filter (include/exclude photo-only messages) ([#28](https://github.com/heywood8/taggro/issues/28)) ([e968f81](https://github.com/heywood8/taggro/commit/e968f8183924203e508fc67853631417ea6103e4))

## [0.9.0](https://github.com/heywood8/taggro/compare/v0.8.0...v0.9.0) (2026-03-17)


### Features

* replace card dimming with separator; mark older messages read on dwell ([#26](https://github.com/heywood8/taggro/issues/26)) ([b0bd7d7](https://github.com/heywood8/taggro/commit/b0bd7d795ad573e39d0080e9b33e1ee0b36eeced))

## [0.8.0](https://github.com/heywood8/taggro/compare/v0.7.1...v0.8.0) (2026-03-17)


### Features

* replace card dimming with separator; mark older messages read on dwell ([#24](https://github.com/heywood8/taggro/issues/24)) ([f78e149](https://github.com/heywood8/taggro/commit/f78e149e2f2b6d55954964d0dfb72f54b41573b4))

## [0.7.1](https://github.com/heywood8/taggro/compare/v0.7.0...v0.7.1) (2026-03-17)


### Bug Fixes

* long-press on filter chips now works — use awaitFirstDown(requireUnconsumed=false) ([5e78db9](https://github.com/heywood8/taggro/commit/5e78db96b03d79177fa0f33bdd4b6450bbb39a30))

## [0.7.0](https://github.com/heywood8/taggro/compare/v0.6.0...v0.7.0) (2026-03-17)


### Features

* add isRead to Message model and wire read-state flows in FeedViewModel ([a10a2f8](https://github.com/heywood8/taggro/commit/a10a2f836d1b8cb0dd6abb551ca12d0bb824adfe))
* add read_messages table, DAO, and DB migration v3 ([b56ce31](https://github.com/heywood8/taggro/commit/b56ce31f67f5a2a2dd5531a60bedd5080d6a179c))
* add read/unread UI — dwell detection, card dimming, chip badges, long-press mark read ([83c4926](https://github.com/heywood8/taggro/commit/83c4926d2d07e1074d79f3fc6a6653f8932af86c))


### Bug Fixes

* thread-safe timestamp formatting, correct LaunchedEffect key, fix test MessageEntity constructors ([a240a63](https://github.com/heywood8/taggro/commit/a240a6373a9d5eb8ce626ec4e2f545bfcb3ce0b3))


### Documentation

* add read/unread tracking design spec ([a7025ba](https://github.com/heywood8/taggro/commit/a7025baed7cfeb49fbe684608bd0f8758a3de214))
* add read/unread tracking implementation plan ([b855d17](https://github.com/heywood8/taggro/commit/b855d17b3b2c07a056ec4e6ebbb4bb780d02a09c))
* fix read/unread spec — correct Flow type and add total unread query ([bfbf315](https://github.com/heywood8/taggro/commit/bfbf315e22cd4a99c6962fc56e4a43f16ae3c996))

## [0.6.0](https://github.com/heywood8/taggro/compare/v0.5.1...v0.6.0) (2026-03-17)


### Features

* add pull-to-refresh on feed screen ([88fdc29](https://github.com/heywood8/taggro/commit/88fdc297639e8f6c6706e85e6006872456fc5e56))

## [0.5.1](https://github.com/heywood8/taggro/compare/v0.5.0...v0.5.1) (2026-03-17)


### Documentation

* add release-please conventional commit instructions to CLAUDE.md ([#15](https://github.com/heywood8/taggro/issues/15)) ([023de9a](https://github.com/heywood8/taggro/commit/023de9a0e9737e6863f6e7792be81c1f1ad0e6c9))

## [0.5.0](https://github.com/heywood8/taggro/compare/v0.4.1...v0.5.0) (2026-03-16)


### Features

* toggle channel icons ([5662f37](https://github.com/heywood8/taggro/commit/5662f37728c43a54ded27f211a876c76e681a6f7))


### Miscellaneous Chores

* trigger release ([#12](https://github.com/heywood8/taggro/issues/12)) ([feee541](https://github.com/heywood8/taggro/commit/feee541ba20ea1598e142e1d1ac6ba19e8c154e7))

## [0.4.1](https://github.com/heywood8/taggro/compare/v0.4.0...v0.4.1) (2026-03-16)


### Bug Fixes

* restrict APK to arm64-v8a only ([#9](https://github.com/heywood8/taggro/issues/9)) ([cc0f68b](https://github.com/heywood8/taggro/commit/cc0f68bcc8334568372fbc98f84847d9d4ece019))

## [0.4.0](https://github.com/heywood8/taggro/compare/v0.3.3...v0.4.0) (2026-03-16)


### Features

* truncate feed items to 6 lines, tap to open full article sheet ([#7](https://github.com/heywood8/taggro/issues/7)) ([edbdc27](https://github.com/heywood8/taggro/commit/edbdc27c6838058dec4a7f9f4c36a81cbdf09c65))

## [0.3.3](https://github.com/heywood8/taggro/compare/v0.3.2...v0.3.3) (2026-03-16)


### Bug Fixes

* strip supplementary-plane emoji and channel signature from messages ([b30fccd](https://github.com/heywood8/taggro/commit/b30fccd21df04a0e0874186349aad3347c3ac74b))

## [0.3.2](https://github.com/heywood8/taggro/compare/v0.3.1...v0.3.2) (2026-03-16)


### Documentation

* expand project instructions with build commands and architecture overview ([77998fa](https://github.com/heywood8/taggro/commit/77998fa8bce0d0634ab07338655ee6626289ae2f))

## [0.3.1](https://github.com/heywood8/taggro/compare/v0.3.0...v0.3.1) (2026-03-16)


### Miscellaneous Chores

* remove Python bot code, keep Android app only ([13600c8](https://github.com/heywood8/taggro/commit/13600c8293232d48b56d6c885e823844341645e1))

## [0.3.0](https://github.com/heywood8/taggro/compare/v0.2.0...v0.3.0) (2026-03-16)


### Features

* persist feed to Room, fetch on load, strip emojis, fix TDLib init ([709ff2a](https://github.com/heywood8/taggro/commit/709ff2a48ade0c0e384e6b302fdd435d44874bec))

## [0.2.0](https://github.com/heywood8/taggro/compare/v0.1.0...v0.2.0) (2026-03-16)


### Features

* Android project scaffold with Compose + Material3 + Hilt ([f037761](https://github.com/heywood8/taggro/commit/f0377618e5863336d8a16fddbed3f26718ccfc2e))
* app navigation scaffold with NavigationSuiteScaffold and auth gate ([90afe64](https://github.com/heywood8/taggro/commit/90afe643ba7a3038aec0d8a4f876e723fd565482))
* AuthScreen + AuthViewModel with 3-step phone/code/2FA flow ([a253acd](https://github.com/heywood8/taggro/commit/a253acd2dd1820414b84a356f21276537b8e5c16))
* complete app — Feed UI, Channel management, Settings, WorkManager sync, all tests passing ([bc60671](https://github.com/heywood8/taggro/commit/bc606712b7d0ab6eeab11bf5fd4e69253b9d53f9))
* domain models and repository interfaces ([fc72a5f](https://github.com/heywood8/taggro/commit/fc72a5f25e61bca1e2f5391a974225e9fe176471))
* FeedUseCase combining TDLib stream with per-subscription filtering ([0eddfa3](https://github.com/heywood8/taggro/commit/0eddfa3f419bb99e7088a6bd670e62fdfc51f0aa))
* FilterUseCase with full test coverage (ports filters.py) ([c5aa631](https://github.com/heywood8/taggro/commit/c5aa6319beb12afdeae53f326da61d5e3ed807bc))
* Hilt DI modules and LocalRepositoryImpl ([cb6d9ae](https://github.com/heywood8/taggro/commit/cb6d9ae2532416c4fe73dc38debce554bdd5b89e))
* implement TelegramRepositoryImpl with td-ktx auth + channel search + message streaming ([f5cf950](https://github.com/heywood8/taggro/commit/f5cf95036034adb0abe482110ad2f334ffca416f))
* Room database layer with entities, DAOs, and tests ([923ec88](https://github.com/heywood8/taggro/commit/923ec8800946e2d34b8e6a1f6ef7b09d4ccdcb8c))
* SubscriptionUseCase with mode/keyword management and tests ([32b7ddc](https://github.com/heywood8/taggro/commit/32b7ddceddefc3fda2532a40c898f5f168ac9afc))


### Bug Fixes

* correct build.gradle.kts quote escaping and theme parent for Compose ([624adb5](https://github.com/heywood8/taggro/commit/624adb5e58c3dc7e6bb6469dd87f598a72a616f4))
* handle TDLib async exceptions and add back navigation in auth ([59ea40a](https://github.com/heywood8/taggro/commit/59ea40a2c967056764d6927afe78cc6d7f0d7b64))
* improve DatabaseTest coverage and wire turbine to androidTest ([b2439f2](https://github.com/heywood8/taggro/commit/b2439f243344e92b38567f0a0a8cda8a1068267d))
* install gcc for tgcrypto compilation on arm64 ([f5d7ee9](https://github.com/heywood8/taggro/commit/f5d7ee9e8490a38d29b3b977756648d882cc19e1))
* produce latest tag on version tag pushes, remove extra branch tag rule ([ecd9d7b](https://github.com/heywood8/taggro/commit/ecd9d7b32a5ff7fb1f2d58d579fb4e9cfb15c747))
* show full message text in feed without truncation ([9265909](https://github.com/heywood8/taggro/commit/9265909a9bf5ff5fc8abcdbf7b8992601eb68de9))
* update compileSdk to 36, Gradle to 8.11.1, add tdktx dependency ([e898d4a](https://github.com/heywood8/taggro/commit/e898d4a059b297dfbcae6e0e8108886e9a67c2ca))
* upgrade Kotlin 2.2.21, KSP 2.2.21-2.0.5, Hilt 2.56, Room 2.8.4 for KSP2 compatibility ([ecfbdb2](https://github.com/heywood8/taggro/commit/ecfbdb2628dc74e80e4d7cce0107ecf7dca80bc3))
* use build-essential for tgcrypto compilation (needs libc6-dev) ([1fddd5a](https://github.com/heywood8/taggro/commit/1fddd5ad443e9e11924c5134041f099883c1a305))


### Documentation

* add Android redesign design document ([a64e66e](https://github.com/heywood8/taggro/commit/a64e66ebf089e8b228d518aeba7e57da23623b3e))
* add Android redesign implementation plan ([129c835](https://github.com/heywood8/taggro/commit/129c83581c1237afa3428def6ebd8121bbc8a6cb))
* add multi-arch Docker CI design doc ([90318f1](https://github.com/heywood8/taggro/commit/90318f12b4fe002e493e9c28fdac9220b7476c08))
* add multi-arch Docker CI implementation plan ([edb1050](https://github.com/heywood8/taggro/commit/edb1050ca8280679015a6993d5f48c4db934e28f))


### Miscellaneous Chores

* add release-please config ([4818139](https://github.com/heywood8/taggro/commit/48181394d8c93eb7d67b4e8bc4ad0b0f0da88eaa))
* temporarily remove tdktx dependency until TDLib setup in Task 7 ([ac58911](https://github.com/heywood8/taggro/commit/ac58911acfdf3368cd66b7bfd054b95b7b251bd2))


### Continuous Integration

* add CI, release APK build, and release-please workflows ([39eedb1](https://github.com/heywood8/taggro/commit/39eedb127fd7968d19d0d1921e170cc493026055))
* add multi-arch Docker build workflow for ghcr.io ([b6eacbe](https://github.com/heywood8/taggro/commit/b6eacbe42f07dcdd6e4da740ef541ab0d816977a))
* pin action SHAs and clarify latest tag condition ([93cd4b1](https://github.com/heywood8/taggro/commit/93cd4b11c6ccdf7a3b7f342131395c87ce1bd803))
