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

Media metadata analysis and fingerprinting execute on `Dispatchers.IO`. Import work is owned by ViewModel scope so cancellation follows lifecycle destruction.

The source-of-truth media identity remains the Android document URI plus its persisted metadata/fingerprint. There is no browser filesystem layer, WebView, IndexedDB, service worker, or FFmpeg/WASM path in the Android architecture.
