package com.example.veriscope

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.veriscope.data.ApiClient
import com.example.veriscope.data.NewsEvaluationResult
import com.example.veriscope.data.NewsEvaluationRequest
import com.example.veriscope.data.ImageEvaluationRequest
import com.example.veriscope.data.Evidence
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class VeriscopeActivity : AppCompatActivity() {

    // 헤더 관련
    private lateinit var btnBack: TextView
    private lateinit var btnNotification: TextView
    
    // 탭 관련
    private lateinit var btnUrlTab: Button
    private lateinit var btnImageTab: Button
    private lateinit var layoutUrlTab: LinearLayout
    private lateinit var layoutImageTab: LinearLayout

    // URL 검사 관련
    private lateinit var etNewsUrl: EditText
    private lateinit var btnEvaluate: Button

    // 이미지 검사 관련
    private lateinit var layoutImageSelect: LinearLayout
    private lateinit var ivSelectedImage: ImageView
    private lateinit var layoutImagePlaceholder: LinearLayout
    private lateinit var btnSelectImage: Button
    private lateinit var btnEvaluateImage: Button
    private var selectedImageUri: Uri? = null

    // 공통 결과 표시
    private lateinit var cardResult: CardView
    private lateinit var tvReliabilityScore: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var tvElapsedTime: TextView
    private lateinit var layoutEvidence: LinearLayout
    private lateinit var progressBar: ProgressBar

    // 이미지 선택 결과 처리
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                displaySelectedImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_veriscope)

        // 액션바에 뒤로가기 버튼 추가
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "뉴스 신뢰도 평가"

        initViews()
        setupListeners()
        handleIntent()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun initViews() {
        // 헤더 관련
        btnBack = findViewById(R.id.btnBack)
        btnNotification = findViewById(R.id.btnNotification)
        
        // 탭 관련
        btnUrlTab = findViewById(R.id.btnUrlTab)
        btnImageTab = findViewById(R.id.btnImageTab)
        layoutUrlTab = findViewById(R.id.layoutUrlTab)
        layoutImageTab = findViewById(R.id.layoutImageTab)

        // URL 검사 관련
        etNewsUrl = findViewById(R.id.etNewsUrl)
        btnEvaluate = findViewById(R.id.btnEvaluate)

        // 이미지 검사 관련
        layoutImageSelect = findViewById(R.id.layoutImageSelect)
        ivSelectedImage = findViewById(R.id.ivSelectedImage)
        layoutImagePlaceholder = findViewById(R.id.layoutImagePlaceholder)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnEvaluateImage = findViewById(R.id.btnEvaluateImage)

        // 공통 결과 표시
        cardResult = findViewById(R.id.cardResult)
        tvReliabilityScore = findViewById(R.id.tvReliabilityScore)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        tvElapsedTime = findViewById(R.id.tvElapsedTime)
        layoutEvidence = findViewById(R.id.layoutEvidence)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        // 헤더 버튼 리스너
        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        btnNotification.setOnClickListener { 
            Toast.makeText(this, "도움말 기능 준비 중입니다", Toast.LENGTH_SHORT).show()
        }
        
        // 탭 버튼 리스너
        btnUrlTab.setOnClickListener { switchToUrlTab() }
        btnImageTab.setOnClickListener { switchToImageTab() }

        // URL 검사 리스너
        btnEvaluate.setOnClickListener { evaluateNews() }

        // 이미지 검사 리스너
        layoutImageSelect.setOnClickListener { selectImage() }
        btnSelectImage.setOnClickListener { selectImage() }
        btnEvaluateImage.setOnClickListener { evaluateImage() }
    }

    private fun handleIntent() {
        val initialUrl = intent.getStringExtra("initial_url")
        val evaluationType = intent.getStringExtra("evaluation_type")

        // URL이 전달된 경우 입력 필드에 설정
        initialUrl?.let {
            etNewsUrl.setText(it)
        }

        // 평가 타입에 따라 초기 탭 설정
        when (evaluationType) {
            "image" -> switchToImageTab()
            "url" -> switchToUrlTab()
            else -> switchToUrlTab() // 기본값은 URL 탭
        }
    }

    private fun switchToUrlTab() {
        // URL 탭 활성화: button_primary 배경 + 흰색 텍스트
        btnUrlTab.setBackgroundResource(R.drawable.button_primary)
        btnUrlTab.backgroundTintList = null
        btnUrlTab.setTextColor(resources.getColor(android.R.color.white, null))
        btnUrlTab.textSize = 16f
        btnUrlTab.typeface = android.graphics.Typeface.DEFAULT_BOLD

        // 이미지 탭 비활성화: explicit drawable (요청한 #9E9E9E) + 비활성 텍스트 색상
        btnImageTab.setBackgroundResource(R.drawable.tab_unselected_fixed)
        btnImageTab.backgroundTintList = null
        btnImageTab.setTextColor(resources.getColor(R.color.tab_inactive_text, null))
        btnImageTab.textSize = 16f
        btnImageTab.typeface = android.graphics.Typeface.DEFAULT

        layoutUrlTab.visibility = View.VISIBLE
        layoutImageTab.visibility = View.GONE
        cardResult.visibility = View.GONE
    }

    private fun switchToImageTab() {
        // 이미지 탭 활성화: button_primary 배경 + 흰색 텍스트
        btnImageTab.setBackgroundResource(R.drawable.button_primary)
        btnImageTab.backgroundTintList = null
        btnImageTab.setTextColor(resources.getColor(android.R.color.white, null))
        btnImageTab.textSize = 16f
        btnImageTab.typeface = android.graphics.Typeface.DEFAULT_BOLD

        // URL 탭 비활성화: explicit drawable (요청한 #9E9E9E) + 비활성 텍스트 색상
        btnUrlTab.setBackgroundResource(R.drawable.tab_unselected_fixed)
        btnUrlTab.backgroundTintList = null
        btnUrlTab.setTextColor(resources.getColor(R.color.tab_inactive_text, null))
        btnUrlTab.textSize = 16f
        btnUrlTab.typeface = android.graphics.Typeface.DEFAULT

        layoutImageTab.visibility = View.VISIBLE
        layoutUrlTab.visibility = View.GONE
        cardResult.visibility = View.GONE
    }

    private fun evaluateNews() {
        val url = etNewsUrl.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(this, "URL을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        if (!url.startsWith("http")) {
            Toast.makeText(this, "올바른 URL을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        // UI 상태 변경 (URL 평가임을 표시)
        showLoading(true, isImageEvaluation = false)

        // API 요청
        val request = NewsEvaluationRequest(
            url = url,
            similarity_threshold = 0.6,
            use_gpu = true,
            fp16 = true,
            nli_batch = 128
        )

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.evaluateNews(request)
                showLoading(false)

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true) {
                        displayResult(apiResponse.data, getElapsedTime(response))
                        Toast.makeText(this@VeriscopeActivity, "평가 완료!", Toast.LENGTH_SHORT).show()
                    } else {
                        showError("평가 실패: ${apiResponse?.message}")
                    }
                } else {
                    showError("서버 오류: ${response.code()}")
                }
            } catch (e: Exception) {
                showLoading(false)
                showError("네트워크 오류: ${e.message}")
            }
        }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun displaySelectedImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            ivSelectedImage.setImageBitmap(bitmap)
            ivSelectedImage.visibility = View.VISIBLE
            layoutImagePlaceholder.visibility = View.GONE
            btnEvaluateImage.isEnabled = true
        } catch (e: IOException) {
            Toast.makeText(this, "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun evaluateImage() {
        selectedImageUri?.let { uri ->
            showLoading(true, isImageEvaluation = true)

            lifecycleScope.launch {
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    val base64Image = bitmapToBase64(bitmap)

                    val request = ImageEvaluationRequest(
                        image_data = base64Image,
                        similarity_threshold = 0.6,
                        use_gpu = true,
                        fp16 = true,
                        nli_batch = 128
                    )

                    val response = ApiClient.apiService.evaluateImage(request)
                    showLoading(false)

                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.success == true) {
                            displayResult(apiResponse.data, getElapsedTime(response))
                            Toast.makeText(this@VeriscopeActivity, "이미지 평가 완료!", Toast.LENGTH_SHORT).show()
                        } else {
                            showError("이미지 평가 실패: ${apiResponse?.message}")
                        }
                    } else {
                        showError("서버 오류: ${response.code()}")
                    }
                } catch (e: Exception) {
                    showLoading(false)
                    showError("이미지 처리 오류: ${e.message}")
                }
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun showLoading(show: Boolean, isImageEvaluation: Boolean = false) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            cardResult.visibility = View.GONE
            btnEvaluate.isEnabled = false
            btnEvaluateImage.isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            btnEvaluate.isEnabled = true
            btnEvaluateImage.isEnabled = selectedImageUri != null
        }
    }

    private fun displayResult(result: NewsEvaluationResult?, elapsedSeconds: Double? = null) {
        if (result == null) {
            tvReliabilityScore.text = "평가 실패"
            tvReliabilityScore.setTextColor(Color.parseColor("#E74C3C"))
            tvRecommendation.text = "평가 결과를 가져올 수 없습니다."
            tvElapsedTime.text = ""
            cardResult.visibility = View.VISIBLE
            return
        }

        // 신뢰도 점수 표시
        val score = when (val rawScore = result.reliability_score) {
            is Int -> rawScore
            is Double -> rawScore.toInt()
            is Float -> rawScore.toInt()
            is String -> rawScore.toIntOrNull() ?: 0
            else -> 0
        }
        val level = result.reliability_level
        tvReliabilityScore.text = String.format("신뢰도: %d%% - %s", score, level)

        // 점수에 따른 색상 변경
        val color = getScoreColor(score)
        tvReliabilityScore.setTextColor(color)

        // 권장사항 표시
        tvRecommendation.text = result.recommendation

        // 처리 시간 표시
        elapsedSeconds?.let { time ->
            tvElapsedTime.text = String.format("처리 시간: %.1f초", time)
        } ?: run {
            tvElapsedTime.text = ""
        }

        // 근거 자료 표시
        result.evidence?.let { evidenceList ->
            displayEvidence(evidenceList)
        }

        // 결과 카드 표시
        cardResult.visibility = View.VISIBLE
    }

    private fun displayEvidence(evidenceList: List<Evidence>) {
        layoutEvidence.removeAllViews()

        for (evidence in evidenceList) {
            val tvEvidence = TextView(this)
            
            val domainName = extractDomainName(evidence.url ?: "")
            
            val text = String.format(
                "%d. %s 기사 - 신뢰도 %d%% (유사도 %.0f%%)\n%s",
                evidence.number ?: 1,
                domainName,
                evidence.score ?: 0,
                (evidence.similarity ?: 0.0) * 100,
                evidence.url ?: ""
            )

            tvEvidence.text = text
            tvEvidence.textSize = 13f
            tvEvidence.setPadding(16, 16, 16, 16)
            tvEvidence.setBackgroundColor(Color.parseColor("#F5F5F5"))
            tvEvidence.isClickable = true
            tvEvidence.isFocusable = true
            
            tvEvidence.setOnClickListener {
                evidence.url?.let { url ->
                    openUrl(url)
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 8, 0, 8)

            layoutEvidence.addView(tvEvidence, params)
        }
    }
    
    private fun extractDomainName(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: ""
            
            when {
                host.contains("naver.com") -> "네이버뉴스"
                host.contains("daum.net") -> "다음뉴스"
                host.contains("chosun.com") -> "조선일보"
                host.contains("donga.com") -> "동아일보"
                host.contains("joongang.co.kr") -> "중앙일보"
                host.contains("hankyung.com") -> "한국경제"
                host.contains("mk.co.kr") -> "매일경제"
                host.contains("ytn.co.kr") -> "YTN"
                host.contains("jtbc.co.kr") -> "JTBC"
                host.contains("sbs.co.kr") -> "SBS"
                host.contains("kbs.co.kr") -> "KBS"
                host.contains("mbc.co.kr") -> "MBC"
                host.contains("edaily.co.kr") -> "이데일리"
                host.contains("newsis.com") -> "뉴시스"
                host.contains("yonhapnews.co.kr") -> "연합뉴스"
                else -> {
                    host.removePrefix("www.").split(".").firstOrNull()?.replaceFirstChar { 
                        if (it.isLowerCase()) it.titlecase() else it.toString() 
                    } ?: "뉴스"
                }
            }
        } catch (e: Exception) {
            "뉴스"
        }
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "링크를 열 수 없습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getScoreColor(score: Int): Int {
        return when {
            score >= 80 -> Color.parseColor("#27AE60") // 초록색
            score >= 60 -> Color.parseColor("#F39C12") // 주황색
            else -> Color.parseColor("#E74C3C") // 빨간색
        }
    }

    private fun getElapsedTime(response: retrofit2.Response<*>): Double? {
        return try {
            val processingTime = response.headers()["X-Processing-Time"]
            processingTime?.toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        tvReliabilityScore.text = "오류 발생"
        tvReliabilityScore.setTextColor(Color.parseColor("#E74C3C"))
        tvRecommendation.text = message
        tvElapsedTime.text = ""
        cardResult.visibility = View.VISIBLE
    }
}