# VideoFlow Android Step 2 — Proxy Architecture

## Purpose

Proxies are derived editing media. They improve preview/edit responsiveness while the original SAF/content-URI asset remains the future render source. A proxy must never silently replace the original as final-render truth.

## Generation

`ProxyManager` uses native Android Media3 `Transformer`, H.264 video and AAC audio. Source media is consumed progressively by the media stack and output is written directly to an app-private MP4; VideoFlow does not read the source into a source-sized byte array.

Quality modes are:

- PERFORMANCE: target 540p.
- BALANCED: target 720p.
- HIGH: target 1080p.

The source is not upscaled when it is already below the requested height. Width is aspect-ratio derived and made encoder-friendly.

## Concurrency and progress

A mutex permits one proxy generation operation at a time, bounding expensive transcoding work. Media3 progress is reported when available; otherwise the UI remains indeterminate rather than fabricating percentages.

## Storage

Proxy files live under app-private `filesDir/proxies`; binary proxy data is not stored in Room. Before generation, `StatFs` verifies estimated capacity plus 25% safety headroom. Failed/cancelled jobs remove incomplete output.

## Persistence and source binding

Room stores proxy metadata including asset id, path, dimensions, codec, quality, source fingerprint, state, creation time and size. States include NONE, QUEUED, GENERATING, READY, FAILED and STALE.

The proxy filename/entity binds to the source fingerprint. `reconcile()` marks a previously READY proxy STALE when the source is changed, the fingerprint differs or the proxy file disappears.

## Cancellation, deletion and regeneration

Cancellation calls the active Media3 Transformer cancellation path, removes incomplete media and never promotes it to READY. Delete removes only derived proxy data; the original asset is untouched. Regeneration creates a fresh derived file after normal validation/storage checks.

## Original offline

`PreviewPlan` can select a valid READY proxy without dereferencing an unavailable original. The editor therefore supports the intended "Original Offline — Editing Using Proxy" path when proxy identity is still trustworthy. Final export from a proxy is outside Step 2 and must not be implemented accidentally.
