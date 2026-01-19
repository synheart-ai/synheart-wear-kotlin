# Synheart Flux Native Libraries

This directory contains vendored Flux native binaries for Android.

**Source:** https://github.com/synheart-ai/synheart-flux

## Structure

```
vendor/flux/
├── VERSION                    # Pinned Flux version (e.g., v0.1.0)
├── README.md                  # This file
└── android/
    └── jniLibs/
        ├── arm64-v8a/         # libsynheart_flux.so (ARM64)
        ├── armeabi-v7a/       # libsynheart_flux.so (ARMv7)
        └── x86_64/            # libsynheart_flux.so (x86_64)
```

## Release Artifacts

Download from [Flux releases](https://github.com/synheart-ai/synheart-flux/releases):

| Artifact | Platform | Contents |
|----------|----------|----------|
| `synheart-flux-android-jniLibs.tar.gz` | Android | JNI libs for arm64-v8a, armeabi-v7a, x86_64 |

## Installation

### Manual Installation

1. Download `synheart-flux-android-jniLibs.tar.gz` from the releases page
2. Extract to `vendor/flux/android/jniLibs/`:
   ```bash
   tar -xzf synheart-flux-android-jniLibs.tar.gz -C vendor/flux/android/jniLibs/
   ```

### CI/CD Integration

On SDK release, CI should:

1. Read the Flux version from `VERSION`
2. Download Flux artifacts from GitHub Releases by tag
3. Place them into the appropriate directories
4. Build SDK artifacts
5. Publish SDK

Example CI script:

```bash
FLUX_VERSION=$(cat vendor/flux/VERSION)
FLUX_BASE_URL="https://github.com/synheart-ai/synheart-flux/releases/download/${FLUX_VERSION}"

# Download and extract Android JNI libs
curl -L "${FLUX_BASE_URL}/synheart-flux-android-jniLibs.tar.gz" -o /tmp/flux-android.tar.gz
tar -xzf /tmp/flux-android.tar.gz -C vendor/flux/android/jniLibs/
```

## Versioning

When updating Flux:

1. Update the `VERSION` file with the new tag (e.g., `v0.2.0`)
2. CI will automatically fetch the new binaries on next release
3. Add to release notes: "Bundled Flux: vX.Y.Z"

## JNI Function Mapping

The native library (`libsynheart_flux.so`) exports the following JNI functions:

| Kotlin Method | JNI Function |
|--------------|--------------|
| `nativeWhoopToHsiDaily` | `Java_ai_synheart_wear_flux_FluxFfi_nativeWhoopToHsiDaily` |
| `nativeGarminToHsiDaily` | `Java_ai_synheart_wear_flux_FluxFfi_nativeGarminToHsiDaily` |
| `nativeProcessorNew` | `Java_ai_synheart_wear_flux_FluxFfi_nativeProcessorNew` |
| `nativeProcessorFree` | `Java_ai_synheart_wear_flux_FluxFfi_nativeProcessorFree` |
| `nativeProcessorProcessWhoop` | `Java_ai_synheart_wear_flux_FluxFfi_nativeProcessorProcessWhoop` |
| `nativeProcessorProcessGarmin` | `Java_ai_synheart_wear_flux_FluxFfi_nativeProcessorProcessGarmin` |
| `nativeProcessorSaveBaselines` | `Java_ai_synheart_wear_flux_FluxFfi_nativeProcessorSaveBaselines` |
| `nativeProcessorLoadBaselines` | `Java_ai_synheart_wear_flux_FluxFfi_nativeProcessorLoadBaselines` |
| `nativeLastError` | `Java_ai_synheart_wear_flux_FluxFfi_nativeLastError` |

## Graceful Degradation

If the native library is not available at runtime:

- `FluxFfi.isAvailable` returns `false`
- All processing methods return `null`
- The SDK continues to function without Flux features
- Check `FluxFfi.getLoadError()` for details on why loading failed

## Current Implementation Note

The Wear SDK calls the **native Rust Flux library via JNI**.

- The Rust binaries are **not meant to be checked into git**. CI/CD should download
  them from Flux GitHub Releases (pinned by `vendor/flux/VERSION`) right before publishing.
- If the native binaries are missing at runtime, Flux will not be available (see
  `isFluxAvailable` / `fluxLoadError` in the public Flux API).
