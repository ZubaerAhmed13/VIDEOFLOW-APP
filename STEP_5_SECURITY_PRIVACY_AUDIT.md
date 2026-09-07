# Security and privacy audit

Repository: `ZubaerAhmed13/VIDEOFLOW-APP`  
Baseline branch: `professional-feature-ux-upgrade`  
Verified baseline: `7e23f342c6c7fb5563bd62b1f22bf7b4684e366d`  
Step 5 branch: `step5-final-hardening-certification`  
Exact candidate commit: `{{COMMIT}}`  
Workflow: {{RUN_URL}} (run `{{RUN_ID}}`)  
Automated status: **{{AUTOMATED_STATUS}}**  
Physical certification: **NOT VERIFIED**

Source documents are certification templates. Only the report artifact generated after all exact-commit gates succeed establishes a software PASS. No merge or production release is authorized by this report.


The Review merged manifest is checked for absence of INTERNET and ACCESS_NETWORK_STATE. No cloud inference, advertising SDK, analytics requirement or runtime model download is present. Core CI device checks disable Wi-Fi and mobile data before preview, inference and export. The build downloads model files; runtime installs them from embedded assets after exact byte-size/SHA-256 checks. FileProvider is unexported and exposes only the derived-media roots. The foreground service is unexported; MainActivity is the launcher. Backup is disabled.

Read-only destination guards cover exact and normalized URIs, document IDs, canonical paths and regular-file device/inode aliases. Instrumentation creates hard links and verifies unchanged source SHA after rejected prepare/Smart Copy calls. Opaque providers whose aliases expose no shared identity cannot be universally detected; Android's new-document flow remains the required user destination path. No source overwrite is advertised.

Sidecar identifiers are checked before constructing paths; traversal attempts are tested. Derived deletion uses owned directories/canonical paths and never deletes original URIs. Recovery refuses unsafe truncation. LocalDiagnosticLog strips media URLs, private storage paths and control characters and retains at most 200 entries. Raw user media paths are not needed for normal diagnostics; intentional physical-test evidence may contain the tester's supplied source/output URI and stays with the tester.

Dependencies remain version-pinned in Gradle, with project repository overrides rejected and only Google/Maven Central plus the Gradle plugin portal configured. Gradle 9.6.0's binary checksum is pinned against [Gradle's official reference](https://gradle.org/release-checksums/). This is an integrity/configuration audit, not a claim that every transitive dependency is vulnerability-free or independently security-reviewed.

Review uses the existing test-only signing identity, SHA-256 `f3d4e66b350800bca739b2c5f6f4d2c7f15c7dc89b1b8763bd51468ab7150cc7`; it is not a production signing secret. CI checks the signature, embedded models, APK contents, permissions and exact source commit.

Android service handling follows the [foreground-service timeout documentation](https://developer.android.com/develop/background-work/services/fgs/timeout) and [Android 14 service-type requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required). Android 15's background time allowance may interrupt exceptionally long processing; no quality downgrade is used to avoid it.
