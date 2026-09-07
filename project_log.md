# J Motors — Project Log

## Current Status

- **Step:** 10 hotfix complete — CI Java memory options
- **Phase:** GitHub Actions Android CI
- **Done:**
  - Full MVP on `main`: https://github.com/mrvk2018/j.motors.git
  - CI `Build Debug APK` uses `_JAVA_OPTIONS` / `GRADLE_OPTS` and `--no-daemon`
  - `gradlew` no longer passes quoted `-Xmx64m` (that was parsed as a Java class name)
- **Not done:** real backend lead API
- **Context:** `.cursorrules` is the source of truth for J Motors identity, stack, and Чоник persona/conversation flow

## Action History

- **2026-09-07 — Step 10 hotfix:** CI assembleDebug failed with `Could not find or load main class "-Xmx64m"`. Set clean Java/Gradle memory env on the Build Debug APK step and removed quoted heap args from `gradlew`. Pushed to `main` (`dca8eda`).
- **2026-09-07 — Step 10:** Added GitHub Actions Android CI, initial commit, and push to `https://github.com/mrvk2018/j.motors.git`. Local development phase complete.
- **2026-09-07 — Step 9:** Added hands-free voice loop: `ChonikSttManager` (SpeechRecognizer, ru-RU free-form) and `ChonikTtsManager` (TextToSpeech, Locale ru). ViewModel speaks Gemini replies, drives `_audioAmplitude`, then restarts the mic. AR overlay unchanged aside from mic permission and status labels.
- **2026-09-07 — Step 8:** Wired the real Gemini key through `local.properties` → Gradle `buildConfigField` → `BuildConfig.GEMINI_API_KEY`. Enabled `buildFeatures.buildConfig`. `AiRepositoryImpl` no longer uses `YOUR_GEMINI_API_KEY`. `local.properties` is gitignored.
- **2026-09-07 — Step 7:** Integrated ARCore: camera permission, Play Services for AR metadata, horizontal plane detection, Anchor, and Compose overlay of `ChonikAvatar` via 3D→2D projection. Dialogue text is shown under the sphere. Office handover is `Log.d` only. Active MVP coding phase marked complete.
- **2026-09-07 — Step 6:** Added living emotional sphere: `ChonikEmotion`, Gemini tag parsing in `ChonikViewModel`, and `ChonikAvatar` (Canvas + radialGradient, `animateColorAsState`, breathing scale, `audioAmplitude` stub). No ARCore.
- **2026-09-07 — Step 5:** Integrated Google Gemini via Retrofit (`gemini-2.5-flash` generateContent). Added `GeminiApiService`, request/response DTOs, `AiRepository`/`AiRepositoryImpl`, and wired replies into `ChonikViewModel`. API key is a `YOUR_GEMINI_API_KEY` stub. No AR code.
- **2026-09-07 — Step 4:** Added dialogue scaffold: `UserProfile`, `ChonikState` state machine, and `ChonikViewModel` with a randomized 3-phrase greeting pool. No AR or network code.
- **2026-09-07 — Step 3:** Configured Gradle for `:app` (`compileSdk`/`targetSdk` 34, `minSdk` 26, Compose, ARCore 1.54.0, Lifecycle/ViewModel, Coroutines, Retrofit). No business logic added.
- **2026-09-07 — Step 2:** Created `.cursorrules` in the project root (`C:\Users\user\projects\JMotors\.cursorrules`) with J Motors identity, technical stack, and Чоник persona/workflow rules. Chonik context system successfully initialized. Gradle logic and dependencies were not changed.
- **2026-09-07 — Step 1:** Initialized Clean Architecture package structure (`core`, `data`, `domain`, `presentation`) in `app/src/main/kotlin/com/jmotors/`. No business logic added. `.cursorrules` and `project_log.md` were not found in the project root; this log was created and updated for Step 1.
