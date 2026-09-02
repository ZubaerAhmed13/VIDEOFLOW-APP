package com.videoflow.app.data.device

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CodecSupport(
    val mime: String,
    val decoder: Boolean,
    val encoder: Boolean,
    val hardwareDecoder: Boolean?,
    val hardwareEncoder: Boolean?,
    val decode4k30: Boolean?,
    val decode4k60: Boolean?,
    val encode4k30: Boolean?,
    val encode4k60: Boolean?
)

data class DeviceCapabilityProfile(
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val abis: List<String>,
    val cpuCores: Int,
    val totalRamBytes: Long,
    val freeInternalBytes: Long,
    val persistedReadPermissionCount: Int,
    val codecs: List<CodecSupport>
)

@Singleton
class DeviceCapabilityRepository @Inject constructor(@ApplicationContext private val context: Context) {
    fun read(): DeviceCapabilityProfile {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val storage = StatFs(context.filesDir.absolutePath)
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList()
        val videoMimes = listOf("video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01")

        val supports = videoMimes.map { mime ->
            val decoders = codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            val encoders = codecInfos.filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            CodecSupport(
                mime = mime,
                decoder = decoders.isNotEmpty(),
                encoder = encoders.isNotEmpty(),
                hardwareDecoder = hardwareStatus(decoders),
                hardwareEncoder = hardwareStatus(encoders),
                decode4k30 = supportsSizeAndRate(decoders, mime, 3840, 2160, 30.0),
                decode4k60 = supportsSizeAndRate(decoders, mime, 3840, 2160, 60.0),
                encode4k30 = supportsSizeAndRate(encoders, mime, 3840, 2160, 30.0),
                encode4k60 = supportsSizeAndRate(encoders, mime, 3840, 2160, 60.0)
            )
        }

        return DeviceCapabilityProfile(
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            abis = Build.SUPPORTED_ABIS.toList(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamBytes = memoryInfo.totalMem,
            freeInternalBytes = storage.availableBytes,
            persistedReadPermissionCount = context.contentResolver.persistedUriPermissions.count { it.isReadPermission },
            codecs = supports
        )
    }

    private fun hardwareStatus(codecs: List<MediaCodecInfo>): Boolean? {
        if (codecs.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= 29) codecs.any { it.isHardwareAccelerated } else null
    }

    private fun supportsSizeAndRate(
        codecs: List<MediaCodecInfo>,
        mime: String,
        width: Int,
        height: Int,
        fps: Double
    ): Boolean? {
        if (codecs.isEmpty()) return null
        val answers = codecs.mapNotNull { codec ->
            runCatching {
                codec.getCapabilitiesForType(mime).videoCapabilities?.areSizeAndRateSupported(width, height, fps)
            }.getOrNull()
        }
        return if (answers.isEmpty()) null else answers.any { it }
    }
}
