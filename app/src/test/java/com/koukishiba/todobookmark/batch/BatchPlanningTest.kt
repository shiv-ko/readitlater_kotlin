package com.koukishiba.todobookmark.batch

import com.koukishiba.todobookmark.network.BatchResultItem
import com.koukishiba.todobookmark.network.BatchResultStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchPlanningTest {
    @Test
    fun `21件は20件と1件のバッチに分割する`() {
        val urls = (1..21).map { "https://example.com/$it" }

        val chunks = chunkUrls(urls)

        assertEquals(2, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(1, chunks[1].size)
        assertEquals(urls, chunks.flatten())
    }

    @Test
    fun `20件はちょうど1バッチになる`() {
        val urls = (1..20).map { "https://example.com/$it" }

        assertEquals(1, chunkUrls(urls).size)
    }

    @Test
    fun `空リストはバッチを生成しない`() {
        assertEquals(emptyList<List<String>>(), chunkUrls(emptyList()))
    }

    @Test
    fun `SaveSummary は加算できる`() {
        val total = SaveSummary(successCount = 2, failureCount = 1) + SaveSummary(successCount = 3, failureCount = 0)

        assertEquals(SaveSummary(successCount = 5, failureCount = 1), total)
    }

    @Test
    fun `created と existing は成功、invalid は失敗として集計する`() {
        val results = listOf(
            BatchResultItem("https://a.com", BatchResultStatus.CREATED, id = "1"),
            BatchResultItem("https://b.com", BatchResultStatus.EXISTING, id = "2"),
            BatchResultItem("invalid-url", BatchResultStatus.INVALID),
        )

        assertEquals(SaveSummary(successCount = 2, failureCount = 1), results.toSaveSummary())
    }
}
