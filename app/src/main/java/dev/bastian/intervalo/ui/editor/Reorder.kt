package dev.bastian.intervalo.ui.editor

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drag-to-reorder for a [androidx.compose.foundation.lazy.LazyColumn] whose reorderable items use
 * a `Long` key. Items with any other key (headers, buttons) are never swapped with.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (fromKey: Long, toKey: Long) -> Unit,
) {
    var draggedKey: Long? by mutableStateOf(null)
        private set

    /** Pixels the dragged item is displaced from where the list has laid it out. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** Item order seen when the last swap happened; avoids swapping twice on stale layout info. */
    private var orderAtLastMove: List<Any>? = null

    fun start(key: Long) {
        draggedKey = key
        offset = 0f
        orderAtLastMove = null
    }

    fun drag(delta: Float) {
        val key = draggedKey ?: return
        offset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val order = items.map { it.key }
        if (order == orderAtLastMove) return
        val current = items.firstOrNull { it.key == key } ?: return
        val center = current.offset + current.size / 2f + offset
        val target = items.firstOrNull {
            it.key is Long && it.key != key && center >= it.offset && center <= it.offset + it.size
        } ?: return
        onMove(key, target.key as Long)
        // After the swap the item is laid out at the target's old position; keep it under the finger.
        offset += current.offset - target.offset
        orderAtLastMove = order
    }

    fun end() {
        draggedKey = null
        offset = 0f
        orderAtLastMove = null
    }
}
