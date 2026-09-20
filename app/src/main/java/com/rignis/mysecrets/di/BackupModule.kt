package com.rignis.mysecrets.di

import com.rignis.backup.api.BackupManager
import com.rignis.backup.api.BackupStager
import com.rignis.backup.core.auth.GoogleAccountAuthenticator
import com.rignis.backup.core.auth.GoogleAccountAuthenticatorImpl
import com.rignis.backup.core.code.BackupCodeStore
import com.rignis.backup.core.crypto.BackupKeyHolder
import com.rignis.backup.core.drive.DriveClient
import com.rignis.backup.core.drive.DriveClientImpl
import com.rignis.backup.core.manager.BackupManagerImpl
import com.rignis.backup.core.meta.VaultMetaStore
import com.rignis.backup.core.stage.BackupStagerImpl
import com.rignis.backup.core.sync.SyncEngine
import org.koin.dsl.bind
import org.koin.dsl.module

val backupModule = module {
    single { GoogleAccountAuthenticatorImpl() } bind GoogleAccountAuthenticator::class
    single { DriveClientImpl() } bind DriveClient::class
    single { BackupKeyHolder() }
    single { VaultMetaStore(get()) }
    single { BackupCodeStore(get()) }
    single { BackupStagerImpl(get(), get()) } bind BackupStager::class
    single { SyncEngine(get(), get(), get()) }
    single {
        BackupManagerImpl(
            authenticator = get(), driveClient = get(), vaultMetaStore = get(), keyHolder = get(),
            backupStager = get(), syncEngine = get(), syncDataStore = get(), dataStore = get(), codeStore = get()
        )
    } bind BackupManager::class
}
