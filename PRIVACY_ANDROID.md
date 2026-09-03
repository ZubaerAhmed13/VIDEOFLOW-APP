# VideoFlow Android Privacy — Step 1

Date: 2026-09-03

Certified software baseline: main commit `9e414a65afa8a0a6235a794f056e61846b631bce`, run #43 (`33746474694`).

## Local/offline foundation — PASS

- Media analysis, bounded identity hashing and Media3 playback run locally on the Android device.
- No media-upload path is implemented in Step 1.
- Main application manifest does not request `android.permission.INTERNET`.
- No advertising SDK, analytics SDK, remote telemetry or remote crash-upload subsystem is included.
- No broad `MANAGE_EXTERNAL_STORAGE` permission is requested.
- No legacy broad external-storage permission is required by the Step 1 SAF architecture.
- Only user-selected document read access is used for media sources.

## Project/source-reference data — PASS

Project metadata, source URIs, technical metadata, source status and fingerprint metadata are stored locally in Room. The original media remains with the user's Android document provider and is not copied into the database.

## Backup/data extraction — PASS

VideoFlow keeps `android:allowBackup="false"` and configures modern Android data-extraction rules to exclude app data from cloud backup and device transfer. The privacy intent is no cloud backup of project/source-reference metadata in Step 1.

## Diagnostics — PASS

Local diagnostics are bounded and designed to avoid content URI/filename logging. Device diagnostics use technical properties such as API level, ABI, approximate RAM/free storage and codec capability rather than unique advertising identifiers.

## Debug-only provider — PASS

The content provider used for automated media fixtures exists only in the debug source set. It is not part of the Step 1 release APK and is not a production media-sharing interface.

## Security re-audit — PASS

- INTERNET permission absent from main: PASS
- MANAGE_EXTERNAL_STORAGE absent: PASS
- unnecessary broad storage permission absent: PASS
- launcher activity is the intended exported main component: PASS
- production/main FileProvider not introduced: PASS
- no cloud-backup policy accidentally enabled: PASS

Physical-device confirmation of Android Settings behavior and launcher installation is **NOT VERIFIED** and belongs to the physical certification record.
