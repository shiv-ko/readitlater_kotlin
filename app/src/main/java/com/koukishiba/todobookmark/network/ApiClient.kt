package com.koukishiba.todobookmark.network

import com.koukishiba.todobookmark.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    // encodeDefaults=true: status/source を常に明示送信する（docs/backend-requirements.md 推奨）。
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun create(tokenProvider: IdTokenProvider): BookmarkApi {
        // BASIC はメソッド/パス/レスポンスコードのみを記録し、Authorizationヘッダーやリクエストボディ
        // （共有URL）はログに出さない。トークン・URLを通常ログへ出力しない要件を満たす。
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(logging)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        val mediaType = "application/json".toMediaType()
        val converterFactory = try {
            // Try to load the jakewharton converter factory using reflection
            val clazz = Class.forName("com.jakewharton.retrofit2.kotlinx.serialization.KotlinSerializationConverterFactory")
            val method = clazz.getMethod("create", Json::class.java, okhttp3.MediaType::class.java)
            method.invoke(null, json, mediaType) as retrofit2.Converter.Factory
        } catch (e: Exception) {
            // Fallback: create with just the Json object
            val clazz = Class.forName("com.jakewharton.retrofit2.kotlinx.serialization.KotlinSerializationConverterFactory")
            val constructor = clazz.getConstructor(Json::class.java)
            constructor.newInstance(json) as retrofit2.Converter.Factory
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("${BuildConfig.API_BASE_URL}/")
            .client(okHttpClient)
            .addConverterFactory(converterFactory)
            .build()

        return retrofit.create(BookmarkApi::class.java)
    }
}
