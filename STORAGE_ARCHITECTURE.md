# Storage Architecture — Step 1

Date: 2026-09-03

Certified software baseline: `9e414a65afa8a0a6235a794f056e61846b631bce`, run #43 (`33746474694`).

## Source ownership — PASS

The user/document provider owns the original media. VideoFlow persists the selected `content://` URI plus technical/identity metadata; it does not resolve legacy absolute filesystem paths and does not request broad external-storage management.

## SAF permission model — PASS

VideoFlow uses Android `OpenDocument` and attempts `takePersistableUriPermission()` for read access. Whether persistence is confirmed is stored in `permissionPersisted`; provider limitations are not hidden.

## Room — PASS

Room stores project fields, URI text, media metadata, fingerprint metadata, source status and timestamps. Original media bytes are not stored in Room. Project deletion cascades media-reference rows only and never deletes the external original.

## Large-source safety — PASS

Step 1 creates no source-sized import copy, Room BLOB, full-file byte array or source-sized cache in the normal media import/verification/relink paths. Sizes and offsets use `Long`. Large-file identity reads bounded sample regions.

## Duplicate safety — PASS

A duplicate import candidate is held in memory as metadata/identity state only. The second Room row is not written before explicit Add Anyway confirmation. Cancel leaves storage unchanged. The prepared candidate contains no giant media buffer.

## Relink storage update — PASS

After strong relink or explicitly confirmed weak relink, the media row is refreshed with the selected URI, persisted-permission result, fingerprint SHA/algorithm/strength/sampled bytes/note and current technical metadata. Stale identity data from the previous URI is not retained as if still current.

## Source status persistence — PASS

Current source verification persists justified transitions among AVAILABLE, CHANGED, MISSING, PERMISSION_LOST, CORRUPTED, UNSUPPORTED and UNKNOWN.

## Backup / data extraction — PASS

VideoFlow uses `android:allowBackup="false"` and modern data-extraction rules that exclude app data from cloud backup and device transfer. Project/source-reference metadata is therefore not deliberately uploaded through Android backup.

## Physical storage proof

The architecture/static audit is **PASS**, but Android Settings app-storage before/after measurement with a genuine encoded >3 GB source is **NOT VERIFIED**. That physical measurement is required to close the Step 1 storage certification gate.
