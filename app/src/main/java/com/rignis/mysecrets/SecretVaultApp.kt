package com.rignis.mysecrets

import android.app.Application
import com.rignis.mysecrets.di.cipherModule
import com.rignis.mysecrets.di.storeModule
import com.rignis.mysecrets.di.utilityModule
import com.rignis.mysecrets.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class SecretVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.LOGGING_ENABLED) {
            Timber.plant(Timber.DebugTree())
        }
        startKoin {
            androidContext(this@SecretVaultApp)
            modules(utilityModule, cipherModule, viewModelModule, storeModule)
        }
    }
}