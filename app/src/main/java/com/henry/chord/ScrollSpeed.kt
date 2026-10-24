package com.henry.chord

import androidx.compose.runtime.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.sample

@Stable
class ScrollSpeedSelection(initial: Float) {
    var requested by mutableFloatStateOf(initial)
    var applied by mutableFloatStateOf(initial)
        internal set
}

/** Slider follows the finger; motion/persistence update at most twice per second without restarting. */
@OptIn(FlowPreview::class)
@Composable
fun rememberScrollSpeed(initial: Float, persist: (Float) -> Unit): ScrollSpeedSelection {
    val state=remember { ScrollSpeedSelection(initial) }
    val save by rememberUpdatedState(persist)
    LaunchedEffect(state) {
        snapshotFlow { state.requested }.drop(1).sample(500L).collect {
            state.applied=it
            save(it)
        }
    }
    return state
}
