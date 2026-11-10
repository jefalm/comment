package com.example.commentanalyzer.network

import okhttp3.Interceptor
import okhttp3.Response

class OpenRouterHeaderInterceptor(
    private val tokenProvider: () -> String?,
    private val attributionConfig: OpenRouterAttributionConfig = OpenRouterAttributionConfig()
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("X-Title", attributionConfig.title)
            .header("Content-Type", "application/json")

        tokenProvider()?.let { token ->
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        attributionConfig.getValidatedReferer()?.let { referer ->
            builder.header("HTTP-Referer", referer)
        }

        return chain.proceed(builder.build())
    }
}
