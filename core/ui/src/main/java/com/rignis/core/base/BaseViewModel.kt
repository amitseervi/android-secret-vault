package com.rignis.core.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

abstract class BaseViewModel<S, E> : ViewModel() {
    abstract val state: StateFlow<S>

    abstract fun onAction(e: E)
}