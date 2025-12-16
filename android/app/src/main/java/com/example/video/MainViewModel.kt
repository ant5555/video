package com.example.video

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream

class MainViewModel : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun uploadVideo(context: Context, videoUri: Uri) {
        viewModelScope.launch {
            try {
                _uploadState.value = UploadState.Uploading(0)

                // Uri에서 파일로 변환
                val file = uriToFile(context, videoUri)

                // 진행률을 추적하는 RequestBody 생성
                val requestFile = ProgressRequestBody(
                    file,
                    "video/*".toMediaTypeOrNull()
                ) { progress ->
                    _uploadState.value = UploadState.Uploading(progress)
                }

                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                // API 호출
                val response = NetworkModule
                    .retrofit
                    .create(ApiService::class.java)
                    .uploadVideo(body)

                if (response.isSuccessful) {
                    val uploadResponse = response.body()
                    _uploadState.value = UploadState.Success(
                        uploadResponse?.message ?: "Upload successful",
                        uploadResponse?.filename ?: ""
                    )
                    Log.d("Upload", "Success: ${uploadResponse?.filename}")
                } else {
                    _uploadState.value = UploadState.Error("Upload failed: ${response.code()}")
                    Log.e("Upload", "Failed: ${response.code()}")
                }

                // 임시 파일 삭제
                file.delete()
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error("Upload error: ${e.message}")
                Log.e("Upload", "Error", e)
            }
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("upload_", ".mp4", context.cacheDir)
        tempFile.outputStream().use { output ->
            inputStream?.copyTo(output)
        }
        return tempFile
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }
}

class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgressUpdate: (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val fileLength = file.length()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val inputStream = FileInputStream(file)
        var uploaded = 0L

        inputStream.use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                uploaded += read
                sink.write(buffer, 0, read)

                // 진행률 계산 (0-100)
                val progress = (100 * uploaded / fileLength).toInt()
                onProgressUpdate(progress)
            }
        }
    }
}

sealed class UploadState {
    object Idle : UploadState()
    data class Uploading(val progress: Int) : UploadState()
    data class Success(val message: String, val filename: String) : UploadState()
    data class Error(val message: String) : UploadState()
}