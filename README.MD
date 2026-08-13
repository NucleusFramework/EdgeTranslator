# Edge Translator

**Offline** translator for Android and desktop (macOS, Windows, Linux).  
The model runs on the device: after install, texts, documents, and history stay local — no outbound request.

The UI is available in English and French.

## Features

- On-device streaming translation, via [Gemma 4](https://ai.google.dev/gemma) and [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
- 35 languages (CC3M-35L / PaliGemma catalog), with automatic source-language detection
- Two model profiles: **Fast** (E2B) or **Precision** (E4B)
- GPU acceleration when available, otherwise CPU
- Local history (search, pinning, filters)
- First launch: resumable download, then SHA-256 verification
- System / light / dark theme

## Models

Weights are not in this repository. They are downloaded from Hugging Face on first launch (the only step that needs Internet).

| Profile | Model | Size | Repository |
|---------|--------|------|------------|
| Fast | Gemma 4 E2B IT · QAT 2/4/8-bit | ~2.6 GB | [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) |
| Precision | Gemma 4 E4B IT · QAT 2/4/8-bit | ~3.7 GB | [litert-community/gemma-4-E4B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm) |

Recommended disk space for the Fast profile: **~3.2 GB** (weights plus install headroom).

Gemma models are covered by Google’s [Gemma license](https://ai.google.dev/gemma/docs/gemma_license), which is separate from this application’s license.

## Requirements

- JDK 17 or newer
- Android 8.0 (API 26) or newer for the mobile target
- Internet connection only to download a model

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

Native packages (DMG / NSIS / Deb):

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
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
