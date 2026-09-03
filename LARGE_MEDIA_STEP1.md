# Large Media Architecture — Step 1

VideoFlow Android has no artificial source-file-size cap. Practical limits belong to the selected document provider/filesystem, free storage, Android codec stack, and device hardware.

## Reference-based import

The normal import path stores a `content://` URI and attempts a persistable read grant. It never copies the original video into app-private storage.

## 64-bit safety

Source size, media duration, byte offsets, counters, available storage, and fingerprint sample locations use `Long`. Automated tests exercise 500 MB, 2 GB, 3 GB, 5 GB, 10 GB and 100 GB logical values, including offsets beyond `Int.MAX_VALUE`.

## Bounded fingerprint and revalidation

Algorithm: `VideoFlowSampleSHA256-v1`.

Digest input includes algorithm label, source size, duration, width and height. Files larger than 12 MiB are sampled in three 4 MiB regions: first, centered middle, and final. Working buffers are 256 KiB. Smaller files are streamed through bounded chunks rather than allocated as one source-sized byte array.

Random access uses `ParcelFileDescriptor` → `FileInputStream` → `FileChannel.position(Long)`. If a provider cannot expose stable size or reliable random seek, VideoFlow degrades to a first-region-only bounded fingerprint and records `WEAK_FIRST_REGION_ONLY`; it does not silently copy the full source.

Project-open source revalidation and Locate Original relink reuse the same bounded fingerprint architecture. Detecting `SourceStatus.CHANGED` therefore does not require a full-file hash, a source-sized cache, or a source-sized RAM allocation.

## Structural automated evidence

- 64-bit size/offset tests cover logical values through 100 GB.
- Strong fingerprint tests prove first, middle, and final sampled regions affect identity.
- Small-file full fingerprinting uses bounded streaming chunks.
- Non-seekable/provider-limited behavior is tested as weak identity.
- Room persistence includes 10 GB media-size metadata.
- Repository instrumentation verifies that the same logical `content://` URI changing underlying media becomes `CHANGED`.

These tests establish architecture and decision correctness; they do not substitute for physical-device large-media measurement.

## Real-device evidence

A genuine encoded >3 GB Android document is still required to verify actual-device import, preview, 25/50/75/95% seek, force-stop/reopen, source-storage delta, memory behavior, actual codec capability reporting, and provider-backed persisted URI behavior.

Until those measurements are supplied, the real-device rows remain **NOT VERIFIED** and overall Step 1 remains **PARTIAL** even when automated certification is fully green.
