from pathlib import Path

p = Path('app/src/main/java/com/videoflow/app/ui/screens/EditorScreen.kt')
s = p.read_text()

if 'import kotlin.math.roundToLong\n' not in s:
    s = s.replace('import kotlin.math.abs\n', 'import kotlin.math.abs\nimport kotlin.math.roundToLong\n', 1)

anchor = '                        onMoveClip = { clipId, deltaUs -> vm.selectClip(clipId); selection = EditorSelection.Clip(clipId); vm.moveSelectedSnapped(deltaUs, pixelsPerSecond.toDouble()) },\n'
addition = anchor + '''                        onTrimClipStart = { clipId, deltaTimelineUs ->
                            clips.firstOrNull { it.id == clipId }?.let { clip ->
                                vm.selectClip(clipId)
                                selection = EditorSelection.Clip(clipId)
                                vm.trimSelectedStart((deltaTimelineUs.toDouble() * clip.speed).roundToLong())
                            }
                        },
                        onTrimClipEnd = { clipId, deltaTimelineUs ->
                            clips.firstOrNull { it.id == clipId }?.let { clip ->
                                vm.selectClip(clipId)
                                selection = EditorSelection.Clip(clipId)
                                vm.trimSelectedEnd((deltaTimelineUs.toDouble() * clip.speed).roundToLong())
                            }
                        },
'''
count = s.count(anchor)
if count != 3:
    raise SystemExit(f'expected 3 timeline move callback anchors, got {count}')
s = s.replace(anchor, addition)

p.write_text(s)
