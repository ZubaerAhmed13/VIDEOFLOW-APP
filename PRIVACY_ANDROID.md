# VideoFlow Android Privacy — Step 1

- Media analysis and playback are local to the Android device.
- No media upload path is implemented.
- The release manifest does not request `INTERNET`.
- No ads SDK, analytics SDK, remote telemetry, or crash-upload SDK is included.
- Project metadata and source references remain in the local Room database.
- Only read URI access is requested for user-selected documents.
- No unique device identifier is collected.
- Diagnostics use non-sensitive technical properties such as API level, ABI, approximate RAM/free storage, and codec capabilities.
- The content provider used for automated media fixtures exists only in the debug build and is not part of the release APK.
