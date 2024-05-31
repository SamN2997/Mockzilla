package com.apadmi.mockzilla.desktop.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * This ensures that any active coroutines finish running before the given state is emitted.
 * This ensure for example that loading states are not emitted and then immediately replaced.
 *
 * Note: This doesn't await long-running tasks like network requests, it just allows any synchronous code
 * awaiting execution to run.
 *
 * @param vmScope [CoroutineScope] The VM scope associated with the flow
 * @param newState [T] The new state value to be set if needed
 */
internal fun <T> MutableStateFlow<T>.setStateWithYield(vmScope: CoroutineScope, newState: T) {
    val currentState = value

    vmScope.launch {
        yield()
        if (value == currentState) {
            value = newState
        }
    }
}

public fun CoroutineScope.launchUnit(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) = launch(context, start, block).let { Unit }
