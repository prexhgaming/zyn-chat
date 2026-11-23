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

## Module

A single Gradle module, `app` (applicationId `com.zyn.chat`), holding three package trees:

| Package tree | Role |
| --- | --- |
| `com.zyn.chat.app` | The application: chat screens, ViewModels, Retrofit network layer, Stream Chat client wiring. |
| `com.zyn.chat.compose` | Stream-derived Compose UI components (`StreamingText`, `AITypingIndicator`, `ChatComposer`, `SpeechToTextButton`, AI message parts), pinned by Paparazzi snapshots. |
| `com.zyn.chat.ondevice` | On-device model integration built on ML Kit GenAI. |

Build scripts are Kotlin DSL (`.kts`) throughout, with versions centralised in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Build & test

```bash
./gradlew :app:assembleRelease      # what CI builds: the signed release APK
./gradlew :app:assembleDebug        # local development only
./gradlew :app:testDebugUnitTest    # unit + Paparazzi snapshot tests
./gradlew lint detekt
```

### APK output

A single **universal** APK is built, containing every ABI:

```
zyn-chat-app/build/outputs/apk/release/zyn-chat-app-release.apk
```

There are deliberately no per-ABI splits: the app ships almost no native code, so splitting only
added release and install overhead. If a large native dependency is ever introduced, re-enable
`splits { abi { ... } }` in [`zyn-chat-app/build.gradle.kts`](zyn-chat-app/build.gradle.kts) and
restore the per-ABI upload steps in [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

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

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs three jobs: `release-apk` (signed
release APK build plus the R8 mapping), `unit-tests` (all unit and Paparazzi snapshot tests)
and `static-analysis` (`lint`, `detekt`). CI builds and uploads **the release APK only** — no
debug APK job.
