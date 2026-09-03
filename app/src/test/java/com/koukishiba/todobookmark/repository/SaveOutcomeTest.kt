package com.koukishiba.todobookmark.repository

import com.koukishiba.todobookmark.batch.SaveSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveOutcomeTest {
    @Test
    fun `200 は結果summaryをそのまま Completed にする`() {
        val summary = SaveSummary(successCount = 3, failureCount = 0)

        assertEquals(SaveOutcome.Completed(summary), classifyResponse(200, summary, chunkSize = 3))
    }

    @Test
    fun `200 でも summary が欠けていれば全件失敗扱いにする`() {
        assertEquals(
            SaveOutcome.Completed(SaveSummary(successCount = 0, failureCount = 3)),
            classifyResponse(200, null, chunkSize = 3),
        )
    }

    @Test
    fun `400 は再送しない ClientError にする`() {
        assertEquals(
            SaveOutcome.ClientError(SaveSummary(successCount = 0, failureCount = 2)),
            classifyResponse(400, null, chunkSize = 2),
        )
    }

    @Test
    fun `401 と 403 は AuthExpired にする`() {
        assertEquals(SaveOutcome.AuthExpired, classifyResponse(401, null, chunkSize = 1))
        assertEquals(SaveOutcome.AuthExpired, classifyResponse(403, null, chunkSize = 1))
    }

    @Test
    fun `5xx は Retryable にする`() {
        assertEquals(SaveOutcome.Retryable, classifyResponse(500, null, chunkSize = 1))
        assertEquals(SaveOutcome.Retryable, classifyResponse(503, null, chunkSize = 1))
    }

    @Test
    fun `想定外のコードは再送しない ClientError にする`() {
        assertEquals(
            SaveOutcome.ClientError(SaveSummary(successCount = 0, failureCount = 1)),
            classifyResponse(404, null, chunkSize = 1),
        )
    }

    @Test
    fun `通信エラーは Retryable にする`() {
        assertEquals(SaveOutcome.Retryable, classifyNetworkFailure())
    }
}
