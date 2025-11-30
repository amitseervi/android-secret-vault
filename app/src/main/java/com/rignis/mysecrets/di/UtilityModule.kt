package com.rignis.mysecrets.di

import com.rignis.common.ExecutorFactory
import com.rignis.core.ui.routes.detail.ClipBoardHandler
import com.rignis.mysecrets.MainActivity
import com.rignis.mysecrets.schedulers.ExecutorFactoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val utilityModule = module {
    singleOf(::ExecutorFactoryImpl) bind ExecutorFactory::class
    singleOf(::ClipBoardHandler)
}

