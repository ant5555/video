package com.example.video

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@RestController
@RequestMapping("/video")
class VideoController {

    private val uploadDir = System.getProperty("user.home") + "/Desktop/boostcamp/membership/program/video/uploads/videos"

    init {
        // 업로드 디렉토리 생성
        File(uploadDir).mkdirs()
        println("Upload directory: $uploadDir")
    }

    @GetMapping
    fun video(): String {
        return "OK"
    }

    @PostMapping("/upload")
    fun uploadVideo(@RequestParam("file") file: MultipartFile): ResponseEntity<Map<String, String>> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("error" to "File is empty"))
        }

        // 동영상 파일 확장자 검증
        val contentType = file.contentType
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid file type. Only video files are allowed"))
        }

        try {
            // 고유한 파일명 생성
            val originalFilename = file.originalFilename ?: "video"
            val fileExtension = originalFilename.substringAfterLast(".", "mp4")
            val uniqueFilename = "${UUID.randomUUID()}.$fileExtension"

            // 파일 저장
            val targetPath = Paths.get(uploadDir, uniqueFilename)
            Files.copy(file.inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)

            return ResponseEntity.ok(mapOf(
                "message" to "Video uploaded successfully",
                "filename" to uniqueFilename,
                "size" to file.size.toString()
            ))
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(mapOf("error" to "Upload failed: ${e.message}"))
        }
    }

    @GetMapping("/list")
    fun listVideos(): ResponseEntity<List<Map<String, String>>> {
        val videoFiles = File(uploadDir).listFiles()?.map { file ->
            mapOf(
                "filename" to file.name,
                "size" to file.length().toString(),
                "path" to file.absolutePath
            )
        } ?: emptyList()

        return ResponseEntity.ok(videoFiles)
    }
}