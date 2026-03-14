  # Video Upload Prototype                                                                                                                       
                                                                                                                                                 
  Android에서 영상을 업로드하고 NCP Object Storage에 저장하는 프로토타입입니다.                                                                  
                                                                                                                                                 
  ## 구조
  - android/   - 영상 선택 및 업로드 클라이언트                                                                                                    
  - backend/   - 업로드 처리 및 스토리지 연동 서버

  ## 기능
  - 영상 파일 단건 업로드 (WholeFileUploader)
  - 대용량 영상 멀티파트 업로드 (MultipartUploadManager)
  - NCP Object Storage (S3 호환) 저장

  ## 기술 스택

  **Android**
  - Kotlin, Jetpack Compose
  - Retrofit

  **Backend**
  - Kotlin, Spring Boot 4.0.0
  - Spring Data JPA, MySQL
  - AWS SDK (NCP Object Storage)

  ## 목적
  업로드 방식(단건 vs 멀티파트)을 직접 구현하며 비교해보는 프로토타입입니다.
