# Audio extraction

Video selection → Audio → Extract audio creates an independently editable audio timeline clip. Muting the original is an explicit unchecked option. Source trim, speed, timing and static gain/fades are retained in the new clip. AAC extraction copies compressed samples sequentially with Long timestamps and a bounded buffer. Other supported decoder inputs use the existing local Media3 transformer to produce AAC. Metadata validation rejects channel/sample-rate changes instead of silently accepting a downgrade.

The output is app-owned media exposed through an unexported FileProvider. It is registered in the normal media bin with an `Extracted from …` label. Its reference remains valid for background export without requesting a persistable grant from the app's own provider. Waveform generation, trim/split/move/volume/fades and standard timeline editing use the existing audio infrastructure. Undo/Redo removes/restores the timeline clip; derived media is retained for history and snapshots until project deletion.

The source identity determines the reusable extraction filename. Failed newly created output is deleted; an already registered reusable file is not deleted when a later operation fails. Multi-GB source input is never loaded or copied into memory.

Animated audio-gain keyframes are copied to the extracted clip. If original muting is selected, original gain automation is muted too, with Undo restoring the previous values. Device-dependent conversion formats require real-device coverage. Physical audio extraction certification remains NOT VERIFIED.
