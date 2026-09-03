package com.videoflow.app.data.project

import com.videoflow.app.data.media.CorruptedMediaException
import com.videoflow.app.data.media.UnsupportedMediaException
import com.videoflow.app.domain.model.FingerprintStrength
import com.videoflow.app.domain.model.SourceStatus
import java.io.FileNotFoundException
import kotlin.math.abs
import kotlin.math.max

data class MediaIdentity(
    val fingerprintSha256: String?,
    val fingerprintStrength: FingerprintStrength,
    val sizeBytes: Long?,
    val durationUs: Long?,
    val width: Int?,
    val height: Int?,
    val videoCodecMime: String?
)

enum class IdentityMatch {
    STRONG_MATCH,
    WEAK_MATCH,
    MISMATCH,
    UNVERIFIABLE
}

data class IdentityDecision(
    val match: IdentityMatch,
    val reason: String
)

object SourceIdentityPolicy {
    private const val MIN_DURATION_TOLERANCE_US = 1_000_000L

    fun classifyRelink(original: MediaIdentity, selected: MediaIdentity): IdentityDecision {
        contradictionReason(original, selected)?.let {
            return IdentityDecision(IdentityMatch.MISMATCH, it)
        }

        val originalStrong = original.fingerprintStrength.isStrong()
        val selectedStrong = selected.fingerprintStrength.isStrong()

        if (originalStrong) {
            if (!selectedStrong || original.fingerprintSha256 == null || selected.fingerprintSha256 == null) {
                return IdentityDecision(
                    IdentityMatch.UNVERIFIABLE,
                    "The saved source requires strong fingerprint verification, but the selected provider did not provide an equivalently strong identity."
                )
            }
            return if (original.fingerprintSha256 == selected.fingerprintSha256) {
                IdentityDecision(IdentityMatch.STRONG_MATCH, "Strong fingerprint and known technical identity match.")
            } else {
                IdentityDecision(IdentityMatch.MISMATCH, "Strong fingerprint differs from the saved original media.")
            }
        }

        if (original.fingerprintStrength == FingerprintStrength.WEAK_FIRST_REGION_ONLY) {
            if (selected.fingerprintStrength != FingerprintStrength.WEAK_FIRST_REGION_ONLY ||
                original.fingerprintSha256 == null || selected.fingerprintSha256 == null
            ) {
                return IdentityDecision(
                    IdentityMatch.UNVERIFIABLE,
                    "The saved source has only weak provider-limited identity and cannot be automatically verified against this selection."
                )
            }
            return if (original.fingerprintSha256 == selected.fingerprintSha256) {
                IdentityDecision(
                    IdentityMatch.WEAK_MATCH,
                    "Weak first-region fingerprint and known technical characteristics match; explicit confirmation is required."
                )
            } else {
                IdentityDecision(IdentityMatch.MISMATCH, "Weak fingerprint differs from the saved original media.")
            }
        }

        return IdentityDecision(
            IdentityMatch.UNVERIFIABLE,
            "The saved source has no cryptographic fingerprint, so VideoFlow cannot identify this selection as an exact original."
        )
    }

    fun classifyCurrentSource(original: MediaIdentity, current: MediaIdentity): SourceStatus {
        if (contradictionReason(original, current) != null) return SourceStatus.CHANGED

        return when (original.fingerprintStrength) {
            FingerprintStrength.FULL_SMALL_FILE,
            FingerprintStrength.STRONG_THREE_REGION -> {
                if (!current.fingerprintStrength.isStrong() || original.fingerprintSha256 == null || current.fingerprintSha256 == null) {
                    SourceStatus.UNKNOWN
                } else if (original.fingerprintSha256 == current.fingerprintSha256) {
                    SourceStatus.AVAILABLE
                } else {
                    SourceStatus.CHANGED
                }
            }
            FingerprintStrength.WEAK_FIRST_REGION_ONLY -> {
                if (current.fingerprintStrength != FingerprintStrength.WEAK_FIRST_REGION_ONLY ||
                    original.fingerprintSha256 == null || current.fingerprintSha256 == null
                ) {
                    SourceStatus.UNKNOWN
                } else if (original.fingerprintSha256 == current.fingerprintSha256) {
                    SourceStatus.AVAILABLE
                } else {
                    SourceStatus.CHANGED
                }
            }
            FingerprintStrength.UNAVAILABLE -> SourceStatus.AVAILABLE
        }
    }

    fun contradictionReason(original: MediaIdentity, selected: MediaIdentity): String? {
        if (knownDifferent(original.sizeBytes, selected.sizeBytes)) return "Known file size differs from the saved source."
        if (knownDifferent(original.width, selected.width) || knownDifferent(original.height, selected.height)) {
            return "Known video dimensions differ from the saved source."
        }
        if (original.videoCodecMime != null && selected.videoCodecMime != null && original.videoCodecMime != selected.videoCodecMime) {
            return "Known video codec differs from the saved source."
        }
        if (original.durationUs != null && selected.durationUs != null &&
            abs(original.durationUs - selected.durationUs) > durationTolerance(original.durationUs)
        ) {
            return "Known duration differs from the saved source."
        }
        return null
    }

    private fun durationTolerance(durationUs: Long): Long =
        max(MIN_DURATION_TOLERANCE_US, abs(durationUs) / 1000L)

    private fun <T> knownDifferent(a: T?, b: T?): Boolean = a != null && b != null && a != b

    private fun FingerprintStrength.isStrong(): Boolean =
        this == FingerprintStrength.FULL_SMALL_FILE || this == FingerprintStrength.STRONG_THREE_REGION
}

object SourceAccessFailurePolicy {
    fun classify(error: Throwable): SourceStatus = when (error) {
        is SecurityException -> SourceStatus.PERMISSION_LOST
        is FileNotFoundException -> SourceStatus.MISSING
        is UnsupportedMediaException -> SourceStatus.UNSUPPORTED
        is CorruptedMediaException -> SourceStatus.CORRUPTED
        else -> SourceStatus.UNKNOWN
    }
}

/** Compatibility helper retained for existing callers/tests; exact match means strong only. */
object RelinkIdentity {
    fun matches(original: MediaIdentity, selected: MediaIdentity): Boolean =
        SourceIdentityPolicy.classifyRelink(original, selected).match == IdentityMatch.STRONG_MATCH
}
