package com.example.video

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/video")
class VideoController(
    private val objectStorageService: ObjectStorageService
) {
    private val logger = LoggerFactory.getLogger(VideoController::class.java)

    @PostMapping("/initiate-multipart")
    fun initiateMultipartUpload(
        @RequestParam("filename") filename: String,
        @RequestParam("contentType") contentType: String
    ): ResponseEntity<MultipartUploadResponse> {
        val startTime = System.currentTimeMillis()
        val response = objectStorageService.initiateMultipartUpload(filename, contentType)
        val duration = System.currentTimeMillis() - startTime
        logger.info("[MULTIPART-START] $filename | uploadId: ${response.uploadId} | ${duration}ms")
        return ResponseEntity.ok(response)
    }

    @PostMapping("/part-presigned-url")
    fun getPartPresignedUrl(
        @RequestParam("filename") filename: String,
        @RequestParam("uploadId") uploadId: String,
        @RequestParam("partNumber") partNumber: Int
    ): ResponseEntity<Map<String, String>> {
        val url = objectStorageService.generatePartPresignedUrl(filename, uploadId, partNumber)
        logger.info("[MULTIPART-PART] $filename | part: $partNumber")
        return ResponseEntity.ok(mapOf("url" to url))
    }

    @PostMapping("/complete-multipart")
    fun completeMultipartUpload(
        @RequestParam("filename") filename: String,
        @RequestParam("uploadId") uploadId: String,
        @RequestParam("clientUploadTimeMs") clientUploadTimeMs: Long,
        @RequestBody parts: List<CompletedPartInfo>
    ): ResponseEntity<Map<String, String>> {
        val startTime = System.currentTimeMillis()
        val completedFilename = objectStorageService.completeMultipartUpload(filename, uploadId, parts)
        val fileUrl = objectStorageService.getFileUrl(completedFilename)
        val duration = System.currentTimeMillis() - startTime

        val totalParts = parts.size
        val estimatedSize = totalParts * 5 // 5MB per part (approximate)
        val uploadSpeedMBps = if (clientUploadTimeMs > 0) {
            String.format("%.2f", (estimatedSize * 1000.0) / clientUploadTimeMs)
        } else "N/A"

        logger.info("[MULTIPART-COMPLETE] $filename | ${totalParts}parts (~${estimatedSize}MB) | 총 업로드: ${clientUploadTimeMs}ms (${clientUploadTimeMs/1000.0}초) | 속도: ${uploadSpeedMBps}MB/s | 완료처리: ${duration}ms")

        return ResponseEntity.ok(mapOf(
            "message" to "Upload completed successfully",
            "filename" to completedFilename,
            "url" to fileUrl
        ))
    }
}