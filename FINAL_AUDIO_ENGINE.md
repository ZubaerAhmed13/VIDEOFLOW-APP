# VideoFlow Android Step 3 — Final Audio Engine

## Scope

Step 3 renders the actual Step 2 audio timeline into the final MP4 while keeping source files immutable and reference based.

## Source and timeline authority

Audio is resolved from original `content://` media referenced by the final render plan. Video clips with audio and dedicated audio clips can contribute to the final mix. Track mute/solo state, track gain, clip gain, clip fades, clip speed and timeline placement are taken from the persisted Step 2 project state.

## Decode and mix path

Media3 decodes source audio natively. The composition applies the timeline structure and audio processors/effects required by the rendered plan. The final stream is encoded to AAC-LC. User-selectable AAC bitrates are 128, 192, 256 and 320 kbps, subject to encoder support.

## Gain, mute, solo and fades

The render plan determines which audio-capable tracks are audible. If any audio-capable track is soloed, only soloed tracks participate. Muted tracks are excluded. Clip/track gain and fade-in/fade-out values are represented in the timeline domain and must be applied by the render composition rather than by destructive source modification.

## A/V sync

Timeline time is stored in 64-bit microseconds. Video cadence is represented by rational frame rates and audio remains tied to the same timeline origin. Output validation checks audio-track presence and overall duration. Final physical certification additionally requires perceptual/measured A/V sync to remain within approximately one output video frame for the designated test fixture.

## Encoder policy

Audio output is AAC-LC in MP4. The requested bitrate is passed to Media3's audio encoder settings. Encoder initialization failure is surfaced as an export failure; a failed audio setup must not produce a false completed export.

## Bounded-memory rule

The engine never materializes an entire source audio file or complete PCM program in RAM. Decode/mix/encode remains streaming through the native media pipeline.

## Certification status

Automated instrumentation includes a native video+audio render and validates that the completed MP4 contains an AAC track. Final device-level checks for audible gain/fade behavior, distortion and A/V sync remain mandatory in `STEP_3_PHYSICAL_DEVICE_CERTIFICATION.md`.
