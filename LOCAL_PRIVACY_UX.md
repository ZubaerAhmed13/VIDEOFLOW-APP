# VideoFlow Local Privacy UX

## Product Position

VideoFlow is local-first. The app works with media selected through Android's document framework and the VideoFlow codebase does not add a VideoFlow-server upload step.

## Accurate User Language

The Privacy screen uses these principles:

- `VideoFlow processes media locally on this device.`
- `VideoFlow does not upload your video to a VideoFlow server.`
- `Files are selected through Android's document picker.`

VideoFlow does **not** claim that every selected document provider is physically local. A user may choose a cloud-backed Android SAF provider; that provider's behavior is outside VideoFlow.

## Source Access

Import continues to use the established SAF/content-URI architecture. VideoFlow references original media instead of copying the entire source on import. Persistable access is requested where the provider supports it.

## Network Audit

UI Step 3 does not introduce:

- `android.permission.INTERNET`;
- WebView;
- remote fonts/icons;
- cloud rendering;
- Firebase Analytics;
- Crashlytics;
- Sentry reporting;
- Mixpanel;
- Amplitude;
- Segment;
- equivalent network telemetry.

The exact-head CI architecture audit fails if INTERNET, WebView or the listed telemetry families appear in the audited application source/build configuration.

## Diagnostics Privacy

Normal Home/Editor/Export UI does not present raw content URIs, source fingerprints, internal database IDs or renderer implementation names. Technical information belongs in Diagnostics. Diagnostic output should remain sanitized before being copied or shared.

## Open / Share

Export results use Android content URIs and temporary read grants for Open/Share. No `file://` URI is introduced.

## Deletion

Deleting a VideoFlow project removes the project and its saved editing data through the existing Room relationships. The Step 3 project-delete action does not issue a deletion against original user-selected source media.

Clearing editing proxies removes VideoFlow-derived proxy files/rows through the existing proxy manager. Original media, project edits and completed exports are not targeted by that action.

## Scope Boundary

This document describes application behavior and UI claims. It does not claim that Android, the selected document provider, another installed app used for sharing, or the operating system itself has no network access.
