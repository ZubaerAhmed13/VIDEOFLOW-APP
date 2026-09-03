package com.videoflow.app.project

import com.videoflow.app.data.project.IdentityMatch
import com.videoflow.app.data.project.MediaIdentity
import com.videoflow.app.data.project.RelinkIdentity
import com.videoflow.app.data.project.SourceIdentityPolicy
import com.videoflow.app.domain.model.FingerprintStrength
import com.videoflow.app.domain.model.SourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelinkPolicyTest {
    private val strongOriginal = identity("abc", FingerprintStrength.STRONG_THREE_REGION)
    private val weakOriginal = identity("weak", FingerprintStrength.WEAK_FIRST_REGION_ONLY)

    @Test
    fun strongToStrongExactMatchPasses() {
        val decision = SourceIdentityPolicy.classifyRelink(
            strongOriginal,
            identity("abc", FingerprintStrength.STRONG_THREE_REGION)
        )
        assertEquals(IdentityMatch.STRONG_MATCH, decision.match)
        assertTrue(RelinkIdentity.matches(strongOriginal, identity("abc", FingerprintStrength.STRONG_THREE_REGION)))
    }

    @Test
    fun strongShaMismatchRejects() {
        assertEquals(
            IdentityMatch.MISMATCH,
            SourceIdentityPolicy.classifyRelink(
                strongOriginal,
                identity("def", FingerprintStrength.STRONG_THREE_REGION)
            ).match
        )
    }

    @Test
    fun strongToWeakNeverAutoApproves() {
        assertEquals(
            IdentityMatch.UNVERIFIABLE,
            SourceIdentityPolicy.classifyRelink(
                strongOriginal,
                identity("abc", FingerprintStrength.WEAK_FIRST_REGION_ONLY)
            ).match
        )
        assertFalse(RelinkIdentity.matches(strongOriginal, identity("abc", FingerprintStrength.WEAK_FIRST_REGION_ONLY)))
    }

    @Test
    fun weakToWeakMatchRequiresConfirmation() {
        assertEquals(
            IdentityMatch.WEAK_MATCH,
            SourceIdentityPolicy.classifyRelink(
                weakOriginal,
                identity("weak", FingerprintStrength.WEAK_FIRST_REGION_ONLY)
            ).match
        )
    }

    @Test
    fun weakShaMismatchRejects() {
        assertEquals(
            IdentityMatch.MISMATCH,
            SourceIdentityPolicy.classifyRelink(
                weakOriginal,
                identity("other", FingerprintStrength.WEAK_FIRST_REGION_ONLY)
            ).match
        )
    }

    @Test
    fun unavailableFingerprintIsNeverExact() {
        assertEquals(
            IdentityMatch.UNVERIFIABLE,
            SourceIdentityPolicy.classifyRelink(
                identity(null, FingerprintStrength.UNAVAILABLE),
                identity(null, FingerprintStrength.UNAVAILABLE)
            ).match
        )
    }

    @Test
    fun knownSizeMismatchRejects() {
        assertEquals(
            IdentityMatch.MISMATCH,
            SourceIdentityPolicy.classifyRelink(
                strongOriginal,
                identity("abc", FingerprintStrength.STRONG_THREE_REGION, sizeBytes = 10_001L)
            ).match
        )
    }

    @Test
    fun knownDimensionMismatchRejects() {
        assertEquals(
            IdentityMatch.MISMATCH,
            SourceIdentityPolicy.classifyRelink(
                strongOriginal,
                identity("abc", FingerprintStrength.STRONG_THREE_REGION, width = 1920, height = 1080)
            ).match
        )
    }

    @Test
    fun sameSavedSourceIsAvailable() {
        assertEquals(
            SourceStatus.AVAILABLE,
            SourceIdentityPolicy.classifyCurrentSource(
                strongOriginal,
                identity("abc", FingerprintStrength.STRONG_THREE_REGION)
            )
        )
    }

    @Test
    fun changedStrongFingerprintBecomesChanged() {
        assertEquals(
            SourceStatus.CHANGED,
            SourceIdentityPolicy.classifyCurrentSource(
                strongOriginal,
                identity("changed", FingerprintStrength.STRONG_THREE_REGION)
            )
        )
    }

    @Test
    fun changedSizeBecomesChangedEvenIfFingerprintMatches() {
        assertEquals(
            SourceStatus.CHANGED,
            SourceIdentityPolicy.classifyCurrentSource(
                strongOriginal,
                identity("abc", FingerprintStrength.STRONG_THREE_REGION, sizeBytes = 11_000L)
            )
        )
    }

    @Test
    fun changedDimensionsBecomeChangedEvenIfFingerprintMatches() {
        assertEquals(
            SourceStatus.CHANGED,
            SourceIdentityPolicy.classifyCurrentSource(
                strongOriginal,
                identity("abc", FingerprintStrength.STRONG_THREE_REGION, width = 1280, height = 720)
            )
        )
    }

    @Test
    fun strongSourceThatCanOnlyBeWeaklyRecheckedIsUnknownNotChanged() {
        assertEquals(
            SourceStatus.UNKNOWN,
            SourceIdentityPolicy.classifyCurrentSource(
                strongOriginal,
                identity("abc", FingerprintStrength.WEAK_FIRST_REGION_ONLY)
            )
        )
    }

    private fun identity(
        sha: String?,
        strength: FingerprintStrength,
        sizeBytes: Long? = 10_000L,
        durationUs: Long? = 60_000_000L,
        width: Int? = 3840,
        height: Int? = 2160,
        codec: String? = "video/avc"
    ) = MediaIdentity(
        fingerprintSha256 = sha,
        fingerprintStrength = strength,
        sizeBytes = sizeBytes,
        durationUs = durationUs,
        width = width,
        height = height,
        videoCodecMime = codec
    )
}
