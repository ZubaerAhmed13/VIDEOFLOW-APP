# Large Media Architecture — Step 1

Date: 2026-09-03

Automated certification baseline: main commit `9e414a65afa8a0a6235a794f056e61846b631bce`, run #43 (`33746474694`).

## Structural evidence — PASS

VideoFlow has no artificial application source-file-size cap. The 3 GB requirement is a target to certify, not a hard-coded limit.

The normal import path stores a SAF `content://` reference and technical/identity metadata. It does not copy the original source into app-private storage.

Media size, duration, byte offsets, counters and relevant storage values use 64-bit `Long`. Automated tests exercise 500 MB, 2 GB, 3 GB, 5 GB, 10 GB and 100 GB logical values and offsets above `Int.MAX_VALUE`.

## Bounded fingerprinting — PASS

Algorithm: `VideoFlowSampleSHA256-v1`.

For large media, the strong random-access path samples:

- first 4 MiB
- centered middle 4 MiB
- final 4 MiB

Normal total sampled source data is approximately 12 MiB, using bounded 256 KiB working buffers. Smaller files use deterministic bounded streaming rather than source-sized allocation.

Random access uses Android file descriptors and `FileChannel.position(Long)`. A provider that lacks stable size/reliable random seek falls back to a bounded first-region fingerprint and persists `WEAK_FIRST_REGION_ONLY`; VideoFlow does not compensate by copying or fully hashing a huge source.

## Source revalidation — PASS

Project/source verification reuses the same bounded identity system. It does not full-hash a 3 GB/20 GB source and is not triggered by Compose recomposition. Project-open verification uses background IO and small bounded concurrency.

## Structural certification evidence

| Requirement | Status |
|---|---|
| No application 3 GB cap | PASS |
| 64-bit file sizes/offsets | PASS |
| 100 GB logical fingerprint fixture | PASS |
| Offsets beyond 32-bit range | PASS |
| Approx. 12 MiB strong large-file sampling | PASS |
| 256 KiB working buffers | PASS |
| No whole-source `readBytes()` import path | PASS |
| No source-sized ByteArray pattern | PASS |
| Reference-based SAF source model | PASS |
| 10 GB size metadata Room reopen | PASS |
| Strong first/middle/end mutation sensitivity | PASS |

## Real-device evidence

A structural fixture does not prove real Android provider/decoder/device behavior. The following remain physical certification gates:

| Requirement | Status |
|---|---|
| Genuine encoded >3 GB Android document | NOT VERIFIED |
| >3 GB import on physical device | NOT VERIFIED |
| >3 GB preview | NOT VERIFIED |
| 25/50/75/95% late seek | NOT VERIFIED |
| App-storage before/after delta | NOT VERIFIED |
| No source-sized physical storage increase | NOT VERIFIED |
| Force-stop/reopen | NOT VERIFIED |
| Reboot persisted-URI behavior | NOT VERIFIED |
| Physical memory measurement | NOT VERIFIED |
| Thermal observation | NOT VERIFIED |

## Conclusion

Large-media architecture and automated structural certification: **PASS**.

Genuine >3 GB physical-device certification: **NOT VERIFIED**.

Overall Step 1 remains **PARTIAL** until the physical evidence is completed.
