# Android Architecture — Step 1

```text
Jetpack Compose UI
        ↓
Hilt ViewModels + lifecycle-aware StateFlow
        ↓
ProjectRepository / DeviceCapabilityRepository
        ↓
┌────────────────────┬────────────────────────┬──────────────────┐
│ Room               │ SAF / ContentResolver  │ Native Media     │
│ ProjectEntity      │ content:// references  │ MediaExtractor   │
│ MediaAssetEntity   │ persisted read grants  │ Media3/ExoPlayer │
└────────────────────┴────────────────────────┴──────────────────┘
                           ↓
                   bounded media fingerprint
                   first / middle / end SHA-256
```

Hilt supplies the Room database and application-scoped repositories/services. UI code does not construct storage/database components and does not perform media I/O on the main thread.

Room schema version and `projectFormatVersion` both start at 1. Media rows are one-to-many under projects with cascade deletion limited to database rows; deleting a VideoFlow project never deletes the user's original media.

Media metadata analysis and fingerprinting execute on `Dispatchers.IO`. Import and verification work is owned by ViewModel scope so obsolete project checks are cancelled with lifecycle/project changes. Project-open revalidation is limited to two concurrent media checks to avoid saturating storage when a project contains several sources.

The source-of-truth media reference remains the Android document URI plus persisted metadata/fingerprint identity. A readable URI is not automatically trusted as unchanged: project-open verification recalculates the existing bounded fingerprint and compares fingerprint strength plus critical technical metadata. Definitive mismatch becomes `SourceStatus.CHANGED` and playback is blocked until the original is safely located.

Duplicate analysis is performed before Room insertion. A duplicate candidate remains an in-memory metadata/reference object until the user chooses **Add Anyway**; cancellation leaves Room unchanged and confirmation reuses the prepared analysis instead of re-reading the source.

Relinking is confidence-aware. Strong identities may reconnect automatically only on strong exact match, provider-limited weak matches require explicit confirmation, and unverifiable or contradictory replacements are not accepted as exact originals. See `SOURCE_IDENTITY_STEP1.md`.

There is no browser filesystem layer, WebView, IndexedDB, service worker, FFmpeg/WASM path, source-sized import copy, or whole-source RAM load in the Android Step 1 architecture.
