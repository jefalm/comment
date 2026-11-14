package com.example.commentanalyzer.network.jev

import com.example.commentanalyzer.network.TypeSafeHeaderInterceptor
import com.example.commentanalyzer.network.dto.jev.JevDecisionResponse
import com.example.commentanalyzer.network.dto.jev.JevRequest
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface JevApiService {
    @POST("v1/systemone")
    suspend fun getDecisions(
        @Body request: JevRequest
    ): Response<JevDecisionResponse>

    companion object {
        fun create(
            baseUrl: String = "https://api.typesafe.ai/",
            tokenProvider: () -> String?,
            okHttpClient: OkHttpClient? = null
        ): JevApiService {
            val client = (okHttpClient?.newBuilder() ?: OkHttpClient.Builder())
                .addInterceptor(TypeSafeHeaderInterceptor(tokenProvider))
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
