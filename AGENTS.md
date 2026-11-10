# AGENTS.md — CECAPI

## What this repo is
- Native Android app (Kotlin + Jetpack Compose, Hilt, Room, CameraX, ML Kit) plus an optional FastAPI backend proxy for the late Module 3 AI. One Gradle module, `:app`.
- Voice-first accessibility app for blind/low-vision users (CECAPI, Durango). Dark theme is intentionally fixed; one `VoiceEngine` drives all TTS/ASR.

## Build and run
- Prereqs: Android Studio Ladybug (2024.2+), Android SDK 35, JDK 17 (compile target/source compatibility), a phone/emulator **with Google Play** (no Play = no speech recognition or ML Kit). The OS JDK on this machine is 25, which Gradle cannot use; build with Android Studio's bundled JBR (`.../android-studio/jbr`) or another JDK 17/21.
- `gradlew` is not executable in this tree; run it as `bash ./gradlew ...` (or `chmod +x gradlew` once).
- Versions are frozen (decision 0001): AGP 8.5.2, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.10.00. If Android Studio offers an AGP upgrade, decline it.
- Gradle flags `android.builtInKotlin=false` and `android.newDsl=false` are intentional; do not flip them.
- Dev build: `JAVA_HOME=<android-studio>/jbr bash ./gradlew :app:assembleDebug` (or set `JAVA_HOME` once). Verified clean build succeeds offline in ~45 s. SDK location comes from gitignored `local.properties`; deps come from Google/Maven Central (network needed the first time).
- There are **no automated tests/CI** in this repo; verification is building the APK and manual testing on a device.
- Regenerate the voice-command reference after editing `CommandCatalog.kt` / `CommandAlternatives.kt`: run `python docs/herramientas/generar_comandos.py` from the repo root (also `generar_pagina.py` for the shared page).

## Backend (Module 3, optional)
- Code is in `backend/`. Setup: `cd backend && pip install -r requirements.txt`, copy `.env.example` to `.env`, fill `GEMINI_API_KEY`/`GROQ_API_KEY`, then `uvicorn main:app --host 0.0.0.0 --port 8000`.
- Point the app at it by editing `AI_PROXY_BASE_URL` in `app/build.gradle.kts` (`defaultConfig`) to `http://<PC_IP>:8000/preguntar`, then rebuild.
- IA is off by default and must stay off by default; API keys must never ship in the APK.
- `main.py` currently does `import imagenes`, which is not in this tree (branch `modulo03/imagenes` not merged). The server will not boot until that module is merged or the import is removed.

## Module boundaries
- App source: `app/src/main/java/com/cecapi/app/`. Each team owns only its `feature/moduloN_*` folder; `core/` is shared and owned by Equipo 1 — request changes rather than editing or copying.
- Room schema version is 16; migrations live in `core/data/Migrations.kt` and are wired in `core/di/DatabaseModule.kt`. Schema or entity changes need a new migration.
- Demo accounts are auto-created on DB open (`CECAPI`, `PEPE`, `JORGE`, `MIRIAM`, `JUAN`, plus pending `LUPITA`); usernames are stored uppercase and login is case-insensitive. `resetAllPasswordsOnce` sets every password to `1234` on the first open (guarded by the `contrasenas_reiniciadas_1234` pref). Demo ranking rows seed `JUAN`/`MIRIAM`.
- `database/schema.sql` also describes Modules 8–14, which have no code yet.
- The app enables cleartext traffic for the LAN backend proxy and ships `allowBackup="false"`.

## Conventions and gotchas
- AI keys stay only in `backend/.env`; the APK holds no secret (see `feature/modulo3_asistenteinteligente/AiAssistantApi.kt`).
- Do not let Android Studio move AGP/Gradle/Kotlin/KSP versions up.
- Branches are named `moduloNN/descripcion`; integration work currently lives on `integracion/actividades`. PR target branch is still unresolved (see `docs/guias/colaborar.md`); confirm with the project owner before opening.
- `git config user.email` must be your GitHub account email or commits won't count.
- If you change spoken UI text (`CommandCatalog`/`CommandAlternatives`), regenerate `docs/referencia/comandos.md`.
- Docs follow a status header (vigente/no, owner, date + commit); documentation lives in `docs/`, never under `app/src/main`.
- Text policy/legal drafts are not final; verify norms docs before relying on them.
- Unresolved security patches (e.g. `S-03`, `S-20`) sit under `docs/agentes/seguridad/parches/` and are **not applied**; check there before touching security-sensitive code.
