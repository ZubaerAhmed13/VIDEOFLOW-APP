# VideoFlow UI Step 3 — Product UX Architecture

## Scope

This document describes the final dedicated UI/UX phase on branch `ui-step3-product-polish`. It builds on the certified UI Step 2 base `7f29a708ab24b9be3c9b4f48f381e26e44dcf987` and does not replace the native editor, PreviewPlan/RenderPlan, source identity, proxy, or native export architecture.

## Product Flow

The primary flow is:

`Onboarding → Home → New Project / Existing Project → Editor → Export → Progress → Complete → Open / Share / Done`

Home opens projects directly in the editor. The existing project-detail/media-bin screen remains available through the project overflow action instead of being a mandatory navigation step.

## First Run

The first-run introduction contains three screens only:

1. Edit directly on your device.
2. Built for large media.
3. Start creating.

Completion is stored in Preferences DataStore. The introduction can be shown again from Settings without resetting the first-run completion state.

## Home

Home prioritizes:

1. VideoFlow identity and local-first positioning.
2. New Project.
3. Recent Projects.
4. Settings.

Projects are supplied by the existing project repository, which already orders them by most recently updated. Cards use user-facing metadata only: name, available duration/resolution metadata, media count, and modified time. Database identifiers, content URIs, fingerprints, project-format version, and codec internals are not displayed.

Project actions are Rename, Project Details, and Delete. Delete does not issue any delete operation against the original SAF source URI. Room relationships remove saved project/editor records. File-system cache cleanup remains limited to existing backend behavior and is not overstated in UI copy.

## New Project

Manual creation exposes a name plus real canvas presets backed by existing `project_settings` persistence:

- 16:9 — 1920×1080
- 9:16 — 1080×1920
- 1:1 — 1080×1080
- 4:5 — 1080×1350

No project database schema change is required. `EditorRepository.ensureProjectInitialized()` creates the existing settings row and the Step 3 project setup updates that row.

### Start from Media

Start from Media uses Android `OpenDocument`. Picker cancellation creates no project. After a selection:

- project is created;
- existing SAF import persists access where supported;
- source metadata/fingerprint flow remains authoritative;
- project canvas is adapted to source aspect/dimensions up to a 1920-pixel long edge when metadata exists;
- common rational source frame rates are preserved (including 29.97 and 59.94);
- video/audio sources are inserted into the existing timeline at time zero;
- original media is referenced, not copied.

If creation/import fails, the newly-created project is removed while the original source is left unchanged.

## Editor Relationship

UI Step 1/2 editor architecture is protected. Step 3 does not return to a parent `LazyColumn`, permanent media bin, permanent proxy controls, or giant property form. The fixed preview / transport / timeline / contextual-tool architecture remains in place. Step 3 changes product navigation and surrounding language rather than reimplementing editing operations.

## Export Relationship

The Step 3 product shell routes to a new recommended-first export presentation over the existing `ExportViewModel`, `ExportRepository`, RenderPlan compiler, capability validator, foreground export service, coordinator, encoder and muxer.

See `EXPORT_UX_ARCHITECTURE.md`.

## Settings

Settings keeps technical device information under Device Capability and Diagnostics. Appearance is a real DataStore preference. Privacy copy reflects the manifest and local-first architecture. Proxy/storage controls are only exposed when they perform real backend work; placebo switches are prohibited.

## Responsive Layout

Home and Export use larger horizontal margins from 600dp upward. The protected editor already has compact-landscape and expanded arrangements; Step 3 retains those behaviors rather than scaling a phone-only page to large screens.

## Errors and Loading

Product copy avoids exception class names, source-status enum names, raw content URIs, raw bitrates, and renderer class names. Export problems are mapped to recovery-oriented titles and messages. Real source/preflight problems are preserved while export settings are recomputed.

## Data / Schema

- No new Room schema version.
- No project format increment solely for UI Step 3.
- App UI preferences use Preferences DataStore.
- Original media remains SAF/content-URI based.
- Final render continues to resolve original sources.
