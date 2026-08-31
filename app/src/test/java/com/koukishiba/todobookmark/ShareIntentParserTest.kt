package com.koukishiba.todobookmark

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareIntentParserTest {
    @Test
    fun `複数の HTTP URL を出現順に抽出する`() {
        val input = """
            Google
            https://google.com

            Example
            http://example.com/article?id=1
        """.trimIndent()

        assertEquals(
            listOf("https://google.com", "http://example.com/article?id=1"),
            ShareIntentParser.extractUrls(listOf(input)),
        )
    }

    @Test
    fun `同じ URL の重複を除く`() {
        val inputs = listOf(
            "https://example.com https://example.com",
            "もう一度 https://example.com",
        )

        assertEquals(
            listOf("https://example.com"),
            ShareIntentParser.extractUrls(inputs),
        )
    }

    @Test
    fun `URL 直後の日本語区切り文字を含めない`() {
        val input = "https://example.com）です。【https://example.org/path】を見てください。"

        assertEquals(
            listOf("https://example.com", "https://example.org/path"),
            ShareIntentParser.extractUrls(listOf(input)),
        )
    }

    @Test
    fun `末尾のカンマとピリオドを取り除く`() {
        val input = "https://example.com/path... https://example.org/test,"

        assertEquals(
            listOf("https://example.com/path", "https://example.org/test"),
            ShareIntentParser.extractUrls(listOf(input)),
        )
    }

    @Test
    fun `URL がなければ空のリストを返す`() {
        assertEquals(
            emptyList<String>(),
            ShareIntentParser.extractUrls(listOf("URL はありません")),
        )
    }
}

