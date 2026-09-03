# Android Architecture — Step 1

Date: 2026-09-03

Certified software baseline: main commit `9e414a65afa8a0a6235a794f056e61846b631bce`, GitHub Actions run #43 (`33746474694`).

```text
Jetpack Compose / Material 3 UI
        ↓
Hilt ViewModels + lifecycle-bound coroutine jobs
        ↓
ProjectRepository / DeviceCapabilityRepository
        ↓
┌────────────────────┬──────────────────────────┬─────────────────────┐
│ Room               │ Android SAF             │ Native Media        │
│ ProjectEntity      │ content:// references   │ MediaExtractor      │
│ MediaAssetEntity   │ persistable read grant  │ Media3 / ExoPlayer  │
└────────────────────┴──────────────────────────┴─────────────────────┘
        ↓                         ↓                         ↓
project/source state      bounded source identity       native preview/seek
                         + safe relink decisions
```

## Native boundary — PASS

VideoFlow Step 1 is a native Kotlin Android application. It contains no WebView/browser-wrapper architecture, IndexedDB, service worker, FFmpeg/WASM or browser File System API path.

## Persistence — PASS

Room schema version and project format version are 1. Projects own one-to-many media-reference rows with database cascade deletion only. Deleting a VideoFlow project never deletes the user's original document.

## Source architecture — PASS

The source of truth is the Android document URI. VideoFlow uses SAF/OpenDocument, `ContentResolver`, `ParcelFileDescriptor`/FileDescriptor and native media APIs. The original media is not imported into a Room BLOB or source-sized app-private cache.

## Source identity — PASS

`MediaIdentity` includes fingerprint SHA, fingerprint strength, known size, duration, dimensions and video codec. Fingerprint strength is a decision input, not merely diagnostic metadata.

Identity outcomes are explicit:

- `STRONG_MATCH`
- `WEAK_MATCH`
- `MISMATCH`
- `UNVERIFIABLE`

Strong identities are `FULL_SMALL_FILE` and `STRONG_THREE_REGION`. `WEAK_FIRST_REGION_ONLY` requires explicit confirmation for relink and is never silently promoted to strong. `UNAVAILABLE` is never described as an exact cryptographic match.

## CHANGED verification — PASS

At project-open/source-verification boundaries, accessible media is re-analyzed and re-fingerprinted on background IO. Definite fingerprint or critical-metadata difference becomes `SourceStatus.CHANGED` and is persisted. Verification is not launched by Compose recomposition. Multiple assets are verified with a small bounded concurrency limit and work is cancellable with ViewModel lifecycle.

## Duplicate import — PASS

Analyze/fingerprint precede persistence. A duplicate is returned as an in-memory prepared candidate; no second media row exists before explicit Add Anyway confirmation. Cancel performs no Room write. Add Anyway persists the prepared candidate without repeating expensive media analysis.

## Relink — PASS

Relink analyzes the selected URI, fingerprints it, applies strength-aware identity policy and only auto-accepts a strong match. A weak compatible result requires explicit user confirmation. Mismatch is rejected. Confirmed relink replaces stale URI, permission, fingerprint and technical metadata with the current selected-source identity.

## Threading and resource bounds — PASS

Room/media/fingerprint work is routed away from the main UI path. Debug StrictMode remains enabled. Fingerprinting is bounded; no whole-source RAM load is used. Media3 player lifecycle is released with Compose/lifecycle disposal.

## Step 1 scope

Timeline editing, proxy generation, production rendering/export, GPU composition, AI/Watermark Studio, advanced audio, recorder/camera and production signing are **NOT APPLICABLE** to Step 1.

## Certification

- Unit tests: **PASS — 23/23**
- API-35 instrumentation: **PASS — 16/16**
- Lint: **PASS**
- Debug APK: **PASS**
- Test-signed Release APK: **PASS**
- Physical genuine >3 GB device certification: **NOT VERIFIED**

Overall Step 1: **PARTIAL** until physical-device gates are measured.
