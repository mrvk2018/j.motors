# J Motors — Project Log

## Current Status

- **Step:** 17 — Filament 3D particle materialize (no Compose overlay)
- **Phase:** MVP demo funnel
- **Done:**
  - 4-stage Gemini system prompt (name → Avante/Sonata/Santa Fe → cash/credit+visa+взнос → Suwon + transfer)
  - Hidden `[SET_CAR:…]` / `[SET_STATE:…]` tags parsed in ViewModel, stripped before TTS
  - `ClientProfile` snapshot written as JSON under `filesDir/leads/` for Suwon
  - Gradle task `:app:downloadCarModels` fills `assets/models/cars/{avante,sonata,santa_fe}.glb`
- **Not done:** live Suwon HTTP API; CI APK is manual (`workflow_dispatch` only)
- **Context:** `.cursorrules` is the source of truth for J Motors identity, stack, and Чоник persona/conversation flow

## Action History

- **2026-09-09 — CI:** Replaced auto-on-push Android workflow with manual `workflow_dispatch` only (`android-build.yml`).
- **2026-09-09 — Step 16:** SET_CAR materializes the GLB: TransformManager scale 0.001→1, Compose neon particles (burst then collapse), then 42s Y idle. Version `0.8.0` (versionCode 9).
- **2026-09-08 — Step 14:** Refactored the SBS showroom for the XREAL MVP demo: deleted eco-city pool + Go2 wireframe, pitch-black passthrough, glowing AI orb with color state machine, Filament `loadCarModel()` for a mid-air rotating GLB (local assets or Khronos CDN). Version `0.6.0` (versionCode 7).
- **2026-09-08 — Step 13:** Packaged MVP fix (staged, no push): Gemini chat history session, brevity system prompt, full-bleed SBS (no letterbox), detailed Go2 hologram with BlurMaskFilter, STT → funnel fact extractor (name / F-4 H-2 E-9 G-1 / budget / credit).
- **2026-09-07 — Step 11 hotfix:** Voice loop: STT heard «меня зовут Влад» but Gemini died in <1s (CI APK baked `MOCK_KEY_FOR_BUILD`). Errors were not shown. Stopped calling `SpeechRecognizer.stopListening()` after `onResults` (that caused ERROR_CLIENT 5 and a STT retry). Surface Gemini errors on the plate and in logcat.
- **2026-09-07 — Step 11:** Replaced ARCore camera/planes with native stereoscopic SBS 3D. Screen splits left/right for XREAL Air 2 Pro. Eco-city background uses uncrossed parallax (far); `ChonikAvatar` + holographic dialogue use crossed parallax (near). Radial highlight on the sphere shifts left/right per eye so the orb has volume. Manifest no longer requires camera/ARCore; `com.google.ar:core` removed. Version `0.5.0` (versionCode 5).
- **2026-09-07 — Step 10 hotfix:** CI assembleDebug failed with `Could not find or load main class "-Xmx64m"`. Set clean Java/Gradle memory env on the Build Debug APK step and removed quoted heap args from `gradlew`. Pushed to `main` (`dca8eda`).
- **2026-09-07 — Step 10:** Added GitHub Actions Android CI, initial commit, and push to `https://github.com/mrvk2018/j.motors.git`. Local development phase complete.
- **2026-09-07 — Step 9:** Added hands-free voice loop: `ChonikSttManager` (SpeechRecognizer, ru-RU free-form) and `ChonikTtsManager` (TextToSpeech, Locale ru). ViewModel speaks Gemini replies, drives `_audioAmplitude`, then restarts the mic.
- **2026-09-07 — Step 8:** Wired the real Gemini key through `local.properties` → Gradle `buildConfigField` → `BuildConfig.GEMINI_API_KEY`. Enabled `buildFeatures.buildConfig`. `local.properties` is gitignored.
- **2026-09-07 — Step 7:** Integrated ARCore: camera permission, Play Services for AR metadata, horizontal plane detection, Anchor, and Compose overlay of `ChonikAvatar` via 3D→2D projection. Dialogue text is shown under the sphere. Office handover is `Log.d` only.
- **2026-09-07 — Step 6:** Added living emotional sphere: `ChonikEmotion`, Gemini tag parsing in `ChonikViewModel`, and `ChonikAvatar` (Canvas + radialGradient).
- **2026-09-07 — Step 5:** Integrated Google Gemini via Retrofit (`gemini-2.5-flash` generateContent).
- **2026-09-07 — Step 4:** Added dialogue scaffold: `UserProfile`, `ChonikState` state machine, and `ChonikViewModel` with a randomized 3-phrase greeting pool.
- **2026-09-07 — Step 3:** Configured Gradle for `:app` (`compileSdk`/`targetSdk` 34, `minSdk` 26, Compose, Lifecycle/ViewModel, Coroutines, Retrofit).
- **2026-09-07 — Step 2:** Created `.cursorrules` in the project root with J Motors identity, technical stack, and Чоник persona/workflow rules.
- **2026-09-07 — Step 1:** Initialized Clean Architecture package structure (`core`, `data`, `domain`, `presentation`) in `app/src/main/kotlin/com/jmotors/`.
