#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if new in text and old not in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, got {count}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# 1. Manifest: editor-time ONNX/Media3 preview is a separate bound process.
# ---------------------------------------------------------------------------
manifest = "app/src/main/AndroidManifest.xml"
old_manifest = '''        <!-- Heavy codec/GL/ONNX export work is intentionally isolated from the editor process.
             All job inputs/state are persisted and rebuilt through repositories in this process. -->
        <service
            android:name=".export.ExportForegroundService"'''
new_manifest = '''        <!-- Editor-time LaMa/Media3 preview work is isolated from the editor process. A fatal
             ONNX/GPU/codec worker failure therefore cannot terminate MainActivity/editor state. -->
        <service
            android:name=".ai.watermark.AiPreviewIsolatedService"
            android:exported="false"
            android:process=":ai_preview" />

        <!-- Heavy codec/GL/ONNX final export work remains isolated independently in :export.
             All job inputs/state are persisted and rebuilt through repositories in this process. -->
        <service
            android:name=".export.ExportForegroundService"'''
replace_once(manifest, old_manifest, new_manifest)


# ---------------------------------------------------------------------------
# 2. Worker service: use min-SDK-safe process identity helper.
# ---------------------------------------------------------------------------
service = "app/src/main/java/com/videoflow/app/ai/watermark/AiPreviewIsolatedService.kt"
text = read(service)
text = text.replace("import android.app.Application\n", "")
if "import com.videoflow.app.export.ExportProcessIdentity\n" not in text:
    text = text.replace(
        "import com.videoflow.app.domain.ai.RoiMotionAnchor\n",
        "import com.videoflow.app.domain.ai.RoiMotionAnchor\nimport com.videoflow.app.export.ExportProcessIdentity\n",
    )
text = text.replace(
    'check(Application.getProcessName() == "$packageName:ai_preview")',
    'check(ExportProcessIdentity.currentProcessName(this) == "$packageName:ai_preview")',
)
text = text.replace(
    "putString(KEY_PROCESS_NAME, Application.getProcessName())",
    "putString(KEY_PROCESS_NAME, ExportProcessIdentity.currentProcessName(this@AiPreviewIsolatedService))",
)
if "Application.getProcessName()" in text:
    raise SystemExit("API-28-only Application.getProcessName remained in isolated AI service")
write(service, text)


# ---------------------------------------------------------------------------
# 3. WatermarkStudio: main process keeps only bounded decode/tracking/state.
#    ORT validation, still LaMa render and moving Media3/LaMa render go via IPC.
# ---------------------------------------------------------------------------
vm = "app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt"
text = read(vm)
for old_import in (
    "import com.videoflow.app.ai.watermark.AiModelPackManager\n",
    "import com.videoflow.app.ai.watermark.AiProcessedPreviewManager\n",
    "import com.videoflow.app.ai.watermark.LocalWatermarkPreviewEngine\n",
):
    text = text.replace(old_import, "")
anchor = "import com.videoflow.app.ai.watermark.AiMovingPreviewLength\n"
needed_imports = '''import com.videoflow.app.ai.watermark.AiMovingPreviewLength
import com.videoflow.app.ai.watermark.AiPreviewCacheController
import com.videoflow.app.ai.watermark.AiPreviewProcessClient
import com.videoflow.app.ai.watermark.LocalPreviewFrameDecoder
'''
if "import com.videoflow.app.ai.watermark.AiPreviewProcessClient\n" not in text:
    if anchor not in text:
        raise SystemExit("Could not locate WatermarkStudio AI import anchor")
    text = text.replace(anchor, needed_imports, 1)

old_ctor = '''    private val repository: AiWatermarkRepository,
    private val modelPackManager: AiModelPackManager,
    private val previewEngine: LocalWatermarkPreviewEngine,
    private val processedPreviewManager: AiProcessedPreviewManager,
    private val tracker: LocalRoiTracker,
    private val historyService: EditHistoryService
'''
new_ctor = '''    private val repository: AiWatermarkRepository,
    private val aiPreviewClient: AiPreviewProcessClient,
    private val frameDecoder: LocalPreviewFrameDecoder,
    private val previewCacheController: AiPreviewCacheController,
    private val tracker: LocalRoiTracker,
    private val historyService: EditHistoryService
'''
if old_ctor in text:
    text = text.replace(old_ctor, new_ctor, 1)
elif new_ctor not in text:
    raise SystemExit("Could not replace WatermarkStudio constructor dependencies")

old_runtime = '''                val effects = repository.effectsForClip(projectId, clipId)
                modelPackManager.ensurePackInstalled()
                val runtime = modelPackManager.status()
                effects to runtime
'''
new_runtime = '''                val effects = repository.effectsForClip(projectId, clipId)
                val runtime = aiPreviewClient.runtimeStatus()
                effects to runtime
'''
if old_runtime in text:
    text = text.replace(old_runtime, new_runtime, 1)
elif new_runtime not in text:
    raise SystemExit("Could not isolate WatermarkStudio runtime validation")

text = text.replace("previewEngine.decodeFrame(", "frameDecoder.decodeFrame(")
text = text.replace("previewEngine.render(", "aiPreviewClient.renderStill(")

old_moving = '''                processedPreviewManager.prepareDraftWindow(
                    projectId = effect.projectId,
                    clipId = effect.clipId,
                    draftEffect = effect,
                    centerLocalUs = centerLocalUs,
                    length = length
                ) { progress ->'''
new_moving = '''                aiPreviewClient.renderMoving(
                    effect = effect,
                    centerLocalUs = centerLocalUs,
                    length = length
                ) { progress ->'''
if old_moving in text:
    text = text.replace(old_moving, new_moving, 1)
elif new_moving not in text:
    raise SystemExit("Could not isolate moving AI preview")
text = text.replace(
    "movingPreviewProvider = processedPreviewManager.state.value.provider,",
    "movingPreviewProvider = ready.provider,",
)
text = text.replace("processedPreviewManager.invalidateProject(", "previewCacheController.invalidateProject(")
text = text.replace("        viewModelScope.launch { processedPreviewManager.cancel() }\n", "")

for forbidden in (
    "modelPackManager.ensurePackInstalled()",
    "modelPackManager.status()",
    "previewEngine.render(",
    "processedPreviewManager.prepareDraftWindow(",
    "processedPreviewManager.cancel()",
):
    if forbidden in text:
        raise SystemExit(f"Heavy main-process AI call remained in WatermarkStudio: {forbidden}")
for required in (
    "aiPreviewClient.runtimeStatus()",
    "aiPreviewClient.renderStill(",
    "aiPreviewClient.renderMoving(",
    "frameDecoder.decodeFrame(",
    "previewCacheController.invalidateProject(",
):
    if required not in text:
        raise SystemExit(f"Missing isolated WatermarkStudio call: {required}")
write(vm, text)


# ---------------------------------------------------------------------------
# 4. API-35 runtime runner: fatal :ai_preview death, main PID survival,
#    worker restart. Keep this in the same authoritative emulator lane.
# ---------------------------------------------------------------------------
runner = "scripts/step5/run_api35.sh"
text = read(runner)
needle = '''grep -E -q 'OK \\(1 test\\)' step4-emulator-reports/step5-process-recovery.txt

adb shell am instrument -w -r -e class com.videoflow.app.step5.Step5AiOutsideRoiTest'''
insert = '''grep -E -q 'OK \\(1 test\\)' step4-emulator-reports/step5-process-recovery.txt

# Kill the dedicated editor-time AI preview worker from inside that worker process. The
# instrumentation/main process must retain the same PID and then successfully bind a fresh worker.
adb shell am instrument -w -r -e class com.videoflow.app.ai.AiPreviewProcessIsolationInstrumentedTest "$PACKAGE_ID.test/androidx.test.runner.AndroidJUnitRunner" > step4-emulator-reports/ai-preview-process-isolation.txt 2>&1
cat step4-emulator-reports/ai-preview-process-isolation.txt
! grep -E -q 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' step4-emulator-reports/ai-preview-process-isolation.txt || exit 1
grep -E -q 'OK \\(1 test\\)' step4-emulator-reports/ai-preview-process-isolation.txt
grep -q 'AI_PREVIEW_PROCESS_ISOLATION_CERTIFIED' step4-emulator-reports/ai-preview-process-isolation.txt

adb shell am instrument -w -r -e class com.videoflow.app.step5.Step5AiOutsideRoiTest'''
if needle in text:
    text = text.replace(needle, insert, 1)
elif "AiPreviewProcessIsolationInstrumentedTest" not in text:
    raise SystemExit("Could not insert API-35 AI preview process isolation test")
write(runner, text)


# ---------------------------------------------------------------------------
# 5. Exact build audit: prove heavy editor-time AI entry points live in service,
#    not WatermarkStudioViewModel.
# ---------------------------------------------------------------------------
workflow = ".github/workflows/android-step5-certification.yml"
text = read(workflow)
audit_anchor = '''          grep -q 'FINAL_AI_EXPORT_CERTIFIED' app/src/androidTest/java/com/videoflow/app/ai/Step4AiFinalExportInstrumentedTest.kt
          grep -q 'onnxruntime-android:1.29.0' app/build.gradle.kts
'''
audit_new = '''          grep -q 'FINAL_AI_EXPORT_CERTIFIED' app/src/androidTest/java/com/videoflow/app/ai/Step4AiFinalExportInstrumentedTest.kt
          grep -q 'android:process=":ai_preview"' app/src/main/AndroidManifest.xml
          grep -q 'AiPreviewProcessClient' app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt
          grep -q 'aiPreviewClient.renderStill' app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt
          grep -q 'aiPreviewClient.renderMoving' app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt
          ! grep -q 'previewEngine.render' app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt
          ! grep -q 'processedPreviewManager.prepareDraftWindow' app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt
          ! grep -q 'modelPackManager.ensurePackInstalled' app/src/main/java/com/videoflow/app/ui/ai/WatermarkStudioViewModel.kt
          grep -q 'previewEngine.render' app/src/main/java/com/videoflow/app/ai/watermark/AiPreviewIsolatedService.kt
          grep -q 'processedPreviewManager.prepareDraftWindow' app/src/main/java/com/videoflow/app/ai/watermark/AiPreviewIsolatedService.kt
          grep -q 'AiPreviewProcessIsolationInstrumentedTest' scripts/step5/run_api35.sh
          grep -q 'onnxruntime-android:1.29.0' app/build.gradle.kts
'''
if audit_anchor in text:
    text = text.replace(audit_anchor, audit_new, 1)
elif "android:process=\":ai_preview\"" not in text:
    raise SystemExit("Could not insert exact-build AI process audit")
write(workflow, text)

print("AI preview process isolation wiring applied")
