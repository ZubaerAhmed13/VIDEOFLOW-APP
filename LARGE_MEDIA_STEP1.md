# Large Media Architecture — Step 1

VideoFlow Android has no artificial source-file-size cap. Practical limits belong to the selected document provider/filesystem, free storage, Android codec stack, and device hardware.

## Reference-based import

The normal import path stores a `content://` URI and attempts a persistable read grant. It never copies the original video into app-private storage.

## 64-bit safety

Source size, media duration, byte offsets, counters, available storage, and fingerprint sample locations use `Long`. Automated tests exercise 500 MB, 2 GB, 3 GB, 5 GB, 10 GB and 100 GB logical values, including offsets beyond `Int.MAX_VALUE`.

## Bounded fingerprint

Algorithm: `VideoFlowSampleSHA256-v1`.

Digest input includes algorithm label, source size, duration, width and height. Files larger than 12 MiB are sampled in three 4 MiB regions: first, centered middle, and final. Working buffers are 256 KiB. Smaller files are streamed through bounded chunks rather than allocated as one source-sized byte array.

Random access uses `ParcelFileDescriptor` → `FileInputStream` → `FileChannel.position(Long)`. If a provider cannot expose stable size or reliable random seek, VideoFlow degrades to a first-region-only bounded fingerprint and records `WEAK_FIRST_REGION_ONLY`; it does not silently copy the full source.

## Certification status

Automated structural `Long` / offset coverage: **PASS**.

GitHub Actions run `33734581374` (run #8) also passed the Android build, unit, lint and API-35 instrumentation gates, including a Room persistence test that stores and reloads 10 GB media-size metadata.

A genuine encoded >3 GB physical-device import/playback/late-seek/save/reopen test, source-storage delta measurement, reboot-persistence test, and profiler memory/thermal measurement remain **NOT VERIFIED** physical-device certification gates and must not be reported as PASS until actually measured.
