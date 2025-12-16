package com.example.video

import retrofit2.Response
import retrofit2.http.POST

interface ApiService {

    @POST("video/initiate-multipart")
    @retrofit2.http.FormUrlEncoded
    suspend fun initiateMultipartUpload(
        @retrofit2.http.Field("filename") filename: String,
        @retrofit2.http.Field("contentType") contentType: String
    ): Response<MultipartUploadResponse>

    @POST("video/part-presigned-url")
    @retrofit2.http.FormUrlEncoded
    suspend fun getPartPresignedUrl(
        @retrofit2.http.Field("filename") filename: String,
        @retrofit2.http.Field("uploadId") uploadId: String,
        @retrofit2.http.Field("partNumber") partNumber: Int
    ): Response<PartPresignedUrlResponse>

    @POST("video/complete-multipart")
    suspend fun completeMultipartUpload(
        @retrofit2.http.Query("filename") filename: String,
        @retrofit2.http.Query("uploadId") uploadId: String,
        @retrofit2.http.Body parts: List<CompletedPartInfo>,
        @retrofit2.http.Query("clientUploadTimeMs") clientUploadTimeMs: Long
    ): Response<CompleteUploadResponse>
}

data class MultipartUploadResponse(
    val uploadId: String,
    val filename: String,
    val key: String
)

data class PartPresignedUrlResponse(
    val url: String
)

data class CompletedPartInfo(
    val partNumber: Int,
    val eTag: String
)

data class CompleteUploadResponse(
    val message: String,
    val filename: String,
    val url: String
)
