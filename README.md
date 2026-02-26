# EdgeAiVoice

Android Studio Kotlin + Compose skeleton for on-device voice AI.

## Model packaging strategy

- Runtime `.so` stays in APK: `app/src/main/jniLibs/arm64-v8a`
- Models are **not** packaged into APK.
- Project-local model store: `runtime/models/...`
- Device runtime model store: `/sdcard/Android/data/com.edgeaivoice/files/models/...`

This avoids giant APK install time and startup copy time.

## Local model layout (project root)

- `runtime/models/asr/ggml-base.bin`
- `runtime/models/llm/model.gguf`
- `runtime/models/_manifest/models.json`

## Push models to device (one click)

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/push_models.ps1
```

Custom package name or serial:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/push_models.ps1 -PackageName com.edgeaivoice -Serial emulator-5554
```

Bash:

```bash
bash scripts/push_models.sh com.edgeaivoice
```

## Runtime behavior in app

`AssetsModelInstaller` no longer copies from assets. It now validates model paths in order:

1. `${externalFilesDir}/models/...` (preferred)
2. `${filesDir}/models/...` (fallback)

UI shows resolved path + size + note for each required file.

## Optional tgz import

If runtime package exists at `../edge-ai-android-runtime-v1.tgz`:

```powershell
.\gradlew.bat importRuntime
# or
powershell -ExecutionPolicy Bypass -File scripts/import_runtime.ps1
```

Import target is now:

- `.so` -> `app/src/main/jniLibs/arm64-v8a`
- models -> `runtime/models/...`

## Verify local runtime files

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify_runtime.ps1
```
