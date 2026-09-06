package com.videoflow.app.domain.editor

/** Pure ordering policy for the first-class Merge Videos workflow. */
object MergeOrdering {
    fun <T> move(items: List<T>, index: Int, delta: Int): List<T> {
        val target = index + delta
        if (index !in items.indices || target !in items.indices || delta == 0) return items
        return items.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
    }
}
