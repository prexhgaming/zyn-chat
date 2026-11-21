# AGENTS.md

Guidance for AI coding assistants working in this repository.

## Project

**Zyn Chat** — Android app (Kotlin + Jetpack Compose), applicationId/namespace `com.zyn.chat`.
Build system: **Gradle Kotlin DSL only** (`.kts`). Never add Groovy `*.gradle` build files.

Derived from `GetStream/stream-chat-android-ai`, re-namespaced to `com.zyn.chat.*`. Upstream
Stream license headers stay in place (see `LICENSE`).

## Modules

- `zyn-chat-compose` (`com.zyn.chat.compose`) — framework-agnostic Compose UI components:
  `StreamingText`, `AITypingIndicator`, `ChatComposer`, `SpeechToTextButton`, AI message parts.
  No Stream Chat, networking or ViewModel dependencies.
- `zyn-chat-ondevice` (`com.zyn.chat.ondevice`) — on-device model integration (ML Kit GenAI).
- `zyn-chat-app` (`com.zyn.chat.app`) — application module: chat UI, ViewModels, Retrofit layer,
  Stream Chat client wiring.

## UI layer (`zyn-chat-app`)

- Design tokens live in `ui/theme`: `Color.kt` (palettes), `Typography.kt` (type scale, also used for
  Markdown heading mapping), `Shape.kt` (radii). Change colours/type there, not inline.
- **The accent is a token, never a literal colour**: `ui/theme/Accent.kt` exposes
  `MaterialTheme.accent` (`primary`, `onPrimary`, `tint`, `border`). It falls back to the colour
  scheme's primary (the app's blue). Selection states, the send button and the model picker read
  these tokens, so a future settings screen swaps the accent via `AppTheme(accent = ...)` without
  touching components. Do not hardcode an accent colour anywhere.
- Icons are `ImageVector`s in `ui/icons/ZynIcons.kt` (shared 24x24 viewport, 1.7dp stroke), not
  drawable resources. The app's older `res/drawable/ic_*` icons are the Material-Symbols ones the
  top bar still uses.
- The app owns its composer: `ui/chat/ZynComposer.kt` + `ComposerControls.kt` + `SmartSendButton.kt`
  + `ModelSelector.kt`. The library's `ChatComposer` and `SpeechToTextButton` are intentionally left
  untouched (and unused by the app), so the `zyn-chat-compose` Paparazzi snapshots keep matching —
  don't restyle library components to change the app's look.
- Dictation is the app's own `ui/chat/speech/SpeechInput.kt` state machine (idle / listening /
  processing / error / unavailable). It writes recognised text into the field and **never** sends;
  only `SmartSendButton` sends. Release it via `rememberSpeechInput()`'s `DisposableEffect`.
- `SmartSendButton` = mic when empty, send when there is text, stop while generating, stop/cancel
  while dictating. Long press dictates, and a long press can never fall through into a send.
- Model picker: `ui/chat/ModelPickerSheet.kt` (Material `ModalBottomSheet`, drag handle, provider
  tabs, per-model rows, empty state). The drawer is a `ModalNavigationDrawer` with a partial-width
  sheet, animated scrim and an edge-swipe strip in `ui/AiChatApp.kt`.
- Drawer: `ui/components/ChatDrawer.kt` with `DrawerNavItem.kt` and `DrawerConversationItem.kt`.
- User-facing text goes through `res/values/strings.xml` (names prefixed `zyn_`).
- Provider and model state: `data/providers/` (`AiProvider`, `AiModel`, `ProvidersState`,
  `ProviderRepository` — SharedPreferences-backed selection) and
  `presentation/providers/ProvidersViewModel.kt`. **Providers start empty by design**: nothing may
  invent a provider or a model list. Integrations call `registerProvider` / `setProviderEnabled` /
  `removeProvider` once they exist.
- The drawer's "Providers" entry and the "Providers" action in the model picker header both call the
  single `onManageProviders` action hoisted in `ui/AiChatApp.kt` (currently a no-op) — never add a
  second provider-management path or screen.
- **Presentation only for now**: routing, the drawer search action, the profile row.

## Commands

```bash
./gradlew :zyn-chat-app:assembleRelease    # what CI builds: the signed release APK
./gradlew :zyn-chat-app:assembleDebug      # local development only
./gradlew :zyn-chat-compose:testDebugUnitTest :zyn-chat-ondevice:testDebugUnitTest
./gradlew lint detekt                      # static analysis
```

Toolchain: JDK 17, compileSdk 36, minSdk 23 (app and ondevice: 26).

## Build & test notes

- The release build emits **one universal APK** (`zyn-chat-app/build/outputs/apk/release/`) —
  ABI splits were removed because the app has almost no native code. CI builds and uploads that
  single release APK (`assembleRelease`); there is no debug APK job.
- There is **no Android SDK in the dev sandbox**: compilation, tests and APK builds only run in
  CI (`.github/workflows/ci.yml`) or on a machine with the SDK. Never claim a local build passed.
- `zyn-chat-compose` runs Paparazzi + Robolectric snapshot tests; they cannot share a JVM, hence
  `tasks.withType<Test> { forkEvery = 1 }` — do not remove.
- `zyn-chat-compose` / `zyn-chat-ondevice` compile with `-Xexplicit-api=strict`: new public
  declarations need explicit visibility and return types.
- Paparazzi snapshot filenames encode the test package (`com.zyn.chat.compose...`) — renaming
  packages means renaming those files too.
- detekt `InvalidPackageDeclaration.rootPackage` is `com.zyn`.

## Release signing

The keystore is **never committed** (`*.jks`, `signing.properties`, `.secrets/` are gitignored).

- CI (GitHub repo secrets): `ANDROID_KEYSTORE_BASE64` (base64 of the JKS), `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. The release job restores the keystore to
  `keystore.jks` **before** Gradle runs and fails fast if the secret is missing.
- Local signed release builds: drop `signing.properties` + the `.jks` in the repository root.
- Environment variables take precedence over `signing.properties`; without either, release falls
  back to the debug signing config so builds still work.

## Scope

Setup and UI/UX redesign are done; the drawer, composer, dictation and model picker are real UI.
BYOK key management, AI provider integrations and request routing are **not implemented yet**: the
provider/model architecture, persistence and empty states exist, but nothing registers a provider.
Do not invent providers, models or backend behaviour.
