# AGENTS.md

Guidance for AI coding assistants working in this repository.

## Project

**Zyn Chat** — Android app (Kotlin + Jetpack Compose), applicationId/namespace `com.zyn.chat`.
Build system: **Gradle Kotlin DSL only** (`.kts`). Never add Groovy `*.gradle` build files.

Derived from `GetStream/stream-chat-android-ai`, re-namespaced to `com.zyn.chat.*`. Upstream
Stream license headers stay in place (see `LICENSE`).

## Module

One Gradle module: `app` (`com.zyn.chat.app`), holding three package trees:

- `com.zyn.chat.app` — the application: chat UI, ViewModels, Retrofit layer, Stream Chat wiring.
- `com.zyn.chat.compose` — Stream-derived Compose components (`StreamingText`, `AITypingIndicator`,
  `ChatComposer`, `SpeechToTextButton`, AI message parts), kept as their own package tree and
  pinned by Paparazzi snapshots even though they share the app's module.
- `com.zyn.chat.ondevice` — on-device model integration (ML Kit GenAI).

## UI layer

- Design tokens live in `ui/theme`: `Color.kt` (palettes), `Typography.kt` (type scale, also used for
  Markdown heading mapping), `Shape.kt` (radii). Change colours/type there, not inline.
- **The accent is a token, never a literal colour**: `ui/theme/Accent.kt` exposes
  `MaterialTheme.accent` (`primary`, `onPrimary`, `tint`, `border`). It falls back to the colour
  scheme's primary, which is the brand emerald: `Green` (`#047857`) in the light theme and the
  brighter `GreenBright` (`#10B981`) in the dark one, both in `Color.kt`. Selection states, the send
  button and the model picker read these tokens, so a future settings screen swaps the accent via
  `AppTheme(accent = ...)` without touching components. Do not hardcode an accent colour anywhere.
- Captions on the accent come from `onAccentColor(seed)`, never a fixed white: white is 5.5:1 on the
  light seed but only 2.5:1 on the dark theme's brighter one — under the 3:1 even a non-text element
  needs — where `Ink` reaches 7.5:1. It picks by WCAG luminance, and `AccentTest` keeps the shipped
  accent pairs at or above 4.5:1.
- UI icons are `ImageVector`s in `ui/icons/ZynIcons.kt` (shared 24x24 viewport, 1.7dp stroke). The
  app's older `res/drawable/ic_*` icons are the Material-Symbols ones the top bar still uses.
- **Brand marks are generated, never hand-drawn**: `tools/lobe-icons/convert.py` downloads the
  MIT-licensed LobeHub icon set (<https://icons.lobehub.com/>) and writes
  `res/drawable/ic_model_<id>.xml`, `data/providers/BrandCatalog.kt` and `ui/brand/ModelMarks.kt`.
  Do not hand-edit those three: add a row to the script's brand table and re-run it from the repo root.
  The drawables are `currentColor` mono glyphs, so they tint with the theme like any other icon.
  The LobeHub sources declare `fill-rule="evenodd"` on the root `<svg>`, which inherits into every
  path; the converter resolves it (path attribute → `style` → nearest ancestor) into
  `android:fillType`. Dropping that inheritance silently fills cutouts solid — the DeepSeek whale
  bug — so keep ancestor attribute inheritance in the path conversion.
- The app owns its composer: `ui/chat/ZynComposer.kt` + `AttachmentMenu.kt` + `ComposerControls.kt` +
  `SmartSendButton.kt` + `ModelSelector.kt`. The Stream-derived `ChatComposer` and
  `SpeechToTextButton` (in the `com.zyn.chat.compose` package tree) are intentionally left untouched
  (and unused by the app), so their Paparazzi snapshots keep matching — don't restyle them to change
  the app's look.
- The attachment menu is a **popup above the button**, never a panel that grows the composer
  (`ui/chat/AttachmentMenu.kt`, placed by its own `PopupPositionProvider` — the composer sits at the
  bottom, so a menu opening downwards would be clipped). Its rows are Camera / Photos / Files only:
  the app has no plugin system and no reasoning level to switch, so ChatGPT's "Plugins" and
  "Think harder" are deliberately absent. `PopupProperties(focusable = true)` is what lets a tap
  outside and the back gesture close it.
- Taking a picture is the one attachment path that writes a file: `ui/chat/CameraCapture.kt` puts the
  shot in `cache/camera/` and hands the camera app a `FileProvider` URI — authority
  `<applicationId>.fileprovider`, declared in `AndroidManifest.xml` with `res/xml/zyn_file_paths.xml`,
  while the code builds that authority from `context.packageName`, so an `applicationIdSuffix` would
  break it. No storage permission is involved, and the image-capability rules gate the camera exactly
  like the photo picker: a model known to be text-only is refused before the camera opens.
- Dictation is the app's own `ui/chat/speech/SpeechInput.kt` state machine (idle / listening /
  processing / error / unavailable). It chains recognition attempts, because the platform recognizer
  stops at the first end of speech, so dictation keeps running until the person stops it or types —
  typing cancels it. It writes recognised text into the field and **never** sends; only
  `SmartSendButton` sends. Release it via `rememberSpeechInput()`'s `DisposableEffect`.
- `SmartSendButton` = mic when empty, send when there is text, stop while generating, stop/cancel
  while dictating. Long press dictates, and a long press can never fall through into a send.
- Model picker: `ui/chat/ModelPickerSheet.kt` (Material `ModalBottomSheet`, drag handle, provider
  tabs, search field, grouped rows, three distinct empty states). The sections come from
  `data/providers/ModelGrouping.kt` (`groupOf` / `groupedModels` / `capabilityTokensOf`), which is
  pure and unit-tested — change grouping rules there, not in the sheet. The drawer is a
  `ModalNavigationDrawer` with a partial-width sheet, animated scrim and an edge-swipe strip in
  `ui/AiChatApp.kt`.
- **Model abilities are never assumed** (`data/providers/ModelCapabilities.kt`): every field is
  tri-state, so `null` means unknown and is never treated as "no". A provider's own report wins over
  the local `ModelCapabilityCatalog` (evidence-based family and suffix knowledge, with explicit
  exceptions checked before families). Images are attached only when image support is positively
  known: a known text-only model is blocked with an explanation, an unknown one is allowed with a
  warning. Never collapse unknown into false.
- Picture previews sit **inside** the composer's own field, not above it; `ImageThumbnails.kt`
  decodes off the main thread because there is no image-loading library on the classpath. A picture
  in a message opens `ui/chat/ImagePreview.kt`, and assistant attachments are rendered as well.
- Opening a chat passes **both** ids: `selectedConversationId` (storage) and `selectedChannelId`
  (Stream). Only a real channel id may reach Stream — see `isChannelId` in
  `domain/chat/ConversationRepository.kt`; handing Stream a stored uuid is what crashed on resume.
- Drawer: `ui/components/ChatDrawer.kt` with `DrawerNavItem.kt`, `DrawerConversationItem.kt` and
  `ConversationDialogs.kt`. Recents are **this device's stored conversations only**, newest first,
  bucketed into Today / Yesterday / Previous 7 days / Older by `updatedAt` (`RecentSection` +
  `groupedByRecentSection` in `ConversationListState.kt`). They are deliberately **not** read from
  Stream's channel list: the app signs into a shared demo Stream account (`uun7ywwamhs9` key with a
  fixed `stream-user`), so that account's channels are other people's seeded sample chats and showed
  up as placeholder recents. A chat that has a channel is stored under that channel's id, so it still
  lists and still opens its channel; only channels this device never used are absent. Long-pressing a
  row offers Rename and Delete; a stored title always wins over a channel name, which is what makes a
  rename stick, and a `TitleSource.Manual` title is never replaced automatically.
- Every new chat gets its own storage id (`newConversationId()`); the id is never shared or reused
  between chats, and `ChatViewModel` stores under `cid ?: localConversationId`.
- User-facing text goes through `res/values/strings.xml` (names prefixed `zyn_`).
- Provider and model state: `data/providers/` — `ProviderCatalog` (known OpenAI-compatible endpoints),
  `ProviderStorage` (JSON in SharedPreferences), `ApiKeyStore` (Android Keystore, AES-GCM),
  `ProviderRepository` (the single writer of providers and selection) and `AiProviderClient`
  (`/models` over OkHttp). **Providers start empty by design**: nothing may invent a provider or a
  model list — the only way in is `ProviderRepository.addProvider`, driven by the providers screen.
- Model metadata: `data/providers/ModelCatalog.kt` **only decorates** what a provider reported — a
  readable name derived from the slug, a known context window, and the `:free` suffix OpenRouter uses —
  while `ModelBrands.of(providerId, slug)` names its maker for a badge, returning `null` (→ initials)
  instead of guessing. Unknown values stay `null` (never guess a context window).
  `ui/components/BrandMark.kt` renders `markRes` and falls back to initials.
- Brand recognition (`data/providers/ModelBrand.kt`) takes the model segment after the last `/` (so
  `openai/gpt-4o` and `publishers/google/models/gemini-2.5-flash` both work), then matches each brand's
  identifying words **longest first** and only as whole words: that is what keeps `llama-3.1-nemotron`
  with NVIDIA rather than Meta, and stops `phi` from claiming `dolphin` or `pi` from claiming
  `pixtral`. A model that names its family beats its provider id, which is the fallback — `azure` →
  Microsoft, `bedrock` → Amazon, `vertex` → Google. `ui/providers/ProviderCard.kt` uses the same
  lookup, so provider rows show real logos too. Changing the rules means changing the tests in
  `ModelBrandTest.kt`, which pin every case above.
- Answers: `domain/chat/ChatEngine.kt` is the seam. `data/chat/ProviderChatEngine.kt` streams from the
  person's own provider over OpenAI-compatible SSE (with `OpenAiProtocol` + `SseReader`), and
  `ChatViewModel` uses it whenever a provider *and* model are configured — otherwise it keeps the
  legacy Stream path untouched. `presentation/chat/ProviderAnswerSource.kt` owns the selection, the
  key lookup and the failure copy. **Engines are stateless**: a request carries its whole
  conversation, built from that conversation alone, and collecting is what makes the HTTP call — so
   cancelling (stop, or leaving the chat) cancels it and no answer can land in another chat.
- **Failed sends are recoverable**: a provider failure marks the *question* `isFailed` with the
  provider's reason (`ChatUiState.Message.errorReason`) and drops any partial answer, instead of
  inserting a failure reply. `ChatViewModel.retryMessage` resends through the normal send path and
  `discardMessage` removes the question so edit can put its text back into the composer
  (`ZynComposer`'s `draftText` / `onDraftConsumed`, consumed once). A failed exchange is never
  written to `ConversationRepository`, so a retry starts from clean history.
- Conversations: `data/chat/ConversationStore.kt` keeps **one JSON file per conversation** under
  `filesDir/conversations/`, and `domain/chat/ConversationRepository.kt` is the **only** source of a
  conversation's turns and title, always keyed by conversation id. Never add a shared or global message
  list: a provider request is built from `history(conversationId)` alone, which is what keeps one
  chat's history out of another's (the per-conversation `ViewModelStore` key and the cancelled stream
  job in `ChatViewModel` complete the guarantee).
- Titles are derived on the device (`domain/chat/ConversationTitles.kt`, Markdown stripped, capped at
  48 chars) instead of repeating the first message verbatim. `TitleSource.Manual` (a rename) is final:
  an automatic title never overwrites it. `ConversationRepository.revision` is the signal the drawer
  refreshes on.
- **Credentials never leak**: `di/NetworkModule.kt` redacts `Authorization` / `x-api-key` /
  `api-key` / `x-goog-api-key` on the single logging interceptor, and logging is only enabled in
  debug builds (`App.kt` passes `BuildConfig.DEBUG`). Every provider authenticates with a **header,
  never a query parameter** — a key in the URL would bypass redaction, so never add a provider that
  passes its key in the query string. API keys are device-local and are never sent to any backend
  other than the provider's own host.
- **Crashes are shown on the device, not just logcat**: `crash/CrashHandler.kt` is installed in
  `App.attachBaseContext` (before `onCreate`, because a crash during startup is exactly what it must
  survive) and opens `crash/CrashActivity.kt`, which the manifest puts in its **own `:crash` process**
  so it can outlive the process that died. `App.onCreate` returns early in that process — that process
  must never run app initialisation. Never move the handler install into `onCreate`, and never give
  `CrashActivity` a dependency on `chatDependencies`.
- `App.onCreate` calls `super.onCreate()` **first**; nothing may run before it. StrictMode is set up
  after it.
- The drawer's "Providers" entry and the "Providers" action in the model picker header both call the
  single `onManageProviders` action hoisted in `ui/AiChatApp.kt`, which opens the one
  `ui/providers/ProvidersScreen.kt`. Adding a provider validates the key against that provider's real
  `/models` endpoint before anything is saved. **Never add a second provider-management path or
  screen.**
- **Presentation only for now**: routing and the profile row. The drawer's search is no longer among
  them: it filters the recents list through `matching()` in
  `presentation/conversations/ConversationSearch.kt`, and its state lives in `ChatDrawer`, not in the
  app's navigation state.

## Commands

```bash
./gradlew :app:assembleRelease    # what CI builds: the signed release APK
./gradlew :app:assembleDebug      # local development only
./gradlew :app:testDebugUnitTest  # every unit and Paparazzi snapshot test
./gradlew lint detekt             # static analysis
```

Toolchain: JDK 17, compileSdk 36, minSdk 26 (the single module's floor; the compose tree's old 23 is
superseded).

### Pushing from the sandbox

`/workspace` has **no `.git`** and no JDK or Android SDK, so nothing can be committed or compiled
there. To push, work through a clone and `gh`'s stored credentials (already authenticated):

```bash
git clone https://github.com/prexhgaming/zyn-chat.git /tmp/zyn-clone   # remote + history live here
# copy the changed files from /workspace into the clone, then:
git -C /tmp/zyn-clone add -A && git -C /tmp/zyn-clone commit && git -C /tmp/zyn-clone push origin main
```

Copy only the files you actually changed: the sandbox loses the executable bit, so `gradlew` always
shows up as a spurious `100755 -> 100644` mode change — committing it would leave the wrapper
non-executable for everyone.

## Build & test notes

- The release build emits **one universal APK** (`zyn-chat-app/build/outputs/apk/release/`) —
  ABI splits were removed because the app has almost no native code. CI builds and uploads that
  single release APK (`assembleRelease`); there is no debug APK job.
- There is **no Android SDK in the dev sandbox**: compilation, tests and APK builds only run in
  CI (`.github/workflows/ci.yml`) or on a machine with the SDK. Never claim a local build passed.
- **The release build is minified, and `zyn-chat-app/proguard-rules.pro` is load-bearing.** State is
  serialised with Moshi's *reflective* adapter, which reads Kotlin metadata at runtime; the rules keep
  that metadata, the serialised models (`data/providers`, `data/chat`, `domain/chat`) and the
  `@Json` field names. Without them the failure lands inside `Application.onCreate` and the app dies
  before any screen appears. Keep serialiser creation lazy and failure-tolerant, as
  `ProviderStorage` / `ConversationStore` do.
- The app module runs Paparazzi + Robolectric snapshot tests; they cannot share a JVM, hence
  `tasks.withType<Test> { forkEvery = 1 }` — do not remove.
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

Implemented: the providers screen (known provider → validated key → real `/models`), encrypted
device-local key storage, model discovery with real brand marks, and bring-your-own-key streaming
answers kept per conversation with derived titles.

Not implemented yet: request routing (the Routing screen is presentation only), the drawer search
action, the profile row, and conversation sync anywhere off the device. Do not invent providers,
models or backend behaviour.
