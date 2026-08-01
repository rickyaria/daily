package com.example

import android.app.Application
import com.example.data.AppRepository
import com.example.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyStaticApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize default units if database is empty
        CoroutineScope(Dispatchers.IO).launch {
            val repository = AppRepository.getInstance(applicationContext)
            repository.initializeDefaultUnitsIfEmpty()
        }

        // Schedule periodic background sync and enqueue immediate sync on startup
        SyncWorker.schedulePeriodicSync(this)
        SyncWorker.enqueueOneTimeSync(this)
    }
}
