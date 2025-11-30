package com.rignis.mysecrets.di

import com.rignis.auth.data.CipherManagerImpl
import com.rignis.auth.domain.CipherManager
import org.koin.androidx.scope.dsl.activityScope
import org.koin.dsl.bind
import org.koin.dsl.module

val cipherModule = module {
    activityScope {
        scoped {
            CipherManagerImpl(get(), get())
        } bind CipherManager::class
    }
}