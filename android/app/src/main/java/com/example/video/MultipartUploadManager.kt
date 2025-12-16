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
import java.io.InputStream
import java.util.UUID

class MultipartUploadManager(
    private val context: Context,
    private val apiService: ApiService,
    private val onProgressUpdate: (Int) -> Unit
) {
    private val client = OkHttpClient()
    private val PART_SIZE = 5 * 1024 * 1024 // 5MB

    suspend fun uploadVideo(videoUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uploadStartTime = System.currentTimeMillis()

            // 1. 파일 정보 가져오기
            val (fileName, fileSize, contentType) = getFileInfo(videoUri)
            val uniqueFileName = "${UUID.randomUUID()}.${fileName.substringAfterLast(".", "mp4")}"

            Log.d("MultipartUpload", "파일: $uniqueFileName, 크기: $fileSize bytes")

            // 2. Multipart Upload 시작
            val initiateResponse = apiService.initiateMultipartUpload(uniqueFileName, contentType)
            if (!initiateResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Multipart 시작 실패"))
            }

            val uploadData = initiateResponse.body()!!
            val uploadId = uploadData.uploadId
            Log.d("MultipartUpload", "Upload ID: $uploadId")

            // 3. 파일을 파트로 분할하여 업로드
            val inputStream = context.contentResolver.openInputStream(videoUri)
                ?: return@withContext Result.failure(Exception("파일 열기 실패"))

            val totalParts = (fileSize + PART_SIZE - 1) / PART_SIZE
            val completedParts = mutableListOf<CompletedPartInfo>()

            inputStream.use { stream ->
                var partNumber = 1
                var uploadedBytes = 0L

                while (uploadedBytes < fileSize) {
                    val partSize = minOf(PART_SIZE.toLong(), fileSize - uploadedBytes).toInt()
                    val buffer = ByteArray(partSize)
                    var bytesRead = 0

                    // 버퍼 채우기
                    while (bytesRead < partSize) {
                        val read = stream.read(buffer, bytesRead, partSize - bytesRead)
                        if (read == -1) break
                        bytesRead += read
                    }

                    if (bytesRead == 0) break

                    Log.d("MultipartUpload", "Part $partNumber/$totalParts 업로드 중 (${bytesRead} bytes)")

                    // 4. Part Presigned URL 가져오기
                    val urlResponse = apiService.getPartPresignedUrl(
                        uniqueFileName,
                        uploadId,
                        partNumber
                    )

                    if (!urlResponse.isSuccessful) {
                        return@withContext Result.failure(Exception("Presigned URL 가져오기 실패"))
                    }

                    val presignedUrl = urlResponse.body()!!.url

                    // 5. Presigned URL로 직접 업로드
                    val eTag = uploadPart(presignedUrl, buffer, bytesRead)
                        ?: return@withContext Result.failure(Exception("Part $partNumber 업로드 실패"))

                    completedParts.add(CompletedPartInfo(partNumber, eTag))

                    uploadedBytes += bytesRead
                    partNumber++

                    // 진행률 업데이트
                    val progress = (uploadedBytes * 100 / fileSize).toInt()
                    onProgressUpdate(progress)
                }
            }

            // 6. Multipart Upload 완료
            val totalUploadTime = System.currentTimeMillis() - uploadStartTime

            val completeResponse = apiService.completeMultipartUpload(
                uniqueFileName,
                uploadId,
                completedParts,
                totalUploadTime
            )

            if (!completeResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Multipart 완료 실패"))
            }

            val result = completeResponse.body()!!
            Log.d("MultipartUpload", "업로드 완료: ${result.url} (총 소요시간: ${totalUploadTime}ms = ${totalUploadTime/1000.0}초)")

            Result.success(result.url)
        } catch (e: Exception) {
            Log.e("MultipartUpload", "업로드 오류", e)
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

    private fun uploadPart(presignedUrl: String, data: ByteArray, size: Int): String? {
        return try {
            val requestBody = data.copyOf(size).toRequestBody("application/octet-stream".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(presignedUrl)
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.header("ETag")?.trim('"')
            } else {
                Log.e("MultipartUpload", "Part 업로드 실패: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("MultipartUpload", "Part 업로드 오류", e)
            null
        }
    }
}
