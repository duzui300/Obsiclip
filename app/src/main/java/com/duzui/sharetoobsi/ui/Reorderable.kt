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
 * Compose reuses a composition slot by *position*, not by identity. Everything here is
 * therefore addressed by position: the drag's own index, and the measured extents. Keying
 * either by item id leaves a slot holding the previous occupant's value once the order
 * changes, and the item that follows the finger stops being the one being dragged — while
 * the reorder itself still lands correctly, so it reads as a purely visual glitch.
 */
class ReorderState {

    var dragging by mutableStateOf<Any?>(null)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    /** Item count as composed, so a walk knows where the list ends. */
    var itemCount: Int = 0

    private val extents = mutableMapOf<Int, Float>()

    /**
     * The dragged item's own index, owned here rather than read from the composition.
     *
     * A drag delivers several events per frame, so an index that only updates on
     * recomposition is already stale by the second one — and a stale index makes the next
     * walk start from the wrong place. That is what stopped a drag crossing more than one
     * item, which read as "you cannot drag the bottom one to the top".
     */
    private var index = 0

    /** Recorded by each item as it is laid out, against the position it occupies. */
    fun extent(position: Int, px: Float) {
        extents[position] = px
    }

    fun begin(key: Any, fromIndex: Int) {
        dragging = key
        index = fromIndex
        offset = 0f
    }

    fun end() {
        dragging = null
        offset = 0f
    }

    /**
     * Adds [delta] to the drag and returns the move it implies, or null if it moved nothing.
     *
     * Walks the neighbours' measured extents rather than assuming equal sizes. Using the
     * dragged item's own size — the obvious first attempt — makes a long book name and a
     * short one swap after different amounts of finger travel.
     */
    fun advance(delta: Float): Pair<Int, Int>? {
        offset += delta
        val from = index

        if (offset > 0f) {
            while (index < itemCount - 1) {
                val next = extents[index + 1] ?: break
                if (offset < next) break
                offset -= next
                index++
            }
        } else {
            while (index > 0) {
                val previous = extents[index - 1] ?: break
                if (-offset < previous) break
                offset += previous
                index--
            }
        }
        return if (index != from) from to index else null
    }
}

@Composable
fun rememberReorderState(itemCount: Int): ReorderState {
    val state = remember { ReorderState() }
    state.itemCount = itemCount
    return state
}

/**
 * Long-press and drag to reorder, with the dragged item tracking the finger.
 *
 * [pointerInput] is keyed on Unit so the drag survives the swaps it causes, which means the
 * key and index it reads must be live values rather than what they were when the block was
 * built — hence [rememberUpdatedState] on both.
 */
@Composable
fun Modifier.reorderDrag(
    key: Any,
    index: Int,
    state: ReorderState,
    horizontal: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier {
    val currentKey by rememberUpdatedState(key)
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
            state.extent(currentIndex, (if (horizontal) size.width else size.height).toFloat())
        }
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.begin(currentKey, currentIndex) },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            ) { _, drag ->
                state.advance(if (horizontal) drag.x else drag.y)?.let { (from, to) ->
                    currentOnMove(from, to)
                }
            }
        }
}
