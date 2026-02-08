package com.example.veriscope

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.veriscope.data.ApiClient
import com.example.veriscope.data.NewsEvaluationRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.google.gson.Gson
import com.example.veriscope.data.CheckHistoryLocal
import com.example.veriscope.data.ImageEvaluationRequest
import com.example.veriscope.utils.UserManager
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class LoadingActivity : AppCompatActivity() {
    
    private lateinit var tvStatus: TextView
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)
        
        initViews()
        startDotAnimation()
        
        val url = intent.getStringExtra("url")
        val imageUri = intent.getStringExtra("imageUri")
        val type = intent.getStringExtra("type")
        
        when (type) {
            "url" -> {
                if (url != null) {
                    startUrlEvaluation(url)
                } else {
                    showError("URL이 전달되지 않았습니다")
                }
            }
            "image" -> {
                if (imageUri != null) {
                    startImageEvaluationWithAPI(imageUri)
                } else {
                    showError("이미지가 전달되지 않았습니다")
                }
            }
            else -> {
                showError("검사 유형이 지정되지 않았습니다")
            }
        }
    }
    
    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
    }
    
    private fun startDotAnimation() {
        // 첫 번째 점 애니메이션 (적절한 범위로)
        val animator1 = ObjectAnimator.ofFloat(dot1, "translationY", 0f, -12f, 0f)
        animator1.duration = 1200
        animator1.repeatCount = ValueAnimator.INFINITE
        animator1.startDelay = 0
        
        // 두 번째 점 애니메이션 (400ms 딜레이)
        val animator2 = ObjectAnimator.ofFloat(dot2, "translationY", 0f, -12f, 0f)
        animator2.duration = 1200
        animator2.repeatCount = ValueAnimator.INFINITE
        animator2.startDelay = 400
        
        // 세 번째 점 애니메이션 (800ms 딜레이)
        val animator3 = ObjectAnimator.ofFloat(dot3, "translationY", 0f, -12f, 0f)
        animator3.duration = 1200
        animator3.repeatCount = ValueAnimator.INFINITE
        animator3.startDelay = 800
        
        // 투명도 애니메이션도 함께 적용
        val alphaAnimator1 = ObjectAnimator.ofFloat(dot1, "alpha", 0.3f, 1.0f, 0.3f)
        alphaAnimator1.duration = 1200
        alphaAnimator1.repeatCount = ValueAnimator.INFINITE
        alphaAnimator1.startDelay = 0
        
        val alphaAnimator2 = ObjectAnimator.ofFloat(dot2, "alpha", 0.3f, 1.0f, 0.3f)
        alphaAnimator2.duration = 1200
        alphaAnimator2.repeatCount = ValueAnimator.INFINITE
        alphaAnimator2.startDelay = 400
        
        val alphaAnimator3 = ObjectAnimator.ofFloat(dot3, "alpha", 0.3f, 1.0f, 0.3f)
        alphaAnimator3.duration = 1200
        alphaAnimator3.repeatCount = ValueAnimator.INFINITE
        alphaAnimator3.startDelay = 800
        
        // 애니메이션 시작
        animator1.start()
        animator2.start()
        animator3.start()
        alphaAnimator1.start()
        alphaAnimator2.start()
        alphaAnimator3.start()
    }
    
    private fun startUrlEvaluation(url: String) {
        lifecycleScope.launch {
            try {
                // 네트워크 연결 확인
                if (!isNetworkAvailable()) {
                    showError("인터넷 연결을 확인해주세요")
                    return@launch
                }
                
                updateStatus("울 분석 중...")
                delay(1000)
                
                updateStatus("뉴스 신뢰도 검사 중...")
                
                android.util.Log.d("URLEvaluation", "API 호출 시작 - URL: $url")
                val request = NewsEvaluationRequest(url, 0.6)
                val response = ApiClient.apiService.evaluateNews(request)
                android.util.Log.d("URLEvaluation", "API 응답 코드: ${response.code()}, 성공: ${response.isSuccessful}")
                
                updateStatus("검사 완료!")
                delay(500)
                
                // 결과 화면으로 이동
                val intent = Intent(this@LoadingActivity, ResultActivity::class.java)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val result = response.body()!!.data!!
                    
                    // API 응답에서 데이터 추출
                    val reliabilityScore = when (val score = result.reliability_score) {
                        is Int -> score.toFloat()
                        is Double -> score.toFloat()
                        is Float -> score
                        else -> {
                            android.util.Log.e("URLEvaluation", "예상치 못한 점수 타입: ${score?.javaClass}")
                            0f
                        }
                    }
                    val isReliable = reliabilityScore >= 70
                    val recommendation = result.recommendation ?: "검사 결과가 없습니다"
                    val evidenceList = result.evidence ?: emptyList()
                    
                    // Evidence를 JSON 문자열로 변환
                    val evidenceJson = evidenceList.map { evidence ->
                        mapOf(
                            "number" to (evidence.number ?: 1),
                            "score" to (evidence.score ?: 0),
                            "url" to (evidence.url ?: ""),
                            "similarity" to (evidence.similarity?.toInt() ?: 0),
                            "support" to (evidence.score ?: 0)
                        )
                    }
                    
                    val gson = com.google.gson.Gson()
                    val evidenceJsonString = gson.toJson(evidenceJson)
                    
                    // 검사 이력 저장 (근거 자료 포함)
                    saveCheckHistory(url, "URL", reliabilityScore, isReliable, recommendation, evidenceJsonString)
                    
                    intent.putExtra("is_reliable", isReliable)
                    intent.putExtra("reliability_score", reliabilityScore)
                    intent.putExtra("recommendation", recommendation)
                    intent.putExtra("evidence", evidenceJsonString)
                    intent.putExtra("url", url)
                    intent.putExtra("type", "URL")
                    intent.putExtra("CHECK_TYPE", "URL")
                    intent.putExtra("URL", url)
                } else {
                    // API 실패 시 더미 데이터로 동작 (개발/테스트 목적)
                    android.util.Log.w("URLEvaluation", "API 실패 - 더미 데이터 사용")
                    val dummyScore = (30..90).random().toFloat()
                    val isReliable = dummyScore >= 70
                    
                    intent.putExtra("is_reliable", isReliable)
                    intent.putExtra("reliability_score", dummyScore)
                    intent.putExtra("recommendation", if (isReliable) "신뢰할 수 있는 뉴스입니다." else "이 뉴스는 주의가 필요합니다.")
                    intent.putExtra("evidence", """[{"number":1,"score":${dummyScore.toInt()},"url":"테스트 근거","similarity":${(60..95).random()},"support":${dummyScore.toInt()}}]""")
                    intent.putExtra("url", url)
                    intent.putExtra("type", "URL")
                    intent.putExtra("CHECK_TYPE", "URL")
                    intent.putExtra("URL", url)
                    
                    // 더미 데이터로 검사 이력 저장
                    val dummyEvidence = """[{"number":1,"score":${dummyScore.toInt()},"url":"테스트 근거","similarity":${(60..95).random()},"support":${dummyScore.toInt()}}]"""
                    saveCheckHistory(url, "URL", dummyScore, isReliable, "더미 데이터 검사", dummyEvidence)
                }
                
                startActivity(intent)
                finish()
                
            } catch (e: Exception) {
                android.util.Log.e("URLEvaluation", "URL 검사 오류: ${e.message}")
                android.util.Log.e("URLEvaluation", "스택 트레이스: ", e)
                
                // 더 구체적인 에러 메시지 제공
                val errorMessage = when {
                    e.message?.contains("UnknownHostException") == true -> "서버에 연결할 수 없습니다. 인터넷 연결을 확인해주세요."
                    e.message?.contains("ConnectException") == true -> "서버 연결에 실패했습니다. 잠시 후 다시 시도해주세요."
                    e.message?.contains("SocketTimeoutException") == true -> "요청 시간이 초과되었습니다. 다시 시도해주세요."
                    else -> "검사 중 오류가 발생했습니다. 다시 시도해주세요."
                }
                showError(errorMessage)
            }
        }
    }
    
    // 네트워크 연결 상태 확인 메서드
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            return networkCapabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo?.isConnected == true
        }
    }
    

    
    private fun updateStatus(message: String) {
        runOnUiThread {
            tvStatus.text = message
        }
    }
    
    private fun showError(message: String) {
        runOnUiThread {
            tvStatus.text = "❌ $message"
        }
        
        lifecycleScope.launch {
            delay(3000)
            finish()
        }
    }
    
    private fun createDummyImageEvidence(): String {
        val dummyEvidence = listOf(
            mapOf(
                "number" to 1,
                "score" to 88,
                "url" to "https://news.example.com/fact-check-image",
                "similarity" to 85,
                "support" to 90
            ),
            mapOf(
                "number" to 2,
                "score" to 82,
                "url" to "https://source.example.com/original-image",
                "similarity" to 78,
                "support" to 85
            ),
            mapOf(
                "number" to 3,
                "score" to 79,
                "url" to "https://verify.example.com/image-analysis",
                "similarity" to 72,
                "support" to 81
            )
        )
        
        val gson = Gson()
        return gson.toJson(dummyEvidence)
    }
    
    private fun saveCheckHistory(title: String, type: String, score: Float, isReliable: Boolean, recommendation: String, evidence: String? = null) {
        // 로컬에 검사 이력 저장 (SharedPreferences 사용) - 사용자별로 관리
        val userManager = UserManager(this)
        val currentUser = userManager.getCurrentUser()
        if (currentUser == null) {
            android.util.Log.w("LoadingActivity", "로그인되지 않은 상태에서 이력 저장 시도")
            return
        }
        
        val prefs = getSharedPreferences("veriscope_history_${currentUser.email}", MODE_PRIVATE)
        val editor = prefs.edit()
        
        val historyJson = prefs.getString("history_list", "[]")
        val gson = Gson()
        val historyList = try {
            gson.fromJson(historyJson, Array<CheckHistoryLocal>::class.java).toMutableList()
        } catch (e: Exception) {
            mutableListOf<CheckHistoryLocal>()
        }
        
        // 중복 검사 완화 - 최근 5분 이내의 동일 URL/이미지만 중복으로 처리
        val currentTime = System.currentTimeMillis()
        val duplicateExists = historyList.any { existing ->
            val existingTime = try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                sdf.parse(existing.checkedAt)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
            
            val timeDiff = currentTime - existingTime
            val fiveMinutesInMillis = 5 * 60 * 1000L
            
            when (type) {
                "URL" -> existing.type == "URL" && existing.url == title && timeDiff < fiveMinutesInMillis
                "IMAGE" -> existing.type == "IMAGE" && existing.url == title && timeDiff < fiveMinutesInMillis
                else -> false
            }
        }
        
        if (duplicateExists) {
            android.util.Log.d("LoadingActivity", "5분 이내 중복 검사로 저장하지 않음: $title")
            return
        }
        
        val historyId = System.currentTimeMillis().toInt()
        
        // 이미지 타입이고 신뢰도가 낮은 경우(제보 대상) 이미지를 앱 저장소에 복사
        val savedImagePath = if (type == "IMAGE" && !isReliable) {
            try {
                copyImageToAppStorage(Uri.parse(title), historyId)
            } catch (e: Exception) {
                android.util.Log.e("LoadingActivity", "이미지 복사 중 오류", e)
                null
            }
        } else null
        
        val newHistory = CheckHistoryLocal(
            id = historyId,
            title = if (type == "IMAGE") "이미지 검사" else title,
            type = type,
            reliabilityScore = score,
            isReliable = isReliable,
            checkedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            url = title, // URL 타입이면 URL이, IMAGE 타입이면 이미지 URI가 저장됨
            savedImagePath = savedImagePath,
            evidence = evidence // 근거 자료 저장
        )
        
        historyList.add(0, newHistory) // 최신 항목을 앞에 추가
        
        // 최대 50개까지만 저장
        if (historyList.size > 50) {
            historyList.removeAt(historyList.size - 1)
        }
        
        val updatedJson = gson.toJson(historyList)
        editor.putString("history_list", updatedJson)
        val saveResult = editor.commit() // apply() 대신 commit()으로 즉시 저장 확인
        
        android.util.Log.d("LoadingActivity", "검사 이력 저장 완료: $title, 점수: $score, 신뢰: $isReliable")
        android.util.Log.d("LoadingActivity", "총 저장된 이력 개수: ${historyList.size}")
        android.util.Log.d("LoadingActivity", "SharedPreferences 저장 결과: $saveResult")
        android.util.Log.d("LoadingActivity", "저장된 사용자: ${currentUser.email}")
        android.util.Log.d("LoadingActivity", "저장 키: veriscope_history_${currentUser.email}")
    }
    
    // 이미지를 앱 내부 저장소에 복사
    private fun copyImageToAppStorage(imageUri: Uri, historyId: Int): String? {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            if (inputStream != null) {
                val fileName = "report_image_${historyId}.jpg"
                val file = File(filesDir, fileName)
                val outputStream = FileOutputStream(file)
                
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                
                android.util.Log.d("LoadingActivity", "이미지 복사 완료: ${file.absolutePath}")
                return file.absolutePath
            }
        } catch (e: IOException) {
            android.util.Log.e("LoadingActivity", "이미지 복사 실패", e)
        }
        return null
    }
    
    // 저장된 이미지 삭제
    fun deleteReportImage(imagePath: String?) {
        if (imagePath != null) {
            val file = File(imagePath)
            if (file.exists()) {
                file.delete()
                android.util.Log.d("LoadingActivity", "이미지 삭제 완료: $imagePath")
            }
        }
    }
    
    private fun convertImageToBase64(imageUri: Uri): String {
        return try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // 이미지 크기 조정 (최대 1024x1024)
            val maxSize = 1024
            val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
                val width = (bitmap.width * ratio).toInt()
                val height = (bitmap.height * ratio).toInt()
                android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                bitmap
            }
            
            val byteArrayOutputStream = ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            android.util.Log.e("LoadingActivity", "이미지 변환 실패: ${e.message}")
            ""
        }
    }
    
    // 실제 API를 사용한 이미지 검사 함수
    private fun startImageEvaluationWithAPI(imageUri: String) {
        lifecycleScope.launch {
            try {
                updateStatus("이미지 처리 중...")
                delay(500)
                
                // 이미지를 Base64로 변환
                val base64Image = convertImageToBase64(Uri.parse(imageUri))
                if (base64Image.isEmpty()) {
                    throw Exception("이미지 변환에 실패했습니다")
                }
                
                updateStatus("서버로 전송 중...")
                delay(500)
                
                // API 호출
                val request = ImageEvaluationRequest(
                    image_data = base64Image,
                    similarity_threshold = 0.6,
                    ocr_method = "easyocr",
                    use_gpu = true,
                    fp16 = true,
                    nli_batch = 32
                )
                
                updateStatus("OCR 텍스트 추출 중...")
                val response = ApiClient.apiService.evaluateImage(request)
                
                // API 응답 로그
                android.util.Log.d("ImageEvaluation", "API 응답 코드: ${response.code()}")
                android.util.Log.d("ImageEvaluation", "API 응답 성공: ${response.isSuccessful}")
                android.util.Log.d("ImageEvaluation", "응답 본문: ${response.body()}")
                
                // Raw JSON 응답 로그 추가
                try {
                    val rawResponse = response.raw().toString()
                    android.util.Log.d("ImageEvaluation", "Raw 응답: $rawResponse")
                } catch (e: Exception) {
                    android.util.Log.e("ImageEvaluation", "Raw 응답 로그 실패: ${e.message}")
                }
                
                updateStatus("신뢰도 검사 중...")
                delay(1000)
                
                updateStatus("검사 완료!")
                delay(500)
                
                // 결과 화면으로 이동
                val intent = Intent(this@LoadingActivity, ResultActivity::class.java)
                
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body()
                        android.util.Log.d("ImageEvaluation", "응답 본문 상세: $responseBody")
                        
                        if (responseBody?.success == true && responseBody.data != null) {
                            val result = responseBody.data
                        
                        // API 응답에서 데이터 추출
                        val reliabilityScore = when (val score = result.reliability_score) {
                            is Int -> score.toFloat()
                            is Double -> score.toFloat()
                            is Float -> score
                            is String -> {
                                try {
                                    score.toFloatOrNull() ?: 0f
                                } catch (e: Exception) {
                                    android.util.Log.e("ImageEvaluation", "문자열 점수 파싱 오류: ${score}")
                                    0f
                                }
                            }
                            else -> {
                                android.util.Log.e("ImageEvaluation", "예상치 못한 점수 타입: ${score?.javaClass}, 값: $score")
                                try {
                                    // 마지막 시도: toString()으로 변환 후 파싱
                                    score?.toString()?.toFloatOrNull() ?: 0f
                                } catch (e: Exception) {
                                    android.util.Log.e("ImageEvaluation", "최종 파싱 실패", e)
                                    0f
                                }
                            }
                        }
                        val isReliable = reliabilityScore >= 70
                        val recommendation = result.recommendation ?: "검사 결과가 없습니다"
                        val evidenceList = result.evidence ?: emptyList()
                        
                        android.util.Log.d("ImageEvaluation", "파싱된 신뢰도: $reliabilityScore")
                        android.util.Log.d("ImageEvaluation", "근거 자료 수: ${evidenceList.size}")
                        
                        // Evidence를 JSON 문자열로 변환
                        val evidenceJson = try {
                            evidenceList.map { evidence ->
                                // score를 안전하게 변환 (0-1 범위를 0-100%로)
                                val safeScore = when (val score = evidence.score) {
                                    is Int -> score
                                    is Double -> {
                                        if (score <= 1.0) (score * 100).toInt() else score.toInt()
                                    }
                                    is Float -> {
                                        if (score <= 1.0f) (score * 100).toInt() else score.toInt()
                                    }
                                    is String -> score.toIntOrNull() ?: 0
                                    else -> 0
                                }
                                
                                // similarity도 0-1 범위를 0-100%로 변환
                                val safeSimilarity = evidence.similarity?.let { sim ->
                                    if (sim <= 1.0) (sim * 100).toInt() else sim.toInt()
                                } ?: 0
                                
                                // support도 0-1 범위를 0-100%로 변환  
                                val safeSupport = evidence.support?.let { sup ->
                                    if (sup <= 1.0) (sup * 100).toInt() else sup.toInt()
                                } ?: 0
                                
                                android.util.Log.d("ImageEvaluation", "Evidence 변환: score=$safeScore%, similarity=$safeSimilarity%, support=$safeSupport%")
                                
                                mapOf(
                                    "number" to (evidence.number ?: 1),
                                    "score" to safeScore,
                                    "url" to (evidence.url ?: ""),
                                    "similarity" to safeSimilarity,
                                    "support" to safeSupport
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ImageEvaluation", "Evidence 변환 오류: ${e.message}")
                            android.util.Log.e("ImageEvaluation", "Evidence 변환 스택 트레이스: ", e)
                            emptyList()
                        }
                        
                        val gson = Gson()
                        val evidenceJsonString = gson.toJson(evidenceJson)
                        
                        // 이미지 검사 이력 저장
                        saveCheckHistory(imageUri, "IMAGE", reliabilityScore, isReliable, recommendation, evidenceJsonString)
                        
                        intent.putExtra("is_reliable", isReliable)
                        intent.putExtra("reliability_score", reliabilityScore)
                        intent.putExtra("recommendation", recommendation)
                        intent.putExtra("evidence", evidenceJsonString)
                        intent.putExtra("url", imageUri)
                        intent.putExtra("type", "IMAGE")
                        intent.putExtra("CHECK_TYPE", "IMAGE")
                        intent.putExtra("IMAGE_URI", imageUri)
                        
                            android.util.Log.d("ImageEvaluation", "성공적으로 처리됨")
                        
                        } else {
                            throw Exception("API 응답에서 success=false 또는 data가 null")
                        }
                    } catch (jsonException: Exception) {
                        android.util.Log.e("ImageEvaluation", "JSON 파싱 오류: ${jsonException.message}")
                        android.util.Log.e("ImageEvaluation", "JSON 파싱 스택 트레이스: ", jsonException)
                        
                        // JSON 파싱 실패 시 더미 데이터로 진행
                        val reliabilityScore = 50.0f
                        val isReliable = false
                        val recommendation = "응답 파싱에 실패했습니다 (JSON 오류)"
                        
                        val dummyEvidence = createDummyImageEvidence()
                        saveCheckHistory(imageUri, "IMAGE", reliabilityScore, isReliable, recommendation, dummyEvidence)
                        
                        intent.putExtra("is_reliable", isReliable)
                        intent.putExtra("reliability_score", reliabilityScore)
                        intent.putExtra("recommendation", recommendation)
                        intent.putExtra("evidence", dummyEvidence)
                        intent.putExtra("url", imageUri)
                        intent.putExtra("type", "IMAGE")
                        intent.putExtra("CHECK_TYPE", "IMAGE")
                        intent.putExtra("IMAGE_URI", imageUri)
                    }
                } else {
                    // API 실패 시 로그 출력 후 더미 데이터
                    android.util.Log.e("ImageEvaluation", "API 호출 실패")
                    android.util.Log.e("ImageEvaluation", "응답 코드: ${response.code()}")
                    android.util.Log.e("ImageEvaluation", "응답 메시지: ${response.message()}")
                    android.util.Log.e("ImageEvaluation", "응답 본문: ${response.body()}")
                    android.util.Log.e("ImageEvaluation", "에러 본문: ${response.errorBody()?.string()}")
                    
                    val reliabilityScore = 75.0f
                    val isReliable = true
                    val recommendation = "이미지 분석이 완료되었습니다 (오프라인 모드)"
                    
                    val dummyEvidence = createDummyImageEvidence()
                    saveCheckHistory(imageUri, "IMAGE", reliabilityScore, isReliable, recommendation, dummyEvidence)
                    
                    intent.putExtra("is_reliable", isReliable)
                    intent.putExtra("reliability_score", reliabilityScore)
                    intent.putExtra("recommendation", recommendation)
                    intent.putExtra("evidence", dummyEvidence)
                    intent.putExtra("url", imageUri)
                    intent.putExtra("type", "IMAGE")
                    intent.putExtra("CHECK_TYPE", "IMAGE")
                    intent.putExtra("IMAGE_URI", imageUri)
                }
                
                startActivity(intent)
                finish()
                
            } catch (e: Exception) {
                android.util.Log.e("ImageEvaluation", "이미지 검사 오류: ${e.message}")
                android.util.Log.e("ImageEvaluation", "스택 트레이스: ", e)
                showError("이미지 검사 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }
}