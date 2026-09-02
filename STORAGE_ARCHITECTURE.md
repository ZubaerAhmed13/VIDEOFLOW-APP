# Storage Architecture — Step 1

The selected media `content://` URI is the source of truth. VideoFlow does not resolve legacy absolute filesystem paths and does not request broad external-storage access.

The app launches Android `OpenDocument` for video selection and attempts `takePersistableUriPermission()` for read access. Some providers may not grant persistence; this is stored honestly in `permissionPersisted` rather than assumed.

Room stores project fields, URI text, technical metadata, fingerprint metadata, source status and timestamps. Room never stores original media bytes.

Step 1 intentionally does not create source-sized caches, Room BLOBs, or full-file byte arrays. Large-file fingerprinting reads only bounded sample regions.
