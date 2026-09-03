# VideoFlow Android — Source Identity & Safe Relinking (Step 1)

## Purpose

Step 1 stores a `content://` document reference and an identity snapshot for each media source. A URI being readable is not, by itself, proof that it still points to the original content. Project-open verification therefore re-analyzes technical metadata and recalculates the existing bounded fingerprint on an IO dispatcher.

This verification is initiated at a project/source lifecycle boundary, not by Compose recomposition. The ViewModel cancels obsolete project-load verification and limits source checks to two concurrent jobs so multiple media assets do not saturate storage.

## Identity model

Identity includes the fingerprint SHA-256, fingerprint strength, known source size, duration, width, height, and primary video codec MIME when available.

### Strong identity

`FULL_SMALL_FILE` and `STRONG_THREE_REGION` are strong identities.

- `FULL_SMALL_FILE` hashes the complete small source with bounded streaming chunks.
- `STRONG_THREE_REGION` hashes bounded first, middle, and final regions for large sources using the existing `VideoFlowSampleSHA256-v1` algorithm.
- A strong saved source is `AVAILABLE` only when current strong identity matches and known critical metadata is not contradictory.
- A definitive strong hash or critical metadata mismatch is `CHANGED`.
- If a previously strong source can only be weakly re-read from the provider, VideoFlow reports identity as unverified (`UNKNOWN`) rather than falsely calling it changed or available.

### Weak identity

`WEAK_FIRST_REGION_ONLY` is provider-limited identity. The provider did not expose reliable random access or stable size, so only a bounded first region can be hashed.

- A weak hash mismatch or contradictory known technical metadata is a mismatch / `CHANGED`.
- A weak hash match with compatible known metadata is only `WEAK_MATCH`.
- `WEAK_MATCH` is never automatically treated as an exact original.
- Relinking a weak match requires explicit user confirmation with **Use This Source**.
- Weak identity is persisted as weak after relink; it is never silently promoted to strong.

### Unavailable identity

`UNAVAILABLE` means VideoFlow does not have a cryptographic fingerprint for that source. Accessibility and known technical metadata can still be checked, but VideoFlow must not describe a replacement as an exact fingerprint match. Ordinary Step 1 relink refuses automatic acceptance when identity is unverifiable.

## Source status decisions

Project/source revalidation follows this order:

1. Open the saved URI.
2. If access fails, classify `MISSING` or `PERMISSION_LOST` where distinguishable.
3. Parse current tracks/technical metadata using `MediaExtractor`.
4. Recalculate the bounded fingerprint using the existing fingerprint service.
5. Compare current identity with the saved identity.
6. Persist the resulting source status in Room.

Parser failures remain `CORRUPTED` / `UNSUPPORTED`; unexpected failures remain `UNKNOWN`.

`CHANGED` blocks automatic playback and presents **Locate Original** rather than playing accessible-but-untrusted replacement content.

## Relink policy

Relink decisions are classified as:

- `STRONG_MATCH` — strong fingerprint match and no contradictory critical metadata; may reconnect automatically.
- `WEAK_MATCH` — provider-limited weak fingerprint and compatible metadata; requires explicit user confirmation.
- `MISMATCH` — definitive fingerprint or critical metadata mismatch; relink rejected.
- `UNVERIFIABLE` — insufficient identity evidence; not accepted as an exact original.

After a successful strong or explicitly confirmed weak relink, VideoFlow persists the replacement URI, persisted-permission result, current technical metadata, fingerprint SHA, fingerprint strength, sampled byte count, fingerprint note, and `AVAILABLE` source state. Stale fingerprint metadata is not retained.

## Large-media invariant

Source revalidation and relinking do not hash or copy an entire large media source. Large-file fingerprint work remains bounded to approximately 12 MiB (4 MiB beginning + 4 MiB middle + 4 MiB end) with bounded working buffers. No arbitrary 3 GB cap, source-sized RAM allocation, or source-sized application-storage copy is introduced by identity verification.
