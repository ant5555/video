package com.example.video

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import kotlin.system.measureTimeMillis

@RestController
@RequestMapping("/video")
class VideoController(
    private val objectStorageService: ObjectStorageService
) {
    private val logger = LoggerFactory.getLogger(VideoController::class.java)

    @GetMapping
    fun video(): String {
        return "OK"
    }

    @PostMapping("/upload")
    fun uploadVideo(@RequestParam("file") file: MultipartFile): ResponseEntity<Map<String, String>> {
        val totalStartTime = System.currentTimeMillis()
        logger.info("========== 업로드 요청 시작 ==========")
        logger.info("파일명: ${file.originalFilename}")
        logger.info("파일 크기: ${file.size / 1024 / 1024}MB (${file.size} bytes)")
        logger.info("Content-Type: ${file.contentType}")

        try {
            var filename: String
            val uploadTime = measureTimeMillis {
                filename = objectStorageService.uploadFile(file)
            }
            logger.info("NCP Object Storage 업로드 시간: ${uploadTime}ms (${uploadTime / 1000.0}초)")

            val fileUrl = objectStorageService.getFileUrl(filename)

            val totalTime = System.currentTimeMillis() - totalStartTime
            logger.info("전체 처리 시간: ${totalTime}ms (${totalTime / 1000.0}초)")
            logger.info("업로드 속도: ${(file.size / 1024.0 / 1024.0) / (totalTime / 1000.0)} MB/s")
            logger.info("========== 업로드 완료 ==========")

            return ResponseEntity.ok(mapOf(
                "message" to "Video uploaded successfully to NCP Object Storage",
                "filename" to filename,
                "url" to fileUrl,
                "size" to file.size.toString(),
                "uploadTimeMs" to uploadTime.toString(),
                "totalTimeMs" to totalTime.toString()
            ))
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - totalStartTime
            logger.error("업로드 실패 (${totalTime}ms): ${e.message}", e)
            return ResponseEntity.internalServerError().body(mapOf("error" to "Upload failed: ${e.message}"))
        }
    }

    @GetMapping("/list")
    fun listVideos(): ResponseEntity<List<Map<String, String>>> {
        return ResponseEntity.ok(emptyList())
    }
}