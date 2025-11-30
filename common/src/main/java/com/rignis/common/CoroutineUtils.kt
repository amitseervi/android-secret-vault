package com.rignis.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> callbackToSuspend(
    start: (onSuccess: (T) -> Unit, onError: (Throwable) -> Unit) -> Unit
): T = suspendCancellableCoroutine { cont ->
    start({ result ->
        if (cont.isActive) cont.resume(result, onCancellation = {
            cont.resumeWithException(it)
        })
    }, { error ->
        if (cont.isActive) cont.resumeWithException(error)
    })
}