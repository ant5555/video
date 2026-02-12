package com.example.video

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

class MultipartUploadManager(
    private val context: Context,
    private val apiService: ApiService,
    private val onProgressUpdate: (Int) -> Unit
) : VideoUploader {
    private val client = OkHttpClient()
    private val PART_SIZE = 5 * 1024 * 1024 // 5MB
    private val PARALLEL_UPLOAD_COUNT = 4
    private val MAX_RETRY_COUNT = 3
    private val RETRY_DELAY_MS = 1000L

    override suspend fun uploadVideo(videoUri: Uri): Result<String> = withContext(Dispatchers.IO) {
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

            // 3. 파일을 파트로 분할하여 병렬 업로드
            val inputStream = context.contentResolver.openInputStream(videoUri)
                ?: return@withContext Result.failure(Exception("파일 열기 실패"))

            val totalParts = (fileSize + PART_SIZE - 1) / PART_SIZE
            val channel = Channel<Pair<Int, ByteArray>>(PARALLEL_UPLOAD_COUNT)
            val completedParts = ConcurrentLinkedQueue<CompletedPartInfo>()
            val uploadedBytes = AtomicLong(0)

            coroutineScope {
                // Producer: 파트를 순차적으로 읽어 Channel에 전달
                launch {
                    inputStream.use { stream ->
                        var partNumber = 1
                        var bytesReadTotal = 0L
                        while (bytesReadTotal < fileSize) {
                            val partSize = minOf(PART_SIZE.toLong(), fileSize - bytesReadTotal).toInt()
                            val buffer = ByteArray(partSize)
                            var bytesRead = 0
                            while (bytesRead < partSize) {
                                val read = stream.read(buffer, bytesRead, partSize - bytesRead)
                                if (read == -1) break
                                bytesRead += read
                            }
                            if (bytesRead == 0) break
                            channel.send(partNumber to buffer.copyOf(bytesRead))
                            bytesReadTotal += bytesRead
                            partNumber++
                        }
                    }
                    channel.close()
                }

                // Consumer: N개의 코루틴이 병렬로 업로드
                repeat(PARALLEL_UPLOAD_COUNT) {
                    launch {
                        for ((partNumber, data) in channel) {
                            Log.d("MultipartUpload", "Part $partNumber/$totalParts 업로드 중 (${data.size} bytes)")

                            var lastException: Exception? = null
                            var eTag: String? = null

                            for (attempt in 1..MAX_RETRY_COUNT) {
                                try {
                                    val urlResponse = apiService.getPartPresignedUrl(uniqueFileName, uploadId, partNumber)
                                    if (!urlResponse.isSuccessful) throw Exception("Presigned URL 가져오기 실패 (Part $partNumber)")

                                    val presignedUrl = urlResponse.body()!!.url
                                    eTag = uploadPart(presignedUrl, data, data.size)
                                    if (eTag == null) throw Exception("Part $partNumber 업로드 실패")

                                    break
                                } catch (e: Exception) {
                                    lastException = e
                                    if (attempt < MAX_RETRY_COUNT) {
                                        val delayMs = RETRY_DELAY_MS * (1 shl (attempt - 1))
                                        Log.w("MultipartUpload", "Part $partNumber 재시도 $attempt/$MAX_RETRY_COUNT (${delayMs}ms 후)")
                                        delay(delayMs)
                                    }
                                }
                            }

                            if (eTag == null) throw (lastException ?: Exception("Part $partNumber 업로드 실패"))

                            completedParts.add(CompletedPartInfo(partNumber, eTag))

                            val uploaded = uploadedBytes.addAndGet(data.size.toLong())
                            onProgressUpdate((uploaded * 100 / fileSize).toInt())
                        }
                    }
                }
            }

            // partNumber 순으로 정렬
            val sortedParts = completedParts.sortedBy { it.partNumber }

            // 6. Multipart Upload 완료
            val totalUploadTime = System.currentTimeMillis() - uploadStartTime

            val completeResponse = apiService.completeMultipartUpload(
                uniqueFileName,
                uploadId,
                sortedParts,
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
