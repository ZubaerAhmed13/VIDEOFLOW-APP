# VideoFlow Settings Architecture

## Principle

Settings must expose only real behavior or accurate read-only product information. UI Step 3 does not add placebo switches.

## Sections

### Editing

The product explains that timeline snapping, track controls and contextual editing are handled in the project editor. Step 3 does not duplicate project editing state into global preferences.

### Performance

Proxy editing is explained in user language. The Settings screen reads actual proxy rows and `sizeBytes` values from the existing proxy database through `ProductSettingsViewModel`.

**UI Step 3 product decision:** VideoFlow does not expose a global proxy on/off or proxy-quality preference because the current backend has no global policy that consumes such a setting. Adding that switch would be a placebo control. The product therefore exposes a clear read-only `Proxy policy`, actual stored proxy usage, and real derived-data cleanup only. Per-asset/project proxy generation continues to use the existing proxy backend, while final export resolves original source media.

`Clear Editing Proxies` is a real destructive derived-data action:

1. enumerate current project media assets;
2. call the existing `ProxyManager.delete(assetId)` path;
3. remove the derived proxy file where present;
4. remove the proxy row;
5. recalculate proxy usage;
6. report completion without claiming original media was deleted.

The confirmation explicitly states that original media and project edits remain unchanged.

### Storage

Storage language distinguishes original media from project/derived data. No raw filesystem paths are exposed to normal users.

### Export

Settings documents the recommended export policy rather than persisting an unsafe device-specific codec/resolution combination globally. Per-export Advanced Settings remain the authority.

### Appearance

Appearance is a genuine Preferences DataStore setting with:

- System
- Light
- Dark

It is intentionally not stored in Room or the project format.

### Accessibility

Settings explains that Android font scaling and animation settings are respected. UI Step 3 does not duplicate system font controls.

### Privacy

Routes to the native Privacy page. No WebView is used.

### Device Capability and Diagnostics

Technical codec/device information remains separated from Home and normal editing/export flow.

### Introduction / About

The three-screen introduction can be replayed without changing the persisted first-run completion state. About displays app version/build and a concise local-first summary.

## Persistence

Preferences DataStore file: `videoflow_product_preferences`.

Current Step 3 keys:

- `onboarding_complete`
- `appearance`

No proxy preference key exists by design because no global backend proxy policy consumes one.

No Room migration or project-format increment is introduced solely for these UI preferences.

## Safety

Global Settings never:

- delete original SAF source media;
- silently change export settings for an active job;
- claim automatic proxy decisions that the backend does not implement;
- claim storage capacity for a SAF provider that does not report it;
- add network telemetry.
