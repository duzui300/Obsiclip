package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Holds a long-press drag so the item can follow the finger.
 *
 * The offset is measured from the slot the item currently occupies, and is reduced by
 * exactly the extents it walks past — which is what keeps the item under the finger.
 */
class ReorderState {

    var dragging by mutableStateOf<Any?>(null)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    /** The order as composed, so a walk knows what comes next. */
    var keys: List<Any> = emptyList()

    private val extents = mutableMapOf<Any, Float>()

    /** Recorded by each item as it is laid out. */
    fun extent(key: Any, px: Float) {
        extents[key] = px
    }

    fun begin(key: Any) {
        dragging = key
        offset = 0f
    }

    fun end() {
        dragging = null
        offset = 0f
    }

    /**
     * Adds [delta] to the drag and returns the index the item should now sit at.
     *
     * Walks the neighbours' measured extents rather than assuming equal sizes. Using the
     * dragged item's own size — the obvious first attempt — makes a long book name and a
     * short one swap after different amounts of finger travel, and cannot cross two items
     * in a single fast drag. Both of those were live bugs.
     */
    fun advance(fromIndex: Int, delta: Float): Int {
        offset += delta
        var index = fromIndex

        if (offset > 0f) {
            while (index < keys.lastIndex) {
                val next = extents[keys[index + 1]] ?: break
                if (offset < next) break
                offset -= next
                index++
            }
        } else {
            while (index > 0) {
                val previous = extents[keys[index - 1]] ?: break
                if (-offset < previous) break
                offset += previous
                index--
            }
        }
        return index
    }
}

@Composable
fun rememberReorderState(keys: List<Any>): ReorderState {
    val state = remember { ReorderState() }
    state.keys = keys
    return state
}

/**
 * Long-press and drag to reorder, with the dragged item tracking the finger.
 *
 * [pointerInput] is keyed on Unit and the live index is read through [rememberUpdatedState]:
 * the swap this gesture causes changes the index, and keying on it would cancel the drag
 * the instant it moved something.
 */
@Composable
fun Modifier.reorderDrag(
    key: Any,
    index: Int,
    state: ReorderState,
    horizontal: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier {
    val currentIndex by rememberUpdatedState(index)
    val currentOnMove by rememberUpdatedState(onMove)
    val active = state.dragging == key

    return this
        .zIndex(if (active) 1f else 0f)
        // The lambda overload keeps this to a layout pass per frame, not a recomposition.
        .offset {
            if (!active) {
                IntOffset.Zero
            } else {
                val px = state.offset.roundToInt()
                if (horizontal) IntOffset(px, 0) else IntOffset(0, px)
            }
        }
        .onSizeChanged { size ->
            state.extent(key, (if (horizontal) size.width else size.height).toFloat())
        }
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.begin(key) },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            ) { _, drag ->
                val from = currentIndex
                val to = state.advance(from, if (horizontal) drag.x else drag.y)
                if (to != from) currentOnMove(from, to)
            }
        }
}
