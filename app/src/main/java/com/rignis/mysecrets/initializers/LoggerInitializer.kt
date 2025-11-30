package com.rignis.mysecrets.initializers

import android.content.Context
import androidx.startup.Initializer

class CipherInitializer : Initializer<Unit> {
    override fun create(context: Context) {

    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}