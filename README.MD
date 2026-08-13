# Edge Translator

Offline translator for **desktop** (macOS, Windows, Linux). An Android app is planned.

The model runs on the device. After install, text, speech, images, and favorites stay local — no outbound request.

The interface is available in the same 35 languages as the catalog, including RTL (Arabic, Hebrew, Persian).

## Features

- On-device streaming translation, via [Gemma 4](https://ai.google.dev/gemma) and [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
- 35 languages (CC3M-35L / PaliGemma catalog), with automatic source-language detection
- Proofreading: the same model corrects spelling and grammar in the language of the input
- Dictation: speak up to 30 seconds, transcribed and translated on-device (Gemma 4 FLEURS, 12 languages)
- Image translation: pick a photo or a screenshot, the model reads its text and translates it on-device
- Read aloud: [Piper](https://github.com/rhasspy/piper) voices, 30 languages, downloaded on demand
- Two model profiles: **Fast** (E2B) or **Precision** (E4B)
- GPU acceleration when available, otherwise CPU
- Local favorites (search, pinning, filters)
- Model and voice downloads you control: start, pause, resume, or cancel — Settings never starts a download on its own
- First launch: resumable download, then SHA-256 verification; optional voice install
- Desktop auto-update from [GitHub Releases](https://github.com/kdroidFilter/OfflineTranslator/releases)
- About screen with open-source licenses
- Follows the system light / dark appearance

## Languages

| Capability | Count | Languages |
|------------|------:|-----------|
| Translation | 35 | French, English, Arabic, Bengali, Chinese, Croatian, Czech, Danish, Dutch, Filipino, Finnish, German, Greek, Hebrew, Hindi, Hungarian, Indonesian, Italian, Japanese, Korean, Māori, Norwegian, Persian, Polish, Portuguese, Romanian, Russian, Spanish, Swahili, Swedish, Telugu, Thai, Turkish, Ukrainian, Vietnamese |
| Dictation | 12 | French, English, Arabic, Chinese, German, Hindi, Italian, Japanese, Korean, Portuguese, Russian, Spanish |
| Read aloud | 30 | every catalog language except Croatian, Filipino, Japanese, Māori, Thai |

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

- JDK 25 or newer (to build or run from source)
- Internet connection only to download a model, a voice, or an app update
- Microphone optional (dictation)

## Install

Prebuilt packages (DMG, NSIS, Deb) are published on [GitHub Releases](https://github.com/kdroidFilter/OfflineTranslator/releases). Packaged builds update themselves from there.

## Running from source

```bash
./gradlew :desktopApp:run
```

Hot reload:

```bash
./gradlew :desktopApp:hotRun --auto
```

Force the install screen: pass `--onboarding` as a launch argument.

Native packages (GraalVM — DMG / Zip / NSIS / Deb):

```bash
./gradlew :desktopApp:packageGraalvmDmg    # macOS
./gradlew :desktopApp:packageGraalvmZip    # macOS updater payload
./gradlew :desktopApp:packageGraalvmNsis   # Windows
./gradlew :desktopApp:packageGraalvmDeb    # Linux
```

Pushing a `v*` tag builds these packages for Linux (x64, ARM64), macOS (Intel, Apple Silicon), and Windows (x64), then publishes them as a GitHub Release.

## Layout

```
shared/       logic, Compose UI, translation engine, data
desktopApp/   desktop entry point (Nucleus)
```

All application code lives in `shared`. The desktop module only starts the UI.

Android is planned: the shared UI is already Compose Multiplatform, but there is no shipping Android app yet.

## License

Copyright (C) 2026 Elie Gambache

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU Lesser General Public License version 3** as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**; without even the implied warranty of **MERCHANTABILITY** or **FITNESS FOR A PARTICULAR PURPOSE**. See the GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License ([LICENSE](LICENSE)) and of the GNU General Public License ([COPYING](COPYING), incorporated by LGPLv3) along with this program. If not, see <https://www.gnu.org/licenses/>.
