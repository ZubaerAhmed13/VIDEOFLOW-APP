#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "app/src/androidTest/java/com/videoflow/app/step5/Step5ProductIntegrationTest.kt"
text = path.read_text(encoding="utf-8")

old_ctor = '''            val manager=AiModelPackManager(context)
            val previewEngine=LocalWatermarkPreviewEngine(context,manager)
            val processedPreviewManager=AiProcessedPreviewManager(context,editor,projects,ai,manager)
            val aiVm=WatermarkStudioViewModel(ai,manager,previewEngine,processedPreviewManager,LocalRoiTracker(previewEngine),history)
'''
new_ctor = '''            val manager=AiModelPackManager(context)
            val previewEngine=LocalWatermarkPreviewEngine(context,manager)
            val aiVm=WatermarkStudioViewModel(
                ai,
                AiPreviewProcessClient(context),
                LocalPreviewFrameDecoder(context),
                AiPreviewCacheController(context),
                LocalRoiTracker(previewEngine),
                history
            )
'''
if old_ctor in text:
    text = text.replace(old_ctor, new_ctor, 1)
elif new_ctor not in text:
    raise SystemExit("Could not update Step5ProductIntegrationTest WatermarkStudioViewModel construction")

text = text.replace('rule.onNodeWithText("Time",substring=false).performClick()', 'rule.onNodeWithText("Duration",substring=false).performClick()')
text = text.replace('rule.onNodeWithText("Select",substring=false).performClick()', 'rule.onNodeWithText("Cover",substring=false).performClick()')
text = text.replace('rule.onNodeWithContentDescription("AI stage Apply").performClick()', 'rule.onNodeWithContentDescription("AI stage Done").performClick()')
text = text.replace('rule.onNodeWithContentDescription("Apply AI removal").assertIsEnabled().performScrollTo().performClick()', 'rule.onNodeWithContentDescription("Save AI removal").assertIsEnabled().performScrollTo().performClick()')

old_wait = '''            // Apply now atomically persists the edit and then prepares the real processed moving
            // timeline-preview media before closing the panel. Prove that modern lifecycle is
            // entered, then allow the same bounded AI budget used by the explicit preview test.
            rule.waitUntil(30_000) {
                tool==null || aiVm.state.value.error!=null ||
                    aiVm.state.value.busy==WatermarkStudioBusy.PREPARING_EDITOR_PREVIEW
            }
            rule.waitUntil(180_000) { tool==null || aiVm.state.value.error!=null }
            assertNull("AI commit or processed editor-preview preparation failed",aiVm.state.value.error)
            assertNull("Applied panel must close after processed editor preview is ready",tool)
'''
new_wait = '''            // Done atomically persists one non-destructive edit definition and returns promptly.
            // Moving preview is an explicit Preview action; final reconstruction remains in Export.
            rule.waitUntil(30_000) { tool==null || aiVm.state.value.error!=null }
            assertNull("AI Done failed",aiVm.state.value.error)
            assertNull("Done must return to the editor promptly",tool)
'''
if old_wait in text:
    text = text.replace(old_wait, new_wait, 1)
elif new_wait not in text:
    raise SystemExit("Could not update Step5ProductIntegrationTest Done lifecycle")

text = text.replace(
    "// Stage chip and commit button both say Apply; the chip exists before opening the stage.",
    "// Done is a lightweight non-destructive save; Preview remains explicit and optional.",
)

# Strengthen the already-executed integrated product test with a real crop-only production render.
# This proves normalized crop geometry is not merely stored in model state: Media3 must render it.
crop_block = '''            val certificationCrop=com.videoflow.app.domain.editor.CropRect(.47f,.08f,.97f,.82f)
            val cropEntity=db.editorDao().getClips(id).first { it.id==clip.id }
            db.editorDao().putClip(cropEntity.copy(
                cropLeft=certificationCrop.left,
                cropTop=certificationCrop.top,
                cropRight=certificationCrop.right,
                cropBottom=certificationCrop.bottom
            ))
            val cropOnlyOutput=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME,"step5-crop-only.mp4")
                put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/VideoFlowCertification")
            })!!
            try {
                val cropCompiled=com.videoflow.app.data.export.ExportRepository(db,editor).compileFinalPlan(id)
                assertTrue(cropCompiled.problems.toString(),cropCompiled.ready)
                val cropPlan=checkNotNull(cropCompiled.plan)
                val renderedCrop=cropPlan.editorPlan.clips.single { it.id==clip.id }.transform.crop
                assertEquals(certificationCrop.left,renderedCrop.left,.0001f)
                assertEquals(certificationCrop.top,renderedCrop.top,.0001f)
                assertEquals(certificationCrop.right,renderedCrop.right,.0001f)
                assertEquals(certificationCrop.bottom,renderedCrop.bottom,.0001f)
                val cropSettings=com.videoflow.app.domain.export.ExportMath.resolve(
                    com.videoflow.app.domain.export.ExportSize(320,240),
                    com.videoflow.app.domain.editor.FrameRate.FPS_30,
                    com.videoflow.app.domain.export.ExportSettings()
                )
                val cropEngine=com.videoflow.app.render.Media3RenderEngine(context,ai,AiModelPackManager(context))
                val cropPrepared=cropEngine.prepare(cropPlan,com.videoflow.app.render.OutputDestination(cropOnlyOutput,"crop-only.mp4"),cropSettings)
                assertTrue(cropPrepared.problems.toString(),cropPrepared.ready)
                val cropResult=cropEngine.render(checkNotNull(cropPrepared.preparation),com.videoflow.app.render.RenderProgressListener {}).getOrThrow()
                assertTrue(cropResult.validation.problems.toString(),cropResult.validation.passed)

                fun frame(uri: android.net.Uri): android.graphics.Bitmap {
                    val retriever=android.media.MediaMetadataRetriever()
                    return try {
                        retriever.setDataSource(context,uri)
                        checkNotNull(retriever.getFrameAtTime(600_000L,android.media.MediaMetadataRetriever.OPTION_CLOSEST))
                    } finally { retriever.release() }
                }
                fun rgbDistance(a:Int,b:Int):Double {
                    val ar=android.graphics.Color.red(a); val ag=android.graphics.Color.green(a); val ab=android.graphics.Color.blue(a)
                    val br=android.graphics.Color.red(b); val bg=android.graphics.Color.green(b); val bb=android.graphics.Color.blue(b)
                    return (kotlin.math.abs(ar-br)+kotlin.math.abs(ag-bg)+kotlin.math.abs(ab-bb))/3.0
                }
                val sourceBitmap=frame(source)
                val outputBitmap=frame(cropOnlyOutput)
                try {
                    val expectedX=(((certificationCrop.left+certificationCrop.right)/2f)*sourceBitmap.width).toInt().coerceIn(0,sourceBitmap.width-1)
                    val expectedY=(((certificationCrop.top+certificationCrop.bottom)/2f)*sourceBitmap.height).toInt().coerceIn(0,sourceBitmap.height-1)
                    val expected=sourceBitmap.getPixel(expectedX,expectedY)
                    val actual=outputBitmap.getPixel(outputBitmap.width/2,outputBitmap.height/2)
                    val uncroppedCenter=sourceBitmap.getPixel(sourceBitmap.width/2,sourceBitmap.height/2)
                    val mappedDistance=rgbDistance(expected,actual)
                    assertTrue("Crop render center must map to the selected source region; RGB distance=$mappedDistance",mappedDistance<130.0)
                    assertTrue("Asymmetric crop must visibly move the source center",rgbDistance(uncroppedCenter,actual)>4.0)
                } finally {
                    sourceBitmap.recycle();outputBitmap.recycle()
                }
                instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","FINAL_EDITOR_CROP_RENDER_CERTIFIED left=${certificationCrop.left} top=${certificationCrop.top} right=${certificationCrop.right} bottom=${certificationCrop.bottom}\\n") })
            } finally { resolver.delete(cropOnlyOutput,null,null) }
'''
anchor = '''            val clip=split.first
            val loaded=editor.load(id)
'''
if 'FINAL_EDITOR_CROP_RENDER_CERTIFIED' not in text:
    if anchor not in text:
        raise SystemExit("Step5 product crop-render insertion anchor was not found")
    text = text.replace(anchor, '            val clip=split.first\n' + crop_block + '            val loaded=editor.load(id)\n', 1)

for stale in (
    "processedPreviewManager=AiProcessedPreviewManager",
    "WatermarkStudioViewModel(ai,manager,previewEngine,processedPreviewManager",
    'onNodeWithText("Time",substring=false)',
    'onNodeWithText("Select",substring=false)',
    'onNodeWithContentDescription("AI stage Apply")',
    'onNodeWithContentDescription("Apply AI removal")',
    "WatermarkStudioBusy.PREPARING_EDITOR_PREVIEW",
):
    if stale in text:
        raise SystemExit(f"Stale Step5 product integration expectation remained: {stale}")

path.write_text(text, encoding="utf-8")
print("Step5 product integration cleanup applied")
