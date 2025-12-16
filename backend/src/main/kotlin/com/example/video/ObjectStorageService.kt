package com.example.video

import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration

@Service
class ObjectStorageService(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: ObjectStorageProperties
) {

    fun initiateMultipartUpload(filename: String, contentType: String): MultipartUploadResponse {
        val objectKey = "videos/$filename"

        val request = CreateMultipartUploadRequest.builder()
            .bucket(properties.bucketName)
            .key(objectKey)
            .contentType(contentType)
            .build()

        val response = s3Client.createMultipartUpload(request)

        return MultipartUploadResponse(
            uploadId = response.uploadId(),
            filename = filename,
            key = objectKey
        )
    }

    fun generatePartPresignedUrl(
        filename: String,
        uploadId: String,
        partNumber: Int
    ): String {
        val objectKey = "videos/$filename"

        val uploadPartRequest = UploadPartRequest.builder()
            .bucket(properties.bucketName)
            .key(objectKey)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .build()

        val presignRequest = software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(30))
            .uploadPartRequest(uploadPartRequest)
            .build()

        return s3Presigner.presignUploadPart(presignRequest).url().toString()
    }

    fun completeMultipartUpload(
        filename: String,
        uploadId: String,
        parts: List<CompletedPartInfo>
    ): String {
        val objectKey = "videos/$filename"

        val completedParts = parts.map { part ->
            CompletedPart.builder()
                .partNumber(part.partNumber)
                .eTag(part.eTag)
                .build()
        }

        val completedMultipartUpload = CompletedMultipartUpload.builder()
            .parts(completedParts)
            .build()

        val request = CompleteMultipartUploadRequest.builder()
            .bucket(properties.bucketName)
            .key(objectKey)
            .uploadId(uploadId)
            .multipartUpload(completedMultipartUpload)
            .build()

        s3Client.completeMultipartUpload(request)

        return filename
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

data class MultipartUploadResponse(
    val uploadId: String,
    val filename: String,
    val key: String
)

data class CompletedPartInfo(
    val partNumber: Int,
    val eTag: String
)
