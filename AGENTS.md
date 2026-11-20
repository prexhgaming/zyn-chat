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

## Commands

```bash
./gradlew :zyn-chat-app:assembleDebug      # debug APKs
./gradlew :zyn-chat-app:assembleRelease    # release APKs (R8 + signing)
./gradlew :zyn-chat-compose:testDebugUnitTest :zyn-chat-ondevice:testDebugUnitTest
./gradlew lint detekt                      # static analysis
```

Toolchain: JDK 17, compileSdk 36, minSdk 23 (app and ondevice: 26).

## Build & test notes

- APKs are **split per ABI** (`armeabi-v7a`, `arm64-v8a`, `x86_64`) plus a universal APK;
  outputs land in `zyn-chat-app/build/outputs/apk/<variant>/`. CI uploads each split as a
  separate artifact — keep the per-ABI upload steps in sync if ABI list changes.
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

Phase 1 is project setup only. BYOK (bring-your-own-key) key management, AI provider integrations
and chat customization are **not implemented yet** — do not assume their design.
