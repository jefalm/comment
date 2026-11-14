package com.example.commentanalyzer.network.glm

import com.example.commentanalyzer.network.OpenRouterAttributionConfig
import com.example.commentanalyzer.network.OpenRouterHeaderInterceptor
import com.example.commentanalyzer.network.dto.glm.GlmRequest
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface GlmApiService {
    @POST("api/v1/chat/completions")
    suspend fun createChatCompletion(@Body request: GlmRequest): Response<ResponseBody>

    companion object {
        fun create(
            baseUrl: String = "https://openrouter.ai/",
            tokenProvider: () -> String?,
            attributionConfig: OpenRouterAttributionConfig = OpenRouterAttributionConfig(),
            okHttpClient: OkHttpClient? = null
        ): GlmApiService {
            val client = (okHttpClient?.newBuilder() ?: OkHttpClient.Builder())
                .addInterceptor(OpenRouterHeaderInterceptor(tokenProvider, attributionConfig))
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GlmApiService::class.java)
        }
    }
}