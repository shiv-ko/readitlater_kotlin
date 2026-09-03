package com.koukishiba.todobookmark.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkApiModelsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `バッチリクエストは status と source を既定値付きで送る`() {
        val body = BatchRequestBody(
            items = listOf(BatchRequestItem("https://example.com/a")),
        )

        val encoded = json.encodeToString(BatchRequestBody.serializer(), body)

        assertEquals(
            """{"items":[{"url":"https://example.com/a"}],"status":"inbox","source":"android"}""",
            encoded,
        )
    }

    @Test
    fun `バッチレスポンスの created と existing と invalid を判定できる`() {
        val responseJson = """
            {"results":[
              {"url":"https://a.com","status":"created","id":"1"},
              {"url":"https://b.com","status":"existing","id":"2"},
              {"url":"invalid-url","status":"invalid"}
            ]}
        """.trimIndent()

        val decoded = json.decodeFromString(BatchResponseBody.serializer(), responseJson)

        assertEquals(
            listOf(BatchResultStatus.CREATED, BatchResultStatus.EXISTING, BatchResultStatus.INVALID),
            decoded.results.map { it.status },
        )
        assertEquals(null, decoded.results.last().id)
    }
}
