# VideoFlow Review APK signing

`VideoFlow Review` is an internal/manual-test build variant only.

- Application ID: `com.videoflow.app.review`
- Display label: `VideoFlow Review`
- Signing alias: `videoflow-review`
- Certificate SHA-256: `F3:D4:E6:6B:35:08:00:BC:A7:39:B2:C5:F6:F4:D2:C7:F1:5C:7D:C8:9B:1B:87:63:BD:51:46:8A:B7:15:0C:C7`
- The base64 keystore material in `review-test.keystore.b64` is intentionally non-production and is used only for the `.review` package.
- The decoded `review-test.keystore` is ignored by the repository-wide `*.keystore` rule.

## Purpose

GitHub-hosted runners generate a fresh default Android debug keystore when one is not restored. That makes separately downloaded debug APKs incompatible as in-place updates even when their package name is unchanged.

The Review variant avoids that problem by using a dedicated package ID and one stable test-only signing identity. A Review APK can therefore be installed beside an existing `com.videoflow.app.debug` build and future Review APKs can update the same Review installation.

## Security boundary

This key is **not** a production trust credential and must never be used for a Play Store / production package. A future production release must use a private production key or Google Play App Signing and must not reuse this Review signing identity.
