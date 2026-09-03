package com.koukishiba.todobookmark.repository

import com.koukishiba.todobookmark.batch.SaveSummary
import com.koukishiba.todobookmark.network.BatchRequestBody
import com.koukishiba.todobookmark.network.BatchResponseBody
import com.koukishiba.todobookmark.network.BatchResultItem
import com.koukishiba.todobookmark.network.BatchResultStatus
import com.koukishiba.todobookmark.network.BookmarkApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

private class FakeBookmarkApi(
    private val responder: (BatchRequestBody) -> Response<BatchResponseBody>,
) : BookmarkApi {
    val requests = mutableListOf<BatchRequestBody>()

    override suspend fun postBatch(body: BatchRequestBody): Response<BatchResponseBody> {
        requests += body
        return responder(body)
    }
}

class BookmarkRepositoryTest {
    @Test
    fun `21件のURLは20件と1件のバッチに分割して順番に送信する`() = runTest {
        val api = FakeBookmarkApi { body ->
            Response.success(
                BatchResponseBody(body.items.map { BatchResultItem(it.url, BatchResultStatus.CREATED, id = it.url) }),
            )
        }
        val repository = BookmarkRepository(api)
        val urls = (1..21).map { "https://example.com/$it" }

        val result = repository.save(urls)

        assertEquals(2, api.requests.size)
        assertEquals(20, api.requests[0].items.size)
        assertEquals(1, api.requests[1].items.size)
        assertEquals(SaveOutcome.Completed(SaveSummary(successCount = 21, failureCount = 0)), result.outcome)
        assertEquals(emptyList<String>(), result.pendingUrls)
    }

    @Test
    fun `5xxが返った以降のバッチは再送対象として残す`() = runTest {
        var callCount = 0
        val api = FakeBookmarkApi { body ->
            callCount++
            if (callCount == 1) {
                Response.success(
                    BatchResponseBody(body.items.map { BatchResultItem(it.url, BatchResultStatus.CREATED, id = it.url) }),
                )
            } else {
                Response.error(503, "".toResponseBody(null))
            }
        }
        val repository = BookmarkRepository(api)
        val urls = (1..45).map { "https://example.com/$it" }

        val result = repository.save(urls)

        assertEquals(SaveOutcome.Retryable, result.outcome)
        assertEquals(25, result.pendingUrls.size)
        assertEquals("https://example.com/21", result.pendingUrls.first())
    }

    @Test
    fun `invalidを含むバッチは再送せず失敗件数として集計する`() = runTest {
        val api = FakeBookmarkApi { body ->
            val results = body.items.mapIndexed { index, item ->
                val status = if (index == 0) BatchResultStatus.INVALID else BatchResultStatus.CREATED
                BatchResultItem(item.url, status, id = if (status == BatchResultStatus.CREATED) item.url else null)
            }
            Response.success(BatchResponseBody(results))
        }
        val repository = BookmarkRepository(api)
        val urls = listOf("invalid-url", "https://example.com/ok1", "https://example.com/ok2")

        val result = repository.save(urls)

        assertEquals(SaveOutcome.ClientError(SaveSummary(successCount = 2, failureCount = 1)), result.outcome)
        assertEquals(emptyList<String>(), result.pendingUrls)
    }

    @Test
    fun `401はAuthExpiredとして未送信分すべてを残す`() = runTest {
        val api = FakeBookmarkApi { Response.error(401, "".toResponseBody(null)) }
        val repository = BookmarkRepository(api)
        val urls = listOf("https://example.com/a")

        val result = repository.save(urls)

        assertEquals(SaveOutcome.AuthExpired, result.outcome)
        assertEquals(urls, result.pendingUrls)
    }

    @Test
    fun `進捗コールバックは処理済み件数を通知する`() = runTest {
        val api = FakeBookmarkApi { body ->
            Response.success(
                BatchResponseBody(body.items.map { BatchResultItem(it.url, BatchResultStatus.CREATED, id = it.url) }),
            )
        }
        val repository = BookmarkRepository(api)
        val urls = (1..25).map { "https://example.com/$it" }
        val progressUpdates = mutableListOf<SaveProgress>()

        repository.save(urls) { progressUpdates += it }

        assertEquals(listOf(SaveProgress(20, 25), SaveProgress(25, 25)), progressUpdates)
    }
}
