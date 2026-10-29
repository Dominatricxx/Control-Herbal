package com.example.controlherbal.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.controlherbal.common.auth.AuthManager
import com.example.controlherbal.common.security.SecureLogger
import com.example.controlherbal.data.database.SensorDatabase
import com.example.controlherbal.data.repository.UserRepositoryImpl

class ProfileSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID)?.takeIf(String::isNotBlank) ?: return Result.failure()
        if (AuthManager.uid() != uid) return Result.success()
        if (!AuthManager.refreshUser()) return Result.retry()
        if (!AuthManager.isEmailVerified()) return Result.success()

        val database = SensorDatabase.getInstance(applicationContext)
        val profile = database.userDao().getUserById(uid) ?: return Result.success()
        val repository = UserRepositoryImpl(applicationContext, database.userDao())
        return if (repository.syncVerifiedProfileToCloud(profile)) {
            Result.success()
        } else {
            SecureLogger.w(TAG, "Sincronización pendiente para perfil local")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ProfileSyncWorker"
        private const val KEY_UID = "profile_uid"

        fun enqueue(context: Context, uid: String) {
            val request = OneTimeWorkRequestBuilder<ProfileSyncWorker>()
                .setInputData(workDataOf(KEY_UID to uid))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "profile-sync-$uid",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
