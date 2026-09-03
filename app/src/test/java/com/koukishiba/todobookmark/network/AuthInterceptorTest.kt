package com.koukishiba.todobookmark.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Authorization ヘッダーに Bearer を付けずトークンをそのまま設定する`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { "dummy-id-token" })
            .build()

        client.newCall(Request.Builder().url(server.url("/bookmarks/batch")).build()).execute()

        val recorded = server.takeRequest()
        assertEquals("dummy-id-token", recorded.getHeader("Authorization"))
        assertFalse(recorded.getHeader("Authorization")!!.startsWith("Bearer"))
    }

    @Test
    fun `トークンが取得できない場合は Authorization ヘッダーを付けない`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { null })
            .build()

        client.newCall(Request.Builder().url(server.url("/bookmarks/batch")).build()).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }
}
