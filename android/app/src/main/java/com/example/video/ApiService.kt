package com.example.video

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("video/upload")
    suspend fun uploadVideo(
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    @GET("video/list")
    suspend fun getVideoList(): Response<List<VideoInfo>>
}

data class UploadResponse(
    val message: String,
    val filename: String,
    val size: String
)

data class VideoInfo(
    val filename: String,
    val size: String,
    val path: String
)
