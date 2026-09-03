package com.koukishiba.todobookmark.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface BookmarkApi {
    @POST("bookmarks/batch")
    suspend fun postBatch(@Body body: BatchRequestBody): Response<BatchResponseBody>
}
