# Long AI processing

Final inference remains local, serial and source-referenced. The final model and 512-pixel tiles are unchanged. `SharedLamaRenderRuntime` now has one worker and a two-task queue with cancellation-aware backpressure. Thermal pressure adds scheduling delay; it does not change model quality. Each tile retains only its immediately previous ROI patch.

`AiLongJobPlan` lazily produces half-open microsecond ranges. Its storage calculations use checked Long arithmetic. The 30-minute automated test exercises this production planner; it does not run neural inference for 30 real minutes.

`SegmentedAiRenderEngine` extends the foreground export path. Supported single-source timelines render to independently finalized video segments. Final assembly copies the encoded video packets and copies compatible original AAC once. This avoids audio encoder priming at every segment boundary. Every segment has a SHA-256; completed segments are reused only when their hashes, temporal patch state, original media fingerprint, plan, settings, model and checkpoint interval agree. The final output must pass `OutputValidator` before completion. Cancellation retains completed checkpoints and truncates partial final output.

Checkpoint eligibility currently requires one enabled video clip starting at timeline zero, normal speed, unchanged audio gain/fades, no keyframes or overlays, and compatible AAC sample rate/channel count (or no audio). Complex compositions retain the original full-composition renderer. They can process long durations, but do not yet resume from a completed segment. Extending checkpoint resume to those compositions is outstanding work; this is not a blanket claim of universal resume support.

Checkpoint files live under a generated digest directory owned by the app. Source media is never copied wholesale, overwritten or deleted. Derived files are removed on successful final assembly or project deletion. Current sampled memory, maximum sampled PSS, worker/queue limits, stage and progress are recorded in checkpoint metadata. A sampled maximum is not a guaranteed high-water memory measurement.

Physical 30-minute quality, thermal, memory and audio/video continuity certification remains NOT VERIFIED.
