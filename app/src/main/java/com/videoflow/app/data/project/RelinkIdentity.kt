package com.videoflow.app.data.project

data class MediaIdentity(
    val fingerprintSha256: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?
)

object RelinkIdentity {
    fun matches(original: MediaIdentity, selected: MediaIdentity): Boolean {
        if (original.fingerprintSha256 == null || selected.fingerprintSha256 == null) return false
        if (original.fingerprintSha256 != selected.fingerprintSha256) return false
        if (original.sizeBytes != null && selected.sizeBytes != null && original.sizeBytes != selected.sizeBytes) return false
        if (original.width != null && selected.width != null && original.width != selected.width) return false
        if (original.height != null && selected.height != null && original.height != selected.height) return false
        return true
    }
}
