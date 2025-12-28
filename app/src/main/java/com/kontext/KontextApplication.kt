package com.kontext

import android.app.Application
import com.kontext.util.SeedManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KontextApplication : Application() {

    @Inject
    lateinit var seedManager: SeedManager

    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        
        // Trigger seeding on app launch
        applicationScope.launch {
            seedManager.seedIfNeeded()
        }
    }
}
