package com.koukishiba.todobookmark.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

fun interface IdTokenProvider {
    suspend fun currentIdToken(): String?
}

/** Cognito ID Token をそのまま `Authorization` ヘッダーへ設定する（`Bearer ` は付けない）。 */
class AuthInterceptor(private val tokenProvider: IdTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = runBlocking { tokenProvider.currentIdToken() }
        val authorizedRequest = if (token != null) {
            request.newBuilder().header("Authorization", token).build()
        } else {
            request
        }
        return chain.proceed(authorizedRequest)
    }
}
