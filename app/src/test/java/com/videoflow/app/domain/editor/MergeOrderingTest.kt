package com.videoflow.app.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MergeOrderingTest {
    @Test
    fun movesSelectedVideoWithoutChangingOtherRelativeOrder() {
        val input = listOf("A", "B", "C", "D")
        assertEquals(listOf("A", "C", "B", "D"), MergeOrdering.move(input, 1, 1))
        assertEquals(listOf("A", "B", "D", "C"), MergeOrdering.move(input, 3, -1))
    }

    @Test
    fun invalidMoveLeavesSelectionUnchanged() {
        val input = listOf("A", "B", "C")
        assertSame(input, MergeOrdering.move(input, 0, -1))
        assertSame(input, MergeOrdering.move(input, 2, 1))
    }

    @Test
    fun intentionalDuplicateSelectionsRemainDistinctOrderedEntries() {
        val input = listOf("clip-1", "clip-1", "clip-2")
        assertEquals(listOf("clip-1", "clip-2", "clip-1"), MergeOrdering.move(input, 1, 1))
    }
}
