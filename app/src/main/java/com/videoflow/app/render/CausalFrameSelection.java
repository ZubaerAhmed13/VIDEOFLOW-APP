package com.videoflow.app.render;

/** Ranking for a CFR compositor: prefer the latest frame at or before the output timestamp. */
public final class CausalFrameSelection {
    private CausalFrameSelection() {}

    public static long distanceUs(long candidateMinusOutputUs) {
        // Before a secondary stream's first frame, retain its first future frame as a fallback;
        // timeline visibility still hides the layer until its authored start.
        if (candidateMinusOutputUs > 0) return Long.MAX_VALUE - 1;
        return candidateMinusOutputUs == Long.MIN_VALUE ? Long.MAX_VALUE - 2 : -candidateMinusOutputUs;
    }
}
