package com.rignis.mysecrets.di

import com.rignis.store.api.DataStore
import com.rignis.store.core.DataStoreFactory
import com.rignis.store.core.DataStoreImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val storeModule = module {
    singleOf(::DataStoreFactory)
    singleOf(::DataStoreImpl) bind DataStore::class
}