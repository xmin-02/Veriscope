package com.example.veriscope.data

data class User(
    val id: Int? = null,
    val name: String,
    val email: String,
    val password: String? = null,
    val email_verified: Boolean? = null,
    val verification_required: Boolean? = null,
    val createdAt: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class SignUpRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val elapsed_seconds: Double? = null
)

data class NewsEvaluationResult(
    val reliability_score: Any,
    val reliability_level: String,
    val recommendation: String,
    val evidence: List<Evidence>? = null,
    val elapsed_seconds: Double? = null
)

data class Evidence(
    val number: Int? = null,
    val rank: Int? = null,
    val score: Any? = null,
    val url: String? = null,
    val similarity: Double? = null,
    val support: Double? = null
)

data class NewsEvaluationRequest(
    val url: String,
    val similarity_threshold: Double = 0.6,
    val use_gpu: Boolean = true,
    val fp16: Boolean = true,
    val nli_batch: Int = 128
)

data class ImageEvaluationRequest(
    val image_data: String,
    val similarity_threshold: Double = 0.5,
    val ocr_method: String = "easyocr",
    val use_gpu: Boolean = true,
    val fp16: Boolean = true,
    val nli_batch: Int = 32
)

data class ForgotPasswordRequest(
    val email: String
)

data class VerifyEmailRequest(
    val email: String,
    val verification_code: String
)

data class ResendVerificationRequest(
    val email: String
)

// 로컬 저장용 데이터 클래스
data class CheckHistoryLocal(
    val id: Int,
    val title: String,
    val type: String,
    val reliabilityScore: Float,
    val isReliable: Boolean,
    val checkedAt: String,
    val url: String?,
    val savedImagePath: String? = null, // 제보용 이미지 저장 경로
    val evidence: String? = null // 근거 자료 JSON 문자열
)