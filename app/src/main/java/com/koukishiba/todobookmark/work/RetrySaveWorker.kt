package com.koukishiba.todobookmark.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.koukishiba.todobookmark.auth.AuthManager
import com.koukishiba.todobookmark.network.ApiClient
import com.koukishiba.todobookmark.repository.BookmarkRepository

private const val KEY_URLS = "urls"
private const val KEY_STATUS = "status"
private const val URL_SEPARATOR = "\n"

/**
 * WorkManagerが保持するのはURLと保存状態のみで、認証トークンは含めない。
 * 実行時に最新のCognitoセッションから取得する（AuthManager経由）。
 */
class RetrySaveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val urls = inputData.getString(KEY_URLS)
            ?.split(URL_SEPARATOR)
            .orEmpty()
            .filter(String::isNotBlank)
        val status = inputData.getString(KEY_STATUS) ?: "inbox"
        if (urls.isEmpty()) return Result.success()

        val authManager = AuthManager()
        val api = ApiClient.create(authManager)
        val repository = BookmarkRepository(api)

        val result = repository.save(urls, status)
        return result.outcome.toWorkResult()
    }

    companion object {
        fun inputData(urls: List<String>, status: String): Data =
            Data.Builder()
                .putString(KEY_URLS, urls.joinToString(URL_SEPARATOR))
                .putString(KEY_STATUS, status)
                .build()
    }
}
