# Zyn Chat

Android chat application for AI agents, built as a **BYOK (bring your own key)** client: users
add their own AI provider API keys and chat with different models.

> **Status: Phase 1 — project foundation only.** The app currently hosts the chat UI and a
> reference chat implementation. API key management, provider integrations and chat customization
> are **not implemented yet** and will be specified in a later phase.

## Attribution

The initial source code is derived from the open repository
[`GetStream/stream-chat-android-ai`](https://github.com/GetStream/stream-chat-android-ai)
(Stream Chat Android AI). Module and package names have been re-namespaced to `com.zyn.chat.*`
and the build has been converted to self-contained Gradle Kotlin DSL. Upstream copyright headers
and the terms in [`LICENSE`](LICENSE) (Stream License) remain in effect — review them before
distributing this application.

## Requirements

- JDK 17
- Android SDK with compileSdk 36 (`ANDROID_HOME` set, or `local.properties` with `sdk.dir`)

## Modules

| Module | Package | Role |
| --- | --- | --- |
| `zyn-chat-compose` | `com.zyn.chat.compose` | Framework-agnostic Compose UI components (`StreamingText`, `AITypingIndicator`, `ChatComposer`, `SpeechToTextButton`, AI message parts). No chat/network dependencies. |
| `zyn-chat-ondevice` | `com.zyn.chat.ondevice` | On-device model integration built on ML Kit GenAI. |
| `zyn-chat-app` | `com.zyn.chat.app` | Application module (applicationId `com.zyn.chat`): chat screens, ViewModels, Retrofit network layer, Stream Chat client wiring. |

Build scripts are Kotlin DSL (`.kts`) throughout, with versions centralised in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Build & test

```bash
./gradlew :zyn-chat-app:assembleRelease      # what CI builds: signed, split release APKs
./gradlew :zyn-chat-app:assembleDebug        # local development only
./gradlew :zyn-chat-compose:testDebugUnitTest :zyn-chat-ondevice:testDebugUnitTest
./gradlew lint detekt
```

### Split APKs

The app module is configured for per-ABI splits plus a universal APK. CI builds the **release**
variant only (`:zyn-chat-app:assembleRelease`), which emits:

```
zyn-chat-app/build/outputs/apk/<variant>/zyn-chat-app-arm64-v8a-<variant>.apk
zyn-chat-app/build/outputs/apk/<variant>/zyn-chat-app-armeabi-v7a-<variant>.apk
zyn-chat-app/build/outputs/apk/<variant>/zyn-chat-app-x86_64-<variant>.apk
zyn-chat-app/build/outputs/apk/<variant>/zyn-chat-app-universal-<variant>.apk
```

CI uploads **each split as its own artifact**, so a single ABI can be downloaded and installed
on its own. To add or drop an ABI, change the `splits { abi { include(...) } }` list in
[`zyn-chat-app/build.gradle.kts`](zyn-chat-app/build.gradle.kts) and keep the per-ABI upload
steps in [`.github/workflows/ci.yml`](.github/workflows/ci.yml) in sync.

## Release signing

The keystore is **never committed** (`*.jks`, `signing.properties` and `.secrets/` are
gitignored). Gradle resolves signing credentials from, in priority order:

1. environment variables — `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
   `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`;
2. `signing.properties` in the repository root (`storeFile`, `storePassword`, `keyAlias`,
   `keyPassword`) — for local builds.

If neither exists, the release variant is signed with the debug key so a fresh clone still
builds.

### Required repository secrets

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the keystore file (`base64 -w0 keystore.jks`, or `openssl base64 -A -in keystore.jks`) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore store password |
| `ANDROID_KEY_ALIAS` | key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | key password |

The release job decodes `ANDROID_KEYSTORE_BASE64` into `keystore.jks` in the workspace **before
Gradle is invoked**, fails fast if the secret is missing, and then verifies with `apksigner`
that every release APK is signed with that key.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs three jobs: `release-apks` (signed
split release APK build, one artifact per ABI plus the R8 mapping), `unit-tests` (compose +
ondevice unit and snapshot tests) and `static-analysis` (`lint`, `detekt`). CI builds and
uploads **release APKs only** — no debug APK job.
