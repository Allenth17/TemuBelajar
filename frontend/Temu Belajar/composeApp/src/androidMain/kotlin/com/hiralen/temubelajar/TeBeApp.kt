package com.hiralen.temubelajar

import android.app.Application
import com.hiralen.temubelajar.di.initKoin
import org.koin.android.ext.koin.androidContext

class TeBeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TeBeApp)
        }
    }
}