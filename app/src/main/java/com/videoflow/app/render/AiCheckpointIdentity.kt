package com.videoflow.app.render

import com.videoflow.app.domain.export.*
import com.videoflow.app.domain.ai.AiWatermarkEffect
import com.videoflow.app.domain.effects.VisualEdits
import java.security.MessageDigest

object AiCheckpointIdentity {
    fun key(plan: FinalRenderPlan,settings: ResolvedExportSettings,effects: List<AiWatermarkEffect>,visual: VisualEdits,
        fingerprint: String,intervalUs: Long,modelHash: String): String {
        val identity="segmented-v4-static-overlays-$intervalUs-$fingerprint"+plan.toString()+settings.toString()+
            effects.toString()+(visual.effects.toString()+visual.enhance.toSortedMap().mapValues { (_,p) -> p.values.toSortedMap() }.toString())+modelHash
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
