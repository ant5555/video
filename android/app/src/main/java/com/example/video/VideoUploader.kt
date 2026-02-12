package com.example.video

import android.net.Uri

interface VideoUploader {
    suspend fun uploadVideo(videoUri: Uri): Result<String>
}
