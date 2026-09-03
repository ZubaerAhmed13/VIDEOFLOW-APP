# Storage Architecture — Step 1

The selected media `content://` URI is the source of truth. VideoFlow does not resolve legacy absolute filesystem paths and does not request broad external-storage access.

The app launches Android `OpenDocument` for video selection and attempts `takePersistableUriPermission()` for read access. Some providers may not grant persistence; this is stored honestly in `permissionPersisted` rather than assumed.

Room stores project fields, URI text, technical metadata, fingerprint metadata, source status and timestamps. Room never stores original media bytes.

Step 1 intentionally does not create source-sized caches, Room BLOBs, or full-file byte arrays. Large-file fingerprinting reads bounded sample regions only. Source revalidation reuses that same bounded fingerprint path and therefore does not convert project opening into a full 3 GB/20 GB hash.

Duplicate candidates are analyzed and fingerprinted before persistence. If an existing project asset matches, the candidate remains only as lightweight in-memory metadata/reference state. **Cancel** leaves Room unchanged; **Add Anyway** inserts the prepared reference without repeating expensive analysis.

A successful relink replaces the persisted URI and its complete current identity snapshot: permission result, technical metadata, fingerprint SHA, fingerprint strength, sampled-byte count, fingerprint note, and source status. Stale identity data from the previous URI is not retained.

Android application backup and device-transfer extraction are disabled/excluded for Step 1 project/source-reference data. This privacy rule does not change the SAF ownership model: original media continues to live with the user's document provider rather than in VideoFlow private storage.
