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
- Icons are `ImageVector`s in `ui/icons/ZynIcons.kt` (shared 24x24 viewport, 1.7dp stroke), not
  drawable resources. The app's older `res/drawable/ic_*` icons are the Material-Symbols ones the
  top bar still uses.
- The app owns its composer: `ui/chat/ZynComposer.kt` + `ComposerControls.kt` + `ModelSelector.kt`.
  The library's `ChatComposer` is intentionally left untouched, so the `zyn-chat-compose` Paparazzi
  snapshots keep matching — don't restyle library components to change the app's look.
- Drawer: `ui/components/ChatDrawer.kt` with `DrawerNavItem.kt` and `DrawerConversationItem.kt`.
- User-facing text goes through `res/values/strings.xml` (names prefixed `zyn_`).
- **Presentation only for now**: the Providers/Routing drawer entries, the drawer search action and
  the composer's model selector render and report taps, but have no screens or wiring behind them.
  `ui/chat/ChatModel.kt` is a local model list, not a provider integration.

## Commands

```bash
./gradlew :zyn-chat-app:assembleRelease    # what CI builds: signed, split release APKs
./gradlew :zyn-chat-app:assembleDebug      # local development only
./gradlew :zyn-chat-compose:testDebugUnitTest :zyn-chat-ondevice:testDebugUnitTest
./gradlew lint detekt                      # static analysis
```

Toolchain: JDK 17, compileSdk 36, minSdk 23 (app and ondevice: 26).

## Build & test notes

- APKs are **split per ABI** (`armeabi-v7a`, `arm64-v8a`, `x86_64`) plus a universal APK;
  outputs land in `zyn-chat-app/build/outputs/apk/<variant>/`. CI builds and uploads **release
  APKs only** (`assembleRelease`) — there is no debug APK job; keep the per-ABI upload steps in
  sync if the ABI list changes.
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

Phase 1 was project setup; the current phase is UI/UX redesign. BYOK (bring-your-own-key) key
management, AI provider integrations and request routing are **not implemented yet** — the drawer
entries and model selector for them are visual only. Do not assume their design.
