package com.duzui.sharetoobsi.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Long-press and drag to move an item one slot at a time, along [horizontal] or not.
 *
 * Threshold-based rather than measuring neighbours: the chips have different widths, so
 * there is no single slot size to divide by. Crossing the threshold swaps with the
 * neighbour and resets, which reads the same and needs no layout arithmetic.
 *
 * The gesture has to survive the swap it triggers, so [pointerInput] is keyed on Unit and
 * the live index is read through [rememberUpdatedState] — keying on the index would cancel
 * the drag the instant it moved something.
 */
@Composable
fun rememberDragReorder(
    index: Int,
    itemCount: Int,
    horizontal: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier {
    val currentIndex by rememberUpdatedState(index)
    val currentCount by rememberUpdatedState(itemCount)
    val currentOnMove by rememberUpdatedState(onMove)

    return remember {
        Modifier.pointerInput(Unit) {
            var travelled = 0f
            val threshold = 88.dp.toPx()

            detectDragGesturesAfterLongPress(
                onDragStart = { travelled = 0f },
                onDragEnd = { travelled = 0f },
                onDragCancel = { travelled = 0f },
            ) { _, drag ->
                travelled += if (horizontal) drag.x else drag.y
                val from = currentIndex
                if (travelled >= threshold && from < currentCount - 1) {
                    currentOnMove(from, from + 1)
                    travelled = 0f
                } else if (travelled <= -threshold && from > 0) {
                    currentOnMove(from, from - 1)
                    travelled = 0f
                }
            }
        }
    }
}
