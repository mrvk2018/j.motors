# J Motors — Project Log

## Current Status

- **Step:** 12 — Premium Sci-Fi overhaul (local commit, not pushed)
- **Phase:** Native SBS 21:9 + Go2 hologram + XREAL USB mic + GitHub secret key
- **Done:**
  - STT capture prefers `GET_DEVICES_INPUTS` USB/wired headset (XREAL) via `setCommunicationDevice`
  - Cinematic 21:9 letterbox per SBS eye (black IMAX cache)
  - Solarpunk background pool (4 bright 4K plates, random per session, uncrossed parallax)
  - `ChonikAvatar` is a neon Unitree Go2 wireframe with TTS mouth lip-sync
  - CI `local.properties` reads `secrets.GEMINI_API_KEY`
- **Not done:** git push / GitHub Actions cloud build; real backend lead API; car-model discussion logic
- **Context:** `.cursorrules` is the source of truth for J Motors identity, stack, and Чоник persona/conversation flow

## Action History

- **2026-09-07 — Step 12:** Premium Sci-Fi overhaul committed locally (no push): XREAL USB mic routing via `GET_DEVICES_INPUTS`, 21:9 SBS letterbox, Solarpunk background pool, holographic Unitree Go2 avatar with amplitude lip-sync, GitHub secret for Gemini. Night city texture removed.
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
