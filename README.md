# EdgeAiVoice

Android Studio Kotlin + Compose skeleton for on-device voice AI.

## Current progress snapshot (2026-03-02)

- Milestone 0/1/2: completed (ASR + LLM streaming on real device)
- Milestone 3: in progress (observability + adaptive policy + stability closure)
- Latest 30-loop regression (`scripts/m3_regression.sh` on Dimensity 8200 / 12GB):
  - `success=21 failed=9`
  - `avg_ttft_e2e_ms=22633`
  - `avg_tok_per_sec=1.53`
- Current priority: close M3 stability acceptance (30-loop stable pass) before Milestone 4 KPI optimization.

## Model packaging strategy

- Runtime `.so` stays in APK: `app/src/main/jniLibs/arm64-v8a`
- Models are **not** packaged into APK.
- Project-local model store: `runtime/models/...`
- Device runtime model store: `/sdcard/Android/data/com.edgeaivoice/files/models/...`

This avoids giant APK install time and startup copy time.

## Local model layout (project root)

- `runtime/models/asr/<asr-model-file>`
- `runtime/models/llm/<llm-model-file>`
- `runtime/models/_manifest/models.json`

## Runtime source of truth

Model download/config/package are owned by `edge-ai` repo.
This Android repo only imports runtime artifacts and validates/pushes them.

Recommended upstream flow:

1. In `edge-ai`: build + download models + package `edge-ai-android-runtime-<version>.tgz`
2. In `edgeai-android`: run `importRuntime` (or `scripts/import_runtime.*`) to import libs/models

## Push runtime models to device (one click)

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/push_runtime.ps1
```

Custom package name or serial:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/push_runtime.ps1 -PackageName com.edgeaivoice -Serial emulator-5554
```

Bash:

```bash
bash scripts/push_runtime.sh com.edgeaivoice
# or with device serial:
bash scripts/push_runtime.sh com.edgeaivoice emulator-5554
```

## Runtime behavior in app

`AssetsModelInstaller` no longer copies from assets. It now validates model paths in order:

1. `${externalFilesDir}/models/...` (preferred)
2. `${filesDir}/models/...` (fallback)

Required model files are read from `models/_manifest/models.json` (`models[].relativePath` with `required=true`).
UI shows resolved path + size + note for each required file.

## Optional tgz import

Default discovery order:

1. `-PruntimeTgzPath=...` or env `RUNTIME_TGZ`
2. `../edge-ai-android-runtime-v1.tgz`
3. `$EDGE_AI_ROOT/edge-ai-android-runtime-v1.tgz`
4. `$HOME/edge-ai/edge-ai-android-runtime-v1.tgz`

Examples:

```powershell
.\gradlew.bat importRuntime
# or
powershell -ExecutionPolicy Bypass -File scripts/import_runtime.ps1
# or override path explicitly
.\gradlew.bat importRuntime -PruntimeTgzPath="D:\edge-ai\edge-ai-android-runtime-v1.tgz"
```

Import target is now:

- `.so` -> `app/src/main/jniLibs/arm64-v8a`
- models -> `runtime/models/...`

## Verify local runtime files

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify_runtime.ps1
```
