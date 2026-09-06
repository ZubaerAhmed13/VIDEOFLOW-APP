package com.videoflow.app.ai.watermark

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.videoflow.app.domain.ai.AiModelCatalog
import com.videoflow.app.domain.ai.AiModelRole
import com.videoflow.app.domain.ai.AiModelSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AiModelPackException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class InstalledAiModel(
    val spec: AiModelSpec,
    val file: File,
    val verifiedSha256: String,
    val inputName: String,
    val outputName: String,
    val inputShape: LongArray
)

data class AiRuntimeStatus(
    val finalModelReady: Boolean,
    val previewModelReady: Boolean,
    val nnapiAvailable: Boolean,
    val detail: String
) {
    val complete: Boolean get() = finalModelReady && previewModelReady
}

/** Holds both SessionOptions and Session because ORT requires options to outlive its session. */
class AiOrtSession internal constructor(
    val model: InstalledAiModel,
    val session: OrtSession,
    private val options: OrtSession.SessionOptions,
    val provider: String
) : AutoCloseable {
    override fun close() {
        runCatching { session.close() }
        runCatching { options.close() }
    }
}

/**
 * Installs checksum-pinned bundled models into app-private storage using streaming I/O. The runtime
 * has no network code: the APK/CI bundle is the only production model source.
 */
@Singleton
class AiModelPackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root = File(context.filesDir, "ai-models")
    private val mutex = Mutex()
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment("VideoFlowLocalAI") }
    private val cache = mutableMapOf<String, InstalledAiModel>()

    suspend fun ensurePackInstalled(): List<InstalledAiModel> = mutex.withLock {
        AiModelCatalog.all.map { installAndValidate(it) }
    }

    suspend fun ensureInstalled(role: AiModelRole): InstalledAiModel = mutex.withLock {
        val spec = AiModelCatalog.all.first { it.role == role }
        installAndValidate(spec)
    }

    suspend fun status(): AiRuntimeStatus = withContext(Dispatchers.IO) {
        val finalReady = runCatching { verifyExisting(AiModelCatalog.FINAL_512) != null }.getOrDefault(false)
        val previewReady = runCatching { verifyExisting(AiModelCatalog.PREVIEW_DYNAMIC) != null }.getOrDefault(false)
        val nnapi = runCatching { OrtEnvironment.getAvailableProviders().contains(OrtProvider.NNAPI) }.getOrDefault(false)
        val detail = when {
            finalReady && previewReady -> "Checksum-pinned dual LaMa pack is installed locally."
            else -> "Bundled model pack has not been installed yet or is unavailable in this APK."
        }
        AiRuntimeStatus(finalReady, previewReady, nnapi, detail)
    }

    suspend fun openSession(role: AiModelRole, preferNnapi: Boolean = true): AiOrtSession = withContext(Dispatchers.IO) {
        val model = ensureInstalled(role)
        if (preferNnapi && OrtEnvironment.getAvailableProviders().contains(OrtProvider.NNAPI)) {
            runCatching { createSession(model, useNnapi = true) }.getOrNull()?.let { return@withContext it }
        }
        createSession(model, useNnapi = false)
    }

    private fun installAndValidate(spec: AiModelSpec): InstalledAiModel {
        cache[spec.id]?.let { cached ->
            if (cached.file.isFile && cached.file.length() == spec.expectedBytes) return cached
        }
        verifyExisting(spec)?.let { return validateOrt(spec, it).also { model -> cache[spec.id] = model } }
        root.mkdirs()
        val target = File(root, spec.fileName)
        val temp = File(root, ".${spec.fileName}.tmp-${System.nanoTime()}")
        try {
            context.assets.open(spec.assetPath).use { input ->
                temp.outputStream().buffered(256 * 1024).use { output -> input.copyTo(output, 256 * 1024) }
            }
        } catch (t: Throwable) {
            temp.delete()
            throw AiModelPackException(
                "Bundled ${spec.sourceLabel} is missing. Install a CI-produced Step-4 APK containing the checksum-pinned model pack.",
                t
            )
        }
        if (temp.length() != spec.expectedBytes) {
            val actual = temp.length()
            temp.delete()
            throw AiModelPackException("${spec.fileName} size mismatch: expected ${spec.expectedBytes}, got $actual bytes.")
        }
        val digest = sha256(temp)
        if (!digest.equals(spec.sha256, ignoreCase = true)) {
            temp.delete()
            throw AiModelPackException("${spec.fileName} SHA-256 mismatch; refusing untrusted AI model bytes.")
        }
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw AiModelPackException("Could not replace an old local AI model.")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw AiModelPackException("Could not atomically install local AI model.")
        }
        return validateOrt(spec, target).also { cache[spec.id] = it }
    }

    private fun verifyExisting(spec: AiModelSpec): File? {
        val target = File(root, spec.fileName)
        if (!target.isFile || target.length() != spec.expectedBytes) return null
        return if (sha256(target).equals(spec.sha256, ignoreCase = true)) target else null
    }

    private fun validateOrt(spec: AiModelSpec, file: File): InstalledAiModel {
        val options = newCpuOptions()
        try {
            environment.createSession(file.absolutePath, options).use { session ->
                val inputName = session.inputNames.singleOrNull()
                    ?: throw AiModelPackException("${spec.fileName} must expose exactly one packed RGB+mask input.")
                val outputName = session.outputNames.firstOrNull()
                    ?: throw AiModelPackException("${spec.fileName} exposes no output tensor.")
                val info = session.inputInfo.getValue(inputName).info as? TensorInfo
                    ?: throw AiModelPackException("${spec.fileName} input is not a tensor.")
                val shape = info.shape
                if (shape.size != 4 || shape[0] !in setOf(1L, -1L) || shape[1] !in setOf(4L, -1L)) {
                    throw AiModelPackException("${spec.fileName} does not match VideoFlow's 1x4xHxW LaMa contract: ${shape.joinToString("x")}.")
                }
                if (spec.role == AiModelRole.FINAL) {
                    val h = shape[2]
                    val w = shape[3]
                    if (h > 0 && h != spec.inferenceSize.toLong() || w > 0 && w != spec.inferenceSize.toLong()) {
                        throw AiModelPackException("Final model must be ${spec.inferenceSize}x${spec.inferenceSize}; got ${h}x${w}.")
                    }
                }
                return InstalledAiModel(spec, file, spec.sha256, inputName, outputName, shape.copyOf())
            }
        } finally {
            options.close()
        }
    }

    private fun createSession(model: InstalledAiModel, useNnapi: Boolean): AiOrtSession {
        val options = newCpuOptions()
        try {
            if (useNnapi) options.addNnapi()
            val session = environment.createSession(model.file.absolutePath, options)
            return AiOrtSession(model, session, options, if (useNnapi) "NNAPI" else "CPU")
        } catch (t: Throwable) {
            runCatching { options.close() }
            throw AiModelPackException("Could not create ${if (useNnapi) "NNAPI" else "CPU"} ONNX session: ${t.message}", t)
        }
    }

    private fun newCpuOptions(): OrtSession.SessionOptions = OrtSession.SessionOptions().apply {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        setIntraOpNumThreads((cores - 1).coerceIn(1, 4))
        setInterOpNumThreads(1)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
