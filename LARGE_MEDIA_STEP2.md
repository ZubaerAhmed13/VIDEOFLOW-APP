# VideoFlow Android Step 2 — Large-Media Safety

## Preserved Step 1 source architecture

Step 2 keeps original media reference-based through SAF/content URIs and persisted permissions where providers permit. Original video/audio is not copied into project storage on import. Source sizes and edit times use 64-bit values, and no artificial 3 GB/4 GB source limit is introduced.

Production source code continues to reject source-sized `readBytes()`/file-size `ByteArray` patterns through CI.

## Derived media is bounded

Proxy generation is streaming through native Media3 Transformer and serialized through a single proxy mutex. App-private proxy output has a storage precheck and incomplete files are removed on cancellation/failure.

Thumbnail generation is capped to a small target (default 320 px; hard accepted range 64–1024 px), uses two decoder slots, caches by source identity/time bucket and caps the cache at 512 files / 128 MiB.

Waveform generation uses one decoder slot and retains at most 4096 float peaks per requested waveform. It never holds the whole compressed source in memory.

## Timeline behavior

Timeline state stores only references, metadata and edit parameters. A six-hour Long-timebase case and a 100-clip/5-track/>2-hour structural case are unit-tested. Large media therefore does not scale editor RAM with source file size.

## Physical certification

The architecture preserves the accepted Step 1 multi-gigabyte reference workflow, but Step 2's required real-device multi-GB timeline + proxy + edit acceptance scenario cannot be certified by GitHub-hosted CI. Until evidence from a physical Android device is recorded, this item is **NOT VERIFIED** and Step 2 overall status remains **PARTIAL** rather than COMPLETE.
