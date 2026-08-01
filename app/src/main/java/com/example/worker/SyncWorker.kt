package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppRepository
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = AppRepository.getInstance(applicationContext)
        val sharedPrefs = applicationContext.getSharedPreferences("daily_check_prefs", Context.MODE_PRIVATE)
        val defaultUrl = "https://script.google.com/macros/s/AKfycbyZ4An-7rYwXwYWbhKxQ3WBjRj7XQizl31g51eGlzNupK1Y6GsTuqikxTvJ1NnNua4B9A/exec"
        val webAppUrl = sharedPrefs.getString("web_app_url", defaultUrl) ?: defaultUrl

        if (webAppUrl.isBlank() || !webAppUrl.startsWith("http")) {
            Log.w("SyncWorker", "Invalid or missing Web App URL, skipping background sync.")
            return Result.failure()
        }

        return try {
            Log.d("SyncWorker", "Starting background sync to $webAppUrl...")
            val result = repository.syncPendingUpdates(webAppUrl)
            Log.d("SyncWorker", "Background sync completed: ${result.second}")

            val message = result.second.lowercase()
            if (message.contains("gagal") || message.contains("terputus") || message.contains("timeout") || message.contains("http 5")) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during background sync execution", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_ONE_TIME_WORK = "DailyStaticOneTimeSync"
        private const val UNIQUE_PERIODIC_WORK = "DailyStaticPeriodicSync"

        /**
         * Enqueues an immediate background sync request.
         * Runs as soon as network connection is available, even if the app is force-closed by user.
         */
        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE,
                syncWorkRequest
            )
        }

        /**
         * Schedules a periodic background sync (every 15 minutes) to ensure local unsynced data
         * eventually gets pushed to Google Sheets in the background.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicSyncRequest
            )
        }
    }
}
