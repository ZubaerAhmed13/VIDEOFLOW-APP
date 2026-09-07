# 30-minute certification

Automated scope: `AiLongJobPlanTest` covers a continuous 1,800,000,000-microsecond production schedule, resume offsets, progress, ragged final segments, multi-hour time, >3 GB estimates and checked overflow. `ProfessionalCheckpointExportInstrumentedTest` exercises real short segmented production output, cancellation, retained checkpoints, resume and final validation. These are separate from visual endurance testing.

The configurable real harness is `com.videoflow.app.ai.LongAiEnduranceInstrumentedTest`. It is intentionally excluded from normal CI because it processes every frame using final local AI. Its inputs are `vfSourceUri`, optional `vfStartUs`/`vfEndUs`, `vfRoi` (left,top,right,bottom normalized), and optional `vfCheckpointUs`. Use an original URI already accessible to the installed debug app. The harness never deletes the provided source. Successful validated output is retained for visual review by default (`vfKeepOutput=false` opts into cleanup). A JSON-lines record under the app's external `ai-endurance` directory samples PSS, thermal status, progress and elapsed time every five seconds and records the final output URI/hash. The reported peak is a sampled maximum, not a guaranteed high-water measurement. Original codec, rotation and colour metadata are passed through the production preflight. Models are packaged offline; no runtime download is used.

Example invocation after final-stage source preparation:

```sh
adb shell am instrument -w -r \
  -e class com.videoflow.app.ai.LongAiEnduranceInstrumentedTest \
  -e vfSourceUri 'content://PROVIDER/ORIGINAL_DOCUMENT' \
  -e vfStartUs 0 -e vfEndUs 1800000000 \
  -e vfRoi '0.70,0.05,0.90,0.15' -e vfCheckpointUs 60000000 \
  com.videoflow.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Physical 1080p 30-minute run: NOT VERIFIED.

Physical 4K 30-minute run: NOT VERIFIED.

One-hour and multi-hour visual endurance: NOT VERIFIED.

No artificial 30-minute maximum exists. Device codec capability and available storage still apply.
