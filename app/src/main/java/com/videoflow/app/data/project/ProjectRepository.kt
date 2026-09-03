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
import com.videoflow.app.domain.model.FingerprintResult
import com.videoflow.app.domain.model.FingerprintStrength
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

data class PreparedMediaImport(
    val entity: MediaAssetEntity
)

sealed interface AddMediaResult {
    data class Added(val asset: MediaAsset) : AddMediaResult
    data class DuplicateCandidate(
        val candidate: PreparedMediaImport,
        val existingAssetId: String
    ) : AddMediaResult
}

data class PreparedRelink(
    val assetId: String,
    val sourceUri: String,
    val info: MediaAnalyzer.Result,
    val fingerprint: FingerprintResult
)

data class RelinkValidation(
    val match: IdentityMatch,
    val reason: String,
    val prepared: PreparedRelink? = null,
    val selectedName: String? = null,
    val selectedSizeBytes: Long? = null,
    val selectedWidth: Int? = null,
    val selectedHeight: Int? = null,
    val selectedDurationUs: Long? = null
) {
    val matches: Boolean get() = match == IdentityMatch.STRONG_MATCH
}

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

        val entity = buildEntity(
            projectId = projectId,
            uri = uri,
            info = info,
            fingerprint = fingerprint,
            permissionPersisted = permissionPersisted
        )
        val duplicate = findDuplicate(projectId, entity)
        if (duplicate != null) {
            onState(ImportState.Ready)
            diagnosticLog.add(DiagnosticLevel.INFO, "Duplicate media candidate detected before database insert")
            return@withContext AddMediaResult.DuplicateCandidate(
                candidate = PreparedMediaImport(entity),
                existingAssetId = duplicate.assetId
            )
        }

        onState(ImportState.Saving)
        insertPrepared(entity)
        onState(ImportState.Ready)
        diagnosticLog.add(DiagnosticLevel.INFO, "Media import completed; persisted permission=$permissionPersisted")
        AddMediaResult.Added(entity.toDomain())
    }

    suspend fun confirmAddDuplicate(candidate: PreparedMediaImport): MediaAsset = withContext(Dispatchers.IO) {
        insertPrepared(candidate.entity)
        diagnosticLog.add(DiagnosticLevel.INFO, "Duplicate media reference added after explicit confirmation")
        candidate.entity.toDomain()
    }

    suspend fun verifySource(asset: MediaAsset): SourceStatus = withContext(Dispatchers.IO) {
        val uri = Uri.parse(asset.sourceUri)
        val status = try {
            resolver.openFileDescriptor(uri, "r")?.use { } ?: return@withContext persistVerifiedStatus(asset, SourceStatus.MISSING)
            val info = analyzer.analyze(uri)
            val fingerprint = fingerprinter.fingerprint(
                uri = uri,
                sizeHint = info.sizeBytes,
                durationUs = info.metadata.durationUs,
                width = info.metadata.width,
                height = info.metadata.height
            )
            SourceIdentityPolicy.classifyCurrentSource(
                original = asset.toIdentity(),
                current = identityOf(info, fingerprint)
            )
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
        persistVerifiedStatus(asset, status)
    }

    suspend fun updateStatus(assetId: String, status: SourceStatus) = withContext(Dispatchers.IO) {
        val entity = db.mediaAssetDao().get(assetId) ?: return@withContext
        db.mediaAssetDao().update(entity.copy(sourceStatus = status.name))
    }

    suspend fun relink(assetId: String, newUri: Uri): RelinkValidation = withContext(Dispatchers.IO) {
        val old = db.mediaAssetDao().get(assetId)
            ?: return@withContext RelinkValidation(IdentityMatch.MISMATCH, "Original media reference was not found.")

        val info = analyzer.analyze(newUri)
        val fingerprint = fingerprinter.fingerprint(
            uri = newUri,
            sizeHint = info.sizeBytes,
            durationUs = info.metadata.durationUs,
            width = info.metadata.width,
            height = info.metadata.height
        )
        val selectedIdentity = identityOf(info, fingerprint)
        val decision = SourceIdentityPolicy.classifyRelink(old.toIdentity(), selectedIdentity)
        val prepared = PreparedRelink(assetId, newUri.toString(), info, fingerprint)

        when (decision.match) {
            IdentityMatch.STRONG_MATCH -> {
                persistRelink(old, prepared)
                diagnosticLog.add(DiagnosticLevel.INFO, "Strong relink identity validation passed")
                RelinkValidation(
                    match = IdentityMatch.STRONG_MATCH,
                    reason = "Original media reconnected.",
                    selectedName = info.displayName,
                    selectedSizeBytes = info.sizeBytes,
                    selectedWidth = info.metadata.width,
                    selectedHeight = info.metadata.height,
                    selectedDurationUs = info.metadata.durationUs
                )
            }
            IdentityMatch.WEAK_MATCH -> RelinkValidation(
                match = IdentityMatch.WEAK_MATCH,
                reason = "VideoFlow cannot strongly verify this media. Technical characteristics and the weak provider-limited fingerprint match; explicit confirmation is required.",
                prepared = prepared,
                selectedName = info.displayName,
                selectedSizeBytes = info.sizeBytes,
                selectedWidth = info.metadata.width,
                selectedHeight = info.metadata.height,
                selectedDurationUs = info.metadata.durationUs
            )
            IdentityMatch.MISMATCH -> RelinkValidation(
                match = IdentityMatch.MISMATCH,
                reason = "This file does not match the saved original media. ${decision.reason}",
                selectedName = info.displayName,
                selectedSizeBytes = info.sizeBytes,
                selectedWidth = info.metadata.width,
                selectedHeight = info.metadata.height,
                selectedDurationUs = info.metadata.durationUs
            )
            IdentityMatch.UNVERIFIABLE -> RelinkValidation(
                match = IdentityMatch.UNVERIFIABLE,
                reason = "VideoFlow cannot verify this selection strongly enough to relink it safely. ${decision.reason}",
                selectedName = info.displayName,
                selectedSizeBytes = info.sizeBytes,
                selectedWidth = info.metadata.width,
                selectedHeight = info.metadata.height,
                selectedDurationUs = info.metadata.durationUs
            )
        }
    }

    suspend fun confirmWeakRelink(prepared: PreparedRelink): RelinkValidation = withContext(Dispatchers.IO) {
        val old = db.mediaAssetDao().get(prepared.assetId)
            ?: return@withContext RelinkValidation(IdentityMatch.MISMATCH, "Original media reference was not found.")
        val decision = SourceIdentityPolicy.classifyRelink(old.toIdentity(), identityOf(prepared.info, prepared.fingerprint))
        if (decision.match != IdentityMatch.WEAK_MATCH) {
            return@withContext RelinkValidation(
                match = decision.match,
                reason = "Relink confirmation was rejected because the prepared identity is no longer a weak compatible match."
            )
        }
        persistRelink(old, prepared)
        diagnosticLog.add(DiagnosticLevel.INFO, "Weak relink accepted after explicit user confirmation")
        RelinkValidation(IdentityMatch.WEAK_MATCH, "Original media reconnected with weak provider-limited verification.")
    }

    private suspend fun persistVerifiedStatus(asset: MediaAsset, status: SourceStatus): SourceStatus {
        if (status != asset.sourceStatus) {
            updateStatus(asset.id, status)
            diagnosticLog.add(DiagnosticLevel.WARN, "Media source status changed to ${status.name}")
        }
        return status
    }

    private suspend fun findDuplicate(projectId: String, candidate: MediaAssetEntity): MediaAssetEntity? {
        db.mediaAssetDao().findByUri(projectId, candidate.sourceUri)?.let { return it }
        val sha = candidate.fingerprintSha256 ?: return null
        val possible = db.mediaAssetDao().findByFingerprint(projectId, sha)
        return possible.firstOrNull { existing ->
            SourceIdentityPolicy.classifyRelink(existing.toIdentity(), candidate.toIdentity()).match in setOf(
                IdentityMatch.STRONG_MATCH,
                IdentityMatch.WEAK_MATCH
            )
        }
    }

    private suspend fun insertPrepared(entity: MediaAssetEntity) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.mediaAssetDao().insert(entity)
            val project = db.projectDao().get(entity.projectId)?.project
            if (project != null) {
                db.projectDao().update(project.copy(updatedAt = now, lastOpenedAt = now))
            }
        }
    }

    private fun buildEntity(
        projectId: String,
        uri: Uri,
        info: MediaAnalyzer.Result,
        fingerprint: FingerprintResult,
        permissionPersisted: Boolean
    ): MediaAssetEntity {
        val primaryVideo = info.metadata.videoTracks.firstOrNull()
        return MediaAssetEntity(
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
            createdAt = System.currentTimeMillis()
        )
    }

    private suspend fun persistRelink(old: MediaAssetEntity, prepared: PreparedRelink) {
        val newUri = Uri.parse(prepared.sourceUri)
        val info = prepared.info
        val fingerprint = prepared.fingerprint
        val permissionPersisted = persistReadPermission(newUri)
        val primaryVideo = info.metadata.videoTracks.firstOrNull()
        db.mediaAssetDao().update(
            old.copy(
                sourceUri = prepared.sourceUri,
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
                sourceStatus = SourceStatus.AVAILABLE.name
            )
        )
    }

    private fun MediaAsset.toIdentity(): MediaIdentity = MediaIdentity(
        fingerprintSha256 = fingerprintSha256,
        fingerprintStrength = fingerprintStrength,
        sizeBytes = sizeBytes,
        durationUs = durationUs,
        width = width,
        height = height,
        videoCodecMime = videoCodecMime
    )

    private fun MediaAssetEntity.toIdentity(): MediaIdentity = MediaIdentity(
        fingerprintSha256 = fingerprintSha256,
        fingerprintStrength = runCatching { FingerprintStrength.valueOf(fingerprintStrength) }
            .getOrDefault(FingerprintStrength.UNAVAILABLE),
        sizeBytes = sizeBytes,
        durationUs = durationUs,
        width = width,
        height = height,
        videoCodecMime = videoCodecMime
    )

    private fun identityOf(info: MediaAnalyzer.Result, fingerprint: FingerprintResult): MediaIdentity = MediaIdentity(
        fingerprintSha256 = fingerprint.sha256,
        fingerprintStrength = fingerprint.strength,
        sizeBytes = info.sizeBytes,
        durationUs = info.metadata.durationUs,
        width = info.metadata.width,
        height = info.metadata.height,
        videoCodecMime = info.metadata.videoCodecMime
    )

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
