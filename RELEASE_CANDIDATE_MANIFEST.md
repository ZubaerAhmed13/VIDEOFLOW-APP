# VideoFlow Android Professional — Release Candidate 1

Repository: `ZubaerAhmed13/VIDEOFLOW-APP`  
Baseline branch: `professional-feature-ux-upgrade`  
Verified baseline: `7e23f342c6c7fb5563bd62b1f22bf7b4684e366d`  
Step 5 branch: `step5-final-hardening-certification`  
Exact candidate commit: `{{COMMIT}}`  
Workflow: {{RUN_URL}} (run `{{RUN_ID}}`)  
Automated status: **{{AUTOMATED_STATUS}}**  
Physical certification: **NOT VERIFIED**

Source documents are certification templates. Only the report artifact generated after all exact-commit gates succeed establishes a software PASS. No merge or production release is authorized by this report.


Build types: Debug diagnostics, Review with stable test signing, matching Debug instrumentation. Production source is shared; no test-only substitute pipeline is used. The instrumentation APK targets Debug and must be installed alongside Debug for automated physical harnesses.

| Model | Role / license | Bytes | SHA-256 |
|---|---|---:|---|
| lama-512-int8-v1 | Final 512 / Apache-2.0 | 62074990 | cab19978adc306622fe37ef60d4a52103b99c98141d499c2a2366a7ed1255dbe |
| lama-dynamic-int8-v1 | Fast preview 256 / Apache-2.0 | 61512617 | 1941214c210399eb815eb2d32570ba91d5e6c4ac3de4c939bd3fb09300454972 |

APK hashes: `SHA256SUMS.txt` in this exact-head package. Machine-readable identity: `ARTIFACT_IDENTITY.json`. Review signer: `f3d4e66b350800bca739b2c5f6f4d2c7f15c7dc89b1b8763bd51468ab7150cc7`.

This candidate is for final physical testing. It is not Play Store publication, a production-signed release, an independent review approval, or a merge to main.
