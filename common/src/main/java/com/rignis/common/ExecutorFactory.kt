package com.rignis.common

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.Executor

interface ExecutorFactory {
    val mainExecutor: Executor
    val backgroundExecutor: Executor
    val mainDispatcher: CoroutineDispatcher
    val backgroundDispatcher: CoroutineDispatcher
}