# VideoFlow Android UI Step 2 — Accessibility Notes

## Principle

No critical Step 2 operation is intended to be direct-gesture-only. Direct manipulation accelerates editing, while labeled controls remain available for precision and assistive technology.

## Toolbar and touch targets

The approved Step 1 toolbar architecture is retained. Primary tool buttons use explicit content descriptions and a 52×58dp interaction container, preserving the >=48dp primary target certification path.

## Trim

The visual range control has a semantic description and exact Start, End and Duration text. Material range semantics provide an adjustable alternative to raw pointer dragging.

## Crop

Direct edge/corner manipulation is accompanied by aspect-ratio presets and Left/Right/Top/Bottom normalized edge sliders with semantic value descriptions.

## Transform

Pan, pinch and rotation gestures are accompanied by Position X, Position Y, Scale and Rotation sliders. Rotation also has 0°, 90°, 180° and 270° quick actions. Clip flip actions are explicit buttons.

## Sliders

Important sliders expose user-facing values such as percentage, degrees, multiplier, seconds or dB. Raw normalized coordinates and microseconds are not presented as labels.

## Keyframes

Keyframe diamonds include semantic phrases such as “Add Horizontal position keyframe” or “... keyframe exists; remove”. Previous and Next are explicit buttons, and Hold/Linear are named controls.

## Text and IME

Text editing uses Compose text input inside an `imePadding()` contextual sheet. Closing the keyboard is separate from leaving the editor. Unsupported font-family storage is not represented by a fake picker.

## Large text

The editor toolbar remains horizontally scrollable and Step 1’s 150% font-scale emulator test remains part of the API 35 certification job. Physical 150% font and TalkBack review remain required before final Step 2 completion.

## TalkBack physical gate

The real-device review must confirm understandable traversal for Trim, Speed, Crop presets, Transform values, Opacity, Volume, Keyframes, Done and Cancel. Until that is performed on the exact Review APK, TalkBack physical status is `NOT VERIFIED`.
