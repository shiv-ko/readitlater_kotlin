package com.koukishiba.todobookmark.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val BACKOFF_DELAY_SECONDS = 30L

    /** ネットワーク接続制約付きで、指数バックオフによる再送をキューへ登録する。 */
    fun enqueueRetry(context: Context, urls: List<String>, status: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RetrySaveWorker>()
            .setInputData(RetrySaveWorker.inputData(urls, status))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
