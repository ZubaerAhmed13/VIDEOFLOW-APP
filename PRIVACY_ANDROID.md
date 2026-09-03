# VideoFlow Android Privacy — Step 1

- Media analysis, fingerprinting, source verification and playback are local to the Android device.
- No media upload path is implemented.
- The release manifest does not request `INTERNET`.
- `MANAGE_EXTERNAL_STORAGE` and legacy broad external-storage permissions are not requested; user media is accessed through SAF document grants.
- No ads SDK, analytics SDK, remote telemetry, or crash-upload SDK is included.
- Project metadata and source references remain in the local Room database.
- `android:allowBackup="false"` remains enabled and modern `dataExtractionRules` explicitly exclude root/files/databases/shared preferences/external app data from cloud backup and device transfer.
- Only read URI access is requested for user-selected documents.
- No unique device identifier is collected.
- Diagnostics use non-sensitive technical properties such as API level, ABI, approximate RAM/free storage, and codec capabilities.
- The content provider used for automated media fixtures exists only in the debug build and is not part of the release APK.
- Fingerprint strength is preserved honestly: provider-limited weak identity is never silently promoted to strong identity.
