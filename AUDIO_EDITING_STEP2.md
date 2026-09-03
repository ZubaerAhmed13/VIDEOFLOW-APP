# VideoFlow Android Step 2 — Audio Editing Foundation

## Timeline audio

Audio-only assets can be added to AUDIO tracks. Video clips remain audio-capable through VIDEO tracks. Track mute/solo determine which audio-capable tracks contribute to preview.

## Gain

Both tracks and clips store real dB gain values. `AudioMath.dbToLinear()` converts dB to linear preview gain. AUDIO_GAIN keyframes can animate clip gain. Track gain and clip/evaluated gain are combined by the preview path.

## Fades

Clips persist fade-in and fade-out durations in 64-bit microseconds. `AudioMath.fadeGain()` evaluates deterministic linear fade gain at a local timeline position. Trim and split operations clamp fades before constructing shorter clip values so valid edits cannot fail because a previous fade exceeds the new duration.

## Waveforms

`WaveformService` uses native `MediaExtractor` + `MediaCodec`, decodes progressively and stores only bounded peak arrays (64–4096 bins). Decoder concurrency is one. Cache files contain duration and floats only and are fingerprint-keyed; they do not duplicate source media. The editor renders these peaks graphically on timeline items.

## Known Step 2 limitations

Advanced EQ, noise reduction, pitch correction, mastering, time-stretch quality algorithms and final audio rendering are outside Step 2. Step 3 owns final render/mux fidelity.
