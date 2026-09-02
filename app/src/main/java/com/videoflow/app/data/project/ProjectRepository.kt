package com.videoflow.app.data.project

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import com.videoflow.app.data.db.MediaAssetEntity
import com.videoflow.app.data.db.ProjectEntity
import com.videoflow.app.data.db.VideoFlowDatabase
import com.videoflow.app.data.db.toDomain
import com.videoflow.app.data.diagnostics.DiagnosticLevel
import com.videoflow.app.data.diagnostics.LocalDiagnosticLog
import com.videoflow.app.data.media.CorruptedMediaException
import com.videoflow.app.data.media.MediaAnalyzer
import com.videoflow.app.data.media.UnsupportedMediaException
import com.videoflow.app.data.media.UriFingerprintService
import com.videoflow.app.domain.model.ImportState
import com.videoflow.app.domain.model.MediaAsset
import com.videoflow.app.domain.model.SourceStatus
import com.videoflow.app.domain.model.VideoFlowProject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AddMediaResult {
    data class Added(val asset: MediaAsset) : AddMediaResult
    data class Duplicate(val asset: MediaAsset, val existingAssetId: String) : AddMediaResult
}

data class RelinkValidation(
    val matches: Boolean,
    val reason: String,
    val selectedName: String? = null,
    val selectedWidth: Int? = null,
    val selectedHeight: Int? = null,
    val selectedDurationUs: Long? = null
)

@Singleton
class ProjectRepository @Inject constructor(
    private val db: VideoFlowDatabase,
    @ApplicationContext context: Context,
    private val analyzer: MediaAnalyzer,
    private val fingerprinter: UriFingerprintService,
    private val diagnosticLog: LocalDiagnosticLog
) {
    private val resolver: ContentResolver = context.contentResolver

    fun observeProjects(): Flow<List<VideoFlowProject>> =
        db.projectDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getProject(id: String): VideoFlowProject? = withContext(Dispatchers.IO) {
        db.projectDao().get(id)?.toDomain()
    }

    suspend fun createProject(name: String): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.projectDao().insert(
            ProjectEntity(
                id = id,
                name = name.trim().ifBlank { "Untitled Project" },
                projectFormatVersion = PROJECT_FORMAT_VERSION,
                createdAt = now,
                updatedAt = now,
                lastOpenedAt = now
            )
        )
        id
    }

    suspend fun renameProject(id: String, name: String) = withContext(Dispatchers.IO) {
        val project = db.projectDao().get(id)?.project ?: return@withContext
        db.projectDao().update(
            project.copy(name = name.trim().ifBlank { project.name }, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        db.projectDao().delete(id)
    }

    suspend fun removeMediaReference(assetId: String) = withContext(Dispatchers.IO) {
        db.mediaAssetDao().delete(assetId)
    }

    suspend fun addMedia(
        projectId: String,
        uri: Uri,
        onState: (ImportState) -> Unit = {}
    ): AddMediaResult = withContext(Dispatchers.IO) {
        onState(ImportState.Opening)
        diagnosticLog.add(DiagnosticLevel.INFO, "Media import opened")
        val permissionPersisted = persistReadPermission(uri)

        onState(ImportState.ReadingMetadata)
        val info = analyzer.analyze(uri)

        onState(ImportState.Fingerprinting)
        val fingerprint = fingerprinter.fingerprint(
            uri = uri,
            sizeHint = info.sizeBytes,
            durationUs = info.metadata.durationUs,
            width = info.metadata.width,
            height = info.metadata.height
        )

        val duplicate = fingerprint.sha256?.let {
            db.mediaAssetDao().findByFingerprint(projectId, it).firstOrNull()
        } ?: db.mediaAssetDao().findByUri(projectId, uri.toString())

        onState(ImportState.Saving)
        val now = System.currentTimeMillis()
        val primaryVideo = info.metadata.videoTracks.firstOrNull()
        val entity = MediaAssetEntity(
            assetId = UUID.randomUUID().toString(),
            projectId = projectId,
            sourceUri = uri.toString(),
            displayName = info.displayName,
            mimeType = info.mimeType,
            sizeBytes = info.sizeBytes,
            durationUs = info.metadata.durationUs,
            width = info.metadata.width,
            height = info.metadata.height,
            rotationDegrees = info.metadata.rotationDegrees,
            frameRate = info.metadata.frameRate,
            videoCodecMime = info.metadata.videoCodecMime,
            audioCodecMime = info.metadata.audioCodecMime,
            audioSampleRate = info.metadata.audioSampleRate,
            audioChannelCount = info.metadata.audioChannelCount,
            videoTrackCount = info.metadata.videoTracks.size,
            audioTrackCount = info.metadata.audioTracks.size,
            videoBitrate = primaryVideo?.bitrate,
            videoProfile = primaryVideo?.profile,
            videoLevel = primaryVideo?.level,
            colorStandard = primaryVideo?.colorStandard,
            colorTransfer = primaryVideo?.colorTransfer,
            colorRange = primaryVideo?.colorRange,
            hdrStaticInfoPresent = primaryVideo?.hdrStaticInfoPresent == true,
            fingerprintSha256 = fingerprint.sha256,
            fingerprintAlgorithm = fingerprint.algorithm,
            fingerprintStrength = fingerprint.strength.name,
            fingerprintSampledBytes = fingerprint.sampledBytes,
            fingerprintNote = fingerprint.note,
            permissionPersisted = permissionPersisted,
            sourceStatus = SourceStatus.AVAILABLE.name,
            createdAt = now
        )

        db.withTransaction {
            db.mediaAssetDao().insert(entity)
            val project = db.projectDao().get(projectId)?.project
            if (project != null) {
                db.projectDao().update(project.copy(updatedAt = now, lastOpenedAt = now))
            }
        }

        onState(ImportState.Ready)
        diagnosticLog.add(DiagnosticLevel.INFO, "Media import completed; persisted permission=$permissionPersisted")
        if (duplicate == null) AddMediaResult.Added(entity.toDomain())
        else AddMediaResult.Duplicate(entity.toDomain(), duplicate.assetId)
    }

    suspend fun verifySource(asset: MediaAsset): SourceStatus = withContext(Dispatchers.IO) {
        val uri = Uri.parse(asset.sourceUri)
        val status = try {
            resolver.openFileDescriptor(uri, "r")?.use { } ?: return@withContext SourceStatus.MISSING
            analyzer.analyze(uri)
            SourceStatus.AVAILABLE
        } catch (_: SecurityException) {
            SourceStatus.PERMISSION_LOST
        } catch (_: FileNotFoundException) {
            SourceStatus.MISSING
        } catch (_: UnsupportedMediaException) {
            SourceStatus.UNSUPPORTED
        } catch (_: CorruptedMediaException) {
            SourceStatus.CORRUPTED
        } catch (_: Throwable) {
            SourceStatus.UNKNOWN
        }
        if (status != asset.sourceStatus) {
            updateStatus(asset.id, status)
            diagnosticLog.add(DiagnosticLevel.WARN, "Media source status changed to ${status.name}")
        }
        status
    }

    suspend fun updateStatus(assetId: String, status: SourceStatus) = withContext(Dispatchers.IO) {
        val entity = db.mediaAssetDao().get(assetId) ?: return@withContext
        db.mediaAssetDao().update(entity.copy(sourceStatus = status.name))
    }

    suspend fun relink(assetId: String, newUri: Uri): RelinkValidation = withContext(Dispatchers.IO) {
        val old = db.mediaAssetDao().get(assetId)
            ?: return@withContext RelinkValidation(false, "Original media reference was not found.")

        val info = analyzer.analyze(newUri)
        val fingerprint = fingerprinter.fingerprint(
            uri = newUri,
            sizeHint = info.sizeBytes,
            durationUs = info.metadata.durationUs,
            width = info.metadata.width,
            height = info.metadata.height
        )

        val validation = validateRelink(old, info, fingerprint.sha256)
        if (!validation.matches) return@withContext validation

        val permissionPersisted = persistReadPermission(newUri)
        val primaryVideo = info.metadata.videoTracks.firstOrNull()
        diagnosticLog.add(DiagnosticLevel.INFO, "Relink fingerprint validation passed")
        db.mediaAssetDao().update(
            old.copy(
                sourceUri = newUri.toString(),
                displayName = info.displayName,
                mimeType = info.mimeType,
                sizeBytes = info.sizeBytes,
                durationUs = info.metadata.durationUs,
                width = info.metadata.width,
                height = info.metadata.height,
                rotationDegrees = info.metadata.rotationDegrees,
                frameRate = info.metadata.frameRate,
                videoCodecMime = info.metadata.videoCodecMime,
                audioCodecMime = info.metadata.audioCodecMime,
                audioSampleRate = info.metadata.audioSampleRate,
                audioChannelCount = info.metadata.audioChannelCount,
                videoTrackCount = info.metadata.videoTracks.size,
                audioTrackCount = info.metadata.audioTracks.size,
                videoBitrate = primaryVideo?.bitrate,
                videoProfile = primaryVideo?.profile,
                videoLevel = primaryVideo?.level,
                colorStandard = primaryVideo?.colorStandard,
                colorTransfer = primaryVideo?.colorTransfer,
                colorRange = primaryVideo?.colorRange,
                hdrStaticInfoPresent = primaryVideo?.hdrStaticInfoPresent == true,
                permissionPersisted = permissionPersisted,
                sourceStatus = SourceStatus.AVAILABLE.name
            )
        )
        validation.copy(reason = "Original media reconnected.")
    }

    private fun validateRelink(
        old: MediaAssetEntity,
        selected: MediaAnalyzer.Result,
        selectedFingerprint: String?
    ): RelinkValidation {
        val selectedMeta = selected.metadata
        val matches = RelinkIdentity.matches(
            original = MediaIdentity(old.fingerprintSha256, old.sizeBytes, old.width, old.height),
            selected = MediaIdentity(selectedFingerprint, selected.sizeBytes, selectedMeta.width, selectedMeta.height)
        )
        return RelinkValidation(
            matches = matches,
            reason = if (matches) "Fingerprint and known technical identity match."
            else "This file does not match the original media.",
            selectedName = selected.displayName,
            selectedWidth = selectedMeta.width,
            selectedHeight = selectedMeta.height,
            selectedDurationUs = selectedMeta.durationUs
        )
    }

    private fun persistReadPermission(uri: Uri): Boolean {
        return try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            resolver.persistedUriPermissions.any { permission ->
                permission.uri == uri && permission.isReadPermission
            }
        } catch (_: SecurityException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    companion object {
        const val PROJECT_FORMAT_VERSION = 1
    }
}
