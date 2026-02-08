package com.example.veriscope.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object ApiClient {
    
    // Veriscope 통합 API 서버 URL (ngrok 터널 - 포트 5004)
    // 주 서버가 작동하지 않을 경우 대체 URL 사용
    private const val BASE_URL = "https://00bca2c0b970.ngrok-free.app/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
    
    fun getInstance(): ApiClient = this
    
    suspend fun findEmail(request: Map<String, String>): ApiResponse<Map<String, String>> {
        android.util.Log.d("ApiClient", "findEmail 시작 - request: $request")
        try {
            val response = apiService.findEmail(request)
            android.util.Log.d("ApiClient", "HTTP 응답 코드: ${response.code()}")
            android.util.Log.d("ApiClient", "HTTP 응답 성공 여부: ${response.isSuccessful}")
            
            return if (response.isSuccessful) {
                val body = response.body()
                android.util.Log.d("ApiClient", "응답 본문: $body")
                body ?: ApiResponse(false, "응답 데이터가 없습니다", null)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("ApiClient", "오류 응답: $errorBody")
                ApiResponse(false, "서버 오류: ${response.code()}", null)
            }
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "API 호출 예외: ${e.message}", e)
            return ApiResponse(false, "네트워크 오류: ${e.message}", null)
        }
    }
}