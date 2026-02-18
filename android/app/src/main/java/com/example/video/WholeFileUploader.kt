package com.example.video

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class WholeFileUploader(
    private val context: Context,
    private val apiService: ApiService,
    private val onProgressUpdate: (Int) -> Unit
) : VideoUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS)
        .build()

    override suspend fun uploadVideo(videoUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uploadStartTime = System.currentTimeMillis()

            // 1. 파일 정보
            val (fileName, fileSize, contentType) = getFileInfo(videoUri)
            val uniqueFileName = "${UUID.randomUUID()}.${fileName.substringAfterLast(".", "mp4")}"
            Log.d("WholeFileUpload", "파일: $uniqueFileName, 크기: $fileSize bytes (${fileSize / 1024 / 1024}MB)")

            // 2. Presigned URL 발급
            val presignedResponse = apiService.getPresignedUrl(uniqueFileName, contentType)
            if (!presignedResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Presigned URL 발급 실패"))
            }
            val presignedData = presignedResponse.body()!!

            // 3. 파일 전체를 메모리에 로드
            onProgressUpdate(0)
            Log.d("WholeFileUpload", "파일 전체 메모리 로딩 시작")

            val fileBytes = context.contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("파일 열기 실패"))

            Log.d("WholeFileUpload", "메모리 로딩 완료: ${fileBytes.size} bytes")
            onProgressUpdate(10)

            // 4. 한번에 업로드
            val requestBody = fileBytes.toRequestBody(contentType.toMediaTypeOrNull())
            val request = Request.Builder()
                .url(presignedData.url)
                .put(requestBody)
                .build()

            onProgressUpdate(20)
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("업로드 실패: ${response.code}"))
            }

            val totalTime = System.currentTimeMillis() - uploadStartTime
            onProgressUpdate(100)
            Log.d("WholeFileUpload", "업로드 완료: ${presignedData.fileUrl} (총 소요시간: ${totalTime}ms = ${totalTime / 1000.0}초)")

            Result.success(presignedData.fileUrl)
        } catch (e: Exception) {
            Log.e("WholeFileUpload", "업로드 오류", e)
            Result.failure(e)
        }
    }

    private fun getFileInfo(uri: Uri): Triple<String, Long, String> {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "video.mp4"
                val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                Triple(name, size, "video/mp4")
            } else {
                Triple("video.mp4", 0L, "video/mp4")
            }
        } ?: Triple("video.mp4", 0L, "video/mp4")
    }
}
