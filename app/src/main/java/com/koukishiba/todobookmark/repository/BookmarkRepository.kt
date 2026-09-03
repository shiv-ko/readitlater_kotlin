package com.koukishiba.todobookmark.repository

import com.koukishiba.todobookmark.batch.SaveSummary
import com.koukishiba.todobookmark.batch.chunkUrls
import com.koukishiba.todobookmark.batch.toSaveSummary
import com.koukishiba.todobookmark.network.BatchRequestBody
import com.koukishiba.todobookmark.network.BatchRequestItem
import com.koukishiba.todobookmark.network.BookmarkApi
import java.io.IOException

data class SaveProgress(val processed: Int, val total: Int)

data class SaveResult(val outcome: SaveOutcome, val pendingUrls: List<String>)

/** URLを20件ずつのバッチへ分割し、順番にBookmark APIへ送信する。 */
class BookmarkRepository(private val api: BookmarkApi) {

    suspend fun save(
        urls: List<String>,
        status: String = "inbox",
        onProgress: (SaveProgress) -> Unit = {},
    ): SaveResult {
        val chunks = chunkUrls(urls)
        var summary = SaveSummary.ZERO
        var processed = 0

        chunks.forEachIndexed { index, chunk ->
            when (val outcome = sendChunk(chunk, status)) {
                is SaveOutcome.Completed -> {
                    summary += outcome.summary
                    processed += chunk.size
                    onProgress(SaveProgress(processed, urls.size))
                }
                is SaveOutcome.ClientError -> {
                    summary += outcome.summary
                    processed += chunk.size
                    onProgress(SaveProgress(processed, urls.size))
                }
                SaveOutcome.AuthExpired -> {
                    return SaveResult(SaveOutcome.AuthExpired, remainingUrlsFrom(chunks, index))
                }
                SaveOutcome.Retryable -> {
                    return SaveResult(SaveOutcome.Retryable, remainingUrlsFrom(chunks, index))
                }
            }
        }

        val finalOutcome = if (summary.failureCount > 0) {
            SaveOutcome.ClientError(summary)
        } else {
            SaveOutcome.Completed(summary)
        }
        return SaveResult(finalOutcome, pendingUrls = emptyList())
    }

    private suspend fun sendChunk(chunk: List<String>, status: String): SaveOutcome {
        val body = BatchRequestBody(items = chunk.map(::BatchRequestItem), status = status)
        return try {
            val response = api.postBatch(body)
            val summary = response.body()?.results?.toSaveSummary()
            classifyResponse(response.code(), summary, chunk.size)
        } catch (error: IOException) {
            classifyNetworkFailure()
        }
    }

    private fun remainingUrlsFrom(chunks: List<List<String>>, fromIndex: Int): List<String> =
        chunks.drop(fromIndex).flatten()
}
