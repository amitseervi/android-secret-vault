package com.rignis.mysecrets.schedulers

import android.content.Context
import androidx.core.content.ContextCompat
import com.rignis.common.ExecutorFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class ExecutorFactoryImpl(context: Context) : ExecutorFactory {
    override val mainExecutor = ContextCompat.getMainExecutor(context)
    override val backgroundExecutor = Executors.newFixedThreadPool(4)!!

    override val mainDispatcher: CoroutineDispatcher = mainExecutor.asCoroutineDispatcher()
    override val backgroundDispatcher: CoroutineDispatcher =
        backgroundExecutor.asCoroutineDispatcher()
}