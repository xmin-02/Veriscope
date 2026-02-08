package com.example.veriscope.data

data class HistoryResponse(
    val success: Boolean,
    val history: List<CheckHistory>,
    val total: Int
)

data class CheckHistory(
    val id: Int,
    val title: String,
    val url: String?,
    val reliabilityScore: Float,
    val isReliable: Boolean,
    val checkedAt: String,
    val type: String, // "URL" 또는 "IMAGE"
    val savedImagePath: String? = null, // 제보용 이미지 저장 경로
    val evidence: String? = null // 근거 자료 JSON 문자열
)

data class ProfileResponse(
    val success: Boolean,
    val profile: UserProfile
)

data class UserProfile(
    val id: Int,
    val name: String,
    val email: String,
    val joinDate: String,
    val totalChecks: Int,
    val reliableCount: Int,
    val unreliableCount: Int
)