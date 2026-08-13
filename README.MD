# Edge Translator

Offline translator for Android and desktop (macOS, Windows, Linux).

The model runs on the device. After install, text, speech, and history stay local — no outbound request.

The interface is available in the same 35 languages as the catalog, including RTL (Arabic, Hebrew, Persian).

## Features

- On-device streaming translation, via [Gemma 4](https://ai.google.dev/gemma) and [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
- 35 languages (CC3M-35L / PaliGemma catalog), with automatic source-language detection
- Dictation: speak up to 30 seconds, transcribed and translated on-device (Gemma 4 FLEURS, 12 languages)
- Image translation: pick a photo or a screenshot, the model reads its text and translates it on-device
- Read aloud on desktop: [Piper](https://github.com/rhasspy/piper) voices, 30 languages, downloaded on demand
- Two model profiles: **Fast** (E2B) or **Precision** (E4B)
- GPU acceleration when available, otherwise CPU
- Local history (search, pinning, filters)
- First launch: resumable download, then SHA-256 verification; optional voice install on desktop
- Follows the system light / dark appearance

## Languages

| Capability | Count | Languages |
|------------|------:|-----------|
| Translation | 35 | French, English, Arabic, Bengali, Chinese, Croatian, Czech, Danish, Dutch, Filipino, Finnish, German, Greek, Hebrew, Hindi, Hungarian, Indonesian, Italian, Japanese, Korean, Māori, Norwegian, Persian, Polish, Portuguese, Romanian, Russian, Spanish, Swahili, Swedish, Telugu, Thai, Turkish, Ukrainian, Vietnamese |
| Dictation | 12 | French, English, Arabic, Chinese, German, Hindi, Italian, Japanese, Korean, Portuguese, Russian, Spanish |
| Read aloud (desktop) | 30 | every catalog language except Croatian, Filipino, Japanese, Māori, Thai |

## Models

Weights are not in this repository. They are downloaded from Hugging Face on first launch (the only step that needs Internet).

| Profile | Model | Size | Repository |
|---------|--------|------|------------|
| Fast | Gemma 4 E2B IT · QAT 2/4/8-bit | ~2.6 GB | [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) |
| Precision | Gemma 4 E4B IT · QAT 2/4/8-bit | ~3.7 GB | [litert-community/gemma-4-E4B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm) |

Recommended disk space for the Fast profile: **~3.2 GB** (weights plus install headroom).

Piper voices come from [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices) (~60–140 MB each) and are optional.

Gemma models are covered by Google’s [Gemma license](https://ai.google.dev/gemma/docs/gemma_license), which is separate from this application’s license.

## Requirements

- JDK 17 or newer
- Android 8.0 (API 26) or newer for the mobile target
- Internet connection only to download a model or a voice
- Microphone optional (dictation)

## Running the app

### Android

Open the project in Android Studio and run the `androidApp` configuration, or:

```bash
./gradlew :androidApp:assembleDebug
```

The APK is at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

### Desktop

```bash
./gradlew :desktopApp:run
```

Hot reload:

```bash
./gradlew :desktopApp:hotRun --auto
```

Force the install screen: pass `--onboarding` as a launch argument.

Native packages (GraalVM — DMG / NSIS / Deb):

```bash
./gradlew :desktopApp:packageGraalvmDmg    # macOS
./gradlew :desktopApp:packageGraalvmNsis   # Windows
./gradlew :desktopApp:packageGraalvmDeb    # Linux
```

## Layout

```
sharedUI/     logic, Compose UI, translation engine, data
androidApp/   Android entry point
desktopApp/   desktop entry point (Nucleus)
```

All application code lives in `sharedUI`. The `*App` modules only start the UI.

## License

Copyright (C) 2026 Elie Gambache

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU Lesser General Public License version 3** as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without even the implied warranty of **MERCHANTABILITY** or **FITNESS FOR A PARTICULAR PURPOSE**. See the GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License ([LICENSE](LICENSE)) and of the GNU General Public License ([COPYING](COPYING), incorporated by LGPLv3) along with this program. If not, see <https://www.gnu.org/licenses/>.
