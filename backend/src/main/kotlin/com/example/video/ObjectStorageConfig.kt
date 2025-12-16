package com.example.video

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class ObjectStorageConfig(
    private val properties: ObjectStorageProperties
) {

    @Bean
    fun s3Client(): S3Client {
        val s3Config = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build()

        return S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        properties.accessKey,
                        properties.secretKey
                    )
                )
            )
            .serviceConfiguration(s3Config)
            .build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        return S3Presigner.builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        properties.accessKey,
                        properties.secretKey
                    )
                )
            )
            .build()
    }
}

@ConfigurationProperties(prefix = "ncp.object-storage")
data class ObjectStorageProperties(
    val endpoint: String = "",
    val region: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val bucketName: String = ""
)
