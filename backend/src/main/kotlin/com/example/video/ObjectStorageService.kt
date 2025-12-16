package com.example.video

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@Service
class ObjectStorageService(
    private val s3Client: S3Client,
    private val properties: ObjectStorageProperties
) {

    fun uploadFile(file: MultipartFile): String {
        val originalFilename = file.originalFilename ?: "video"
        val fileExtension = originalFilename.substringAfterLast(".", "mp4")
        val uniqueFilename = "${UUID.randomUUID()}.$fileExtension"

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(properties.bucketName)
            .key("videos/$uniqueFilename")
            .contentType(file.contentType)
            .contentLength(file.size)
            .build()

        s3Client.putObject(
            putObjectRequest,
            RequestBody.fromInputStream(file.inputStream, file.size)
        )

        return uniqueFilename
    }

    fun getFileUrl(filename: String): String {
        return "${properties.endpoint}/${properties.bucketName}/videos/$filename"
    }

    fun deleteFile(filename: String) {
        s3Client.deleteObject { builder ->
            builder.bucket(properties.bucketName)
                .key("videos/$filename")
        }
    }
}
