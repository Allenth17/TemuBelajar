package com.hiralen.temubelajar.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hiralen.temubelajar.app.DefaultNavigator
import com.hiralen.temubelajar.app.Destination
import com.hiralen.temubelajar.app.Navigator
import com.hiralen.temubelajar.auth.data.database.DatabaseFactory
import com.hiralen.temubelajar.auth.data.database.TokenDatabase
import com.hiralen.temubelajar.auth.data.network.AccountDataSource
import com.hiralen.temubelajar.auth.data.network.RemoteAccountDataSource
import com.hiralen.temubelajar.auth.data.repository.DefaultAccountRepo
import com.hiralen.temubelajar.auth.domain.AccountRepository
import com.hiralen.temubelajar.auth.presentation.AuthViewModel
import com.hiralen.temubelajar.core.data.HttpClientFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    single <Navigator> {
        DefaultNavigator(startDestination = Destination.AuthGraph)
    }
    single {
        get<DatabaseFactory>().create()
            .setDriver(BundledSQLiteDriver())
            .build()
    }
    single { get<TokenDatabase>().dao }
    singleOf(::DefaultAccountRepo).bind<AccountRepository>()
    singleOf(::RemoteAccountDataSource).bind<AccountDataSource>()
    single { HttpClientFactory.create(get()) }
    viewModelOf(::AuthViewModel)
}