package com.example.veriscope.data

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<User>>
    
    @POST("auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<ApiResponse<User>>
    
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiResponse<String>>
    
    @POST("auth/verify-email")
    suspend fun verifyEmail(@Body request: VerifyEmailRequest): Response<ApiResponse<User>>
    
    @POST("auth/resend-verification")
    suspend fun resendVerification(@Body request: ResendVerificationRequest): Response<ApiResponse<String>>
    
    @POST("auth/find-email")
    suspend fun findEmail(@Body request: Map<String, String>): Response<ApiResponse<Map<String, String>>>
    
    @GET("users")
    suspend fun getUsers(): Response<ApiResponse<List<User>>>
    
    @GET("health")
    suspend fun healthCheck(): Response<ApiResponse<String>>
    
    // 뉴스 평가 API
    @POST("evaluate")
    suspend fun evaluateNews(@Body request: NewsEvaluationRequest): Response<ApiResponse<NewsEvaluationResult>>
    
    @POST("evaluate-image")
    suspend fun evaluateImage(@Body request: ImageEvaluationRequest): Response<ApiResponse<NewsEvaluationResult>>
    
    // 마이페이지 API
    @GET("user/history")
    suspend fun getUserHistory(@Query("user_id") userId: String? = null): Response<HistoryResponse>
    
    @GET("user/profile")
    suspend fun getUserProfile(@Query("user_id") userId: String? = null): Response<ProfileResponse>
}