package com.example.commentanalyzer.network.youtube

import com.example.commentanalyzer.network.dto.youtube.CommentListResponse
import com.example.commentanalyzer.network.dto.youtube.CommentThreadListResponse
import com.example.commentanalyzer.network.dto.youtube.VideoListResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {
    @GET("youtube/v3/videos")
    suspend fun videos(
        @Query("part") part: String = "snippet,statistics",
        @Query("id") videoId: String,
        @Query("key") apiKey: String
    ): Response<VideoListResponse>

    @GET("youtube/v3/commentThreads")
    suspend fun commentThreads(
        @Query("part") part: String = "snippet,replies",
        @Query("videoId") videoId: String,
        @Query("maxResults") maxResults: Int = 100,
        @Query("textFormat") textFormat: String = "plainText",
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String
    ): Response<CommentThreadListResponse>

    @GET("youtube/v3/comments")
    suspend fun comments(
        @Query("part") part: String = "snippet",
        @Query("parentId") parentId: String,
        @Query("maxResults") maxResults: Int = 100,
        @Query("textFormat") textFormat: String = "plainText",
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String
    ): Response<CommentListResponse>

    companion object {
        fun create(baseUrl: String = "https://www.googleapis.com/"): YouTubeApiService =
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YouTubeApiService::class.java)
    }
}
