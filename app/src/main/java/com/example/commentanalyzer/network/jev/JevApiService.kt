package com.example.commentanalyzer.network.jev

import com.example.commentanalyzer.network.OpenRouterAttributionConfig
import com.example.commentanalyzer.network.OpenRouterHeaderInterceptor
import com.example.commentanalyzer.network.dto.jev.JevRequest
import com.example.commentanalyzer.network.dto.jev.JevDecisionResponse
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface JevApiService {
    @POST("api/alpha/decisions")
    suspend fun getDecisions(
        @Body request: JevRequest
    ): Response<JevDecisionResponse>

    companion object {
        fun create(
            baseUrl: String = "https://openrouter.ai/",
            tokenProvider: () -> String?,
            attributionConfig: OpenRouterAttributionConfig = OpenRouterAttributionConfig(),
            okHttpClient: OkHttpClient? = null
        ): JevApiService {
            val client = (okHttpClient?.newBuilder() ?: OkHttpClient.Builder())
                .addInterceptor(OpenRouterHeaderInterceptor(tokenProvider, attributionConfig))
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(JevApiService::class.java)
        }
    }
}
