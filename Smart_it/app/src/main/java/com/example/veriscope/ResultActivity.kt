package com.example.veriscope

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ResultActivity : AppCompatActivity() {
    
    // UI 컴포넌트들
    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var inputImageContainer: LinearLayout
    private lateinit var inputImageView: ImageView
    private lateinit var reliabilityCard: LinearLayout
    private lateinit var reliabilityPercentage: TextView
    private lateinit var reliabilityLabel: TextView
    private lateinit var reliabilityDescription: TextView
    private lateinit var evidenceRecyclerView: RecyclerView
    private lateinit var evidenceAdapter: EvidenceAdapter
    private lateinit var confirmButton: Button
    private lateinit var reportButton: Button
    
    // 하단 탭들
    private lateinit var tabHome: LinearLayout
    private lateinit var tabUrlCheck: LinearLayout
    private lateinit var tabImageCheck: LinearLayout
    private lateinit var tabProfile: LinearLayout
    
    // 데이터
    private var reliabilityScore: Float = 0f
    private var evidenceList = mutableListOf<Evidence>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        
        initViews()
        setupClickListeners()
        displayResult()
    }
    
    private fun initViews() {
        // 상단 헤더
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        
        // 입력 이미지 컨테이너
        inputImageContainer = findViewById(R.id.inputImageContainer)
        inputImageView = findViewById(R.id.inputImageView)
        
        // 신뢰도 카드
        reliabilityCard = findViewById(R.id.reliabilityCard)
        reliabilityPercentage = findViewById(R.id.reliabilityPercentage)
        reliabilityLabel = findViewById(R.id.reliabilityLabel)
        reliabilityDescription = findViewById(R.id.reliabilityDescription)
        
        // 근거 자료 리스트
        evidenceRecyclerView = findViewById(R.id.evidenceRecyclerView)
        evidenceRecyclerView.layoutManager = LinearLayoutManager(this)
        evidenceAdapter = EvidenceAdapter { evidence ->
            // 근거 자료 클릭 시 웹사이트 열기
            openUrl(evidence.url)
        }
        evidenceRecyclerView.adapter = evidenceAdapter
        
        // 하단 버튼들
        confirmButton = findViewById(R.id.confirmButton)
        reportButton = findViewById(R.id.reportButton)
        
        // 하단 탭들
        tabHome = findViewById(R.id.tabHome)
        tabUrlCheck = findViewById(R.id.tabUrlCheck)
        tabImageCheck = findViewById(R.id.tabImageCheck)
        tabProfile = findViewById(R.id.tabProfile)
    }
    
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
        
        confirmButton.setOnClickListener {
            val fromHistory = intent.getBooleanExtra("FROM_HISTORY", false)
            if (fromHistory) {
                // 마이페이지로 돌아가기 (뒤로가기)
                finish()
            } else {
                val rewardPoints = 5
                val newsType = intent.getStringExtra("type") ?: "URL"
                val newsUrl = intent.getStringExtra("url") ?: ""
                val toConfirm = Intent(this, ConfirmCompleteActivity::class.java)
                toConfirm.putExtra("REWARD_POINTS", rewardPoints)
                toConfirm.putExtra("RELIABILITY_SCORE", reliabilityScore)
                toConfirm.putExtra("CHECK_TYPE", newsType)
                
                // 이미지 타입인 경우 이미지를 내부 저장소에 복사
                if (newsType == "IMAGE" && newsUrl.isNotEmpty()) {
                    val savedImagePath = saveImageToInternalStorage(newsUrl)
                    toConfirm.putExtra("SAVED_IMAGE_PATH", savedImagePath)
                    toConfirm.putExtra("ORIGINAL_IMAGE_URI", newsUrl)
                } else if (newsType == "URL") {
                    // URL 타입인 경우 검사한 URL 전달
                    toConfirm.putExtra("CHECKED_URL", newsUrl)
                }
                
                startActivity(toConfirm)
            }
        }
        
        reportButton.setOnClickListener {
            openReportPage()
        }
        
        // 하단 탭 클릭 리스너들
        tabHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        
        tabUrlCheck.setOnClickListener {
            // 제보하기 버튼 - ReportActivity로 이동
            val intent = Intent(this, ReportActivity::class.java)
            startActivity(intent)
        }
        
        tabImageCheck.setOnClickListener {
            // 리워드 확인 버튼 - RewardActivity로 이동
            val intent = Intent(this, RewardActivity::class.java)
            startActivity(intent)
        }
        
        tabProfile.setOnClickListener {
            val intent = Intent(this, MyPageActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun displayResult() {
        // Intent에서 데이터 받기
        reliabilityScore = intent.getFloatExtra("reliability_score", 0f)
        val isReliable = intent.getBooleanExtra("is_reliable", false)
        val recommendation = intent.getStringExtra("recommendation") ?: ""
        val evidenceJson = intent.getStringExtra("evidence") ?: "[]"
        val newsUrl = intent.getStringExtra("url") ?: ""
        val newsType = intent.getStringExtra("type") ?: "URL"
        val fromHistory = intent.getBooleanExtra("FROM_HISTORY", false)
        
        // 제목 설정
        titleText.text = if (newsType == "IMAGE") "이미지 검사 결과" else "URL 검사 결과"
        
        // 이미지 검사 결과인 경우 입력 이미지 표시
        if (newsType == "IMAGE") {
            displayInputImage(newsUrl)
        } else {
            inputImageContainer.visibility = View.GONE
        }
        
        // 신뢰도 카드 설정
        setupReliabilityCard(reliabilityScore, isReliable, recommendation)
        
        // 근거 자료 파싱 및 표시
        parseAndDisplayEvidence(evidenceJson)

        // 마이페이지 최근 내역에서 진입한 경우: 뒤로가기 버튼 제공
        if (fromHistory) {
            confirmButton.text = "뒤로가기"
            confirmButton.setBackgroundResource(R.drawable.button_background)
            confirmButton.setTextColor(ContextCompat.getColor(this, R.color.white))
            
            // 제보하기 버튼은 항상 숨김
            reportButton.visibility = View.GONE
        }
    }
    
    private fun setupReliabilityCard(score: Float, isReliable: Boolean, recommendation: String) {
        reliabilityPercentage.text = "${score.toInt()}%"
        
        when {
            score >= 70 -> {
                // 신뢰할 수 있음 (파란색)
                reliabilityCard.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_blue))
                reliabilityLabel.text = "신뢰도 - 매우 높음"
                reliabilityDescription.text = "이 기사는 신뢰할 수 있습니다"
                reliabilityPercentage.setTextColor(ContextCompat.getColor(this, R.color.white))
                reliabilityLabel.setTextColor(ContextCompat.getColor(this, R.color.white))
                reliabilityDescription.setTextColor(ContextCompat.getColor(this, R.color.white))
                
                // 제보하기 버튼 숨김
                reportButton.visibility = View.GONE
            }
            score >= 40 -> {
                // 주의 필요 (주황색)
                reliabilityCard.setBackgroundColor(ContextCompat.getColor(this, R.color.orange))
                reliabilityLabel.text = "신뢰도 - 보통"
                reliabilityDescription.text = "신중한 확인이 필요합니다"
                reliabilityPercentage.setTextColor(ContextCompat.getColor(this, R.color.white))
                reliabilityLabel.setTextColor(ContextCompat.getColor(this, R.color.white))
                reliabilityDescription.setTextColor(ContextCompat.getColor(this, R.color.white))
                
                // 제보하기 버튼 숨김
                reportButton.visibility = View.GONE
            }
            else -> {
                // 신뢰하기 어려움 (빨간색)
                reliabilityCard.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
                reliabilityLabel.text = "신뢰도 - 매우 낮음"
                reliabilityDescription.text = "허위 기사일 가능성이 높습니다"
                reliabilityPercentage.setTextColor(ContextCompat.getColor(this, R.color.white))
                reliabilityLabel.setTextColor(ContextCompat.getColor(this, R.color.white))
                reliabilityDescription.setTextColor(ContextCompat.getColor(this, R.color.white))
                
                // 제보하기 버튼 숨김
                reportButton.visibility = View.GONE
            }
        }
    }
    
    private fun parseAndDisplayEvidence(evidenceJson: String) {
        try {
            val gson = Gson()
            val evidenceType = object : TypeToken<List<Evidence>>() {}.type
            val evidences: List<Evidence> = gson.fromJson(evidenceJson, evidenceType)
            
            evidenceList.clear()
            evidenceList.addAll(evidences)
            evidenceAdapter.updateEvidence(evidenceList)
            
        } catch (e: Exception) {
            // 파싱 실패 시 더미 데이터 표시
            showDummyEvidence()
        }
    }
    
    private fun showDummyEvidence() {
        val dummyEvidence = listOf(
            Evidence(1, 87, "https://news.example.com/covid19-vaccine-study", 72, 85),
            Evidence(2, 91, "https://medical.example.com/vaccine-research", 78, 90),
            Evidence(3, 83, "https://health.example.com/vaccine-benefits", 70, 82),
            Evidence(4, 76, "https://science.example.com/covid-research", 65, 78),
            Evidence(5, 79, "https://journal.example.com/medical-study", 68, 80)
        )
        
        evidenceList.clear()
        evidenceList.addAll(dummyEvidence)
        evidenceAdapter.updateEvidence(evidenceList)
    }
    
    private fun displayInputImage(imageUri: String) {
        try {
            inputImageContainer.visibility = View.VISIBLE
            val uri = Uri.parse(imageUri)
            inputImageView.setImageURI(uri)
        } catch (e: Exception) {
            // 이미지 로딩 실패 시 컨테이너 숨기기
            inputImageContainer.visibility = View.GONE
            Toast.makeText(this, "이미지를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openReportPage() {
        val reportIntent = Intent(this, ReportFormActivity::class.java)
        
        // 검사 타입 결정
        val checkType = intent.getStringExtra("type") ?: "URL"
        reportIntent.putExtra("REPORT_TYPE", checkType)
        
        // 검사 결과 정보 전달
        val reliabilityText = when {
            reliabilityScore >= 80f -> "높음 (${reliabilityScore.toInt()}%)"
            reliabilityScore >= 50f -> "보통 (${reliabilityScore.toInt()}%)" 
            else -> "낮음 (${reliabilityScore.toInt()}%)"
        }
        reportIntent.putExtra("CHECK_RESULT", if (reliabilityScore >= 50f) "신뢰할 수 있음" else "신뢰하기 어려움")
        reportIntent.putExtra("RELIABILITY_SCORE", reliabilityText)
        
        if (checkType == "URL") {
            // URL 검사에서 온 경우
            val url = intent.getStringExtra("url") ?: ""
            reportIntent.putExtra("REPORT_URL", url)
            android.util.Log.d("ResultActivity", "URL 제보 페이지로 이동: $url")
        } else if (checkType == "IMAGE") {
            // 이미지 검사에서 온 경우  
            val imageUri = intent.getStringExtra("url") ?: ""
            reportIntent.putExtra("REPORT_IMAGE", imageUri)
            
            // 저장된 이미지 경로도 전달 (있는 경우)
            val savedImagePath = intent.getStringExtra("SAVED_IMAGE_PATH") ?: ""
            if (savedImagePath.isNotEmpty()) {
                reportIntent.putExtra("SAVED_IMAGE_PATH", savedImagePath)
            }
            
            android.util.Log.d("ResultActivity", "이미지 제보 페이지로 이동: $imageUri")
        }
        
        startActivity(reportIntent)
    }
    
    private fun saveImageToInternalStorage(imageUriString: String): String {
        return try {
            val imageUri = Uri.parse(imageUriString)
            val inputStream = contentResolver.openInputStream(imageUri)
            
            if (inputStream != null) {
                // 내부 저장소에 이미지 폴더 생성
                val imageDir = File(filesDir, "report_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                
                // 고유 파일명 생성
                val fileName = "report_image_${System.currentTimeMillis()}.jpg"
                val imageFile = File(imageDir, fileName)
                
                // 이미지 복사
                val outputStream = imageFile.outputStream()
                inputStream.copyTo(outputStream)
                
                inputStream.close()
                outputStream.close()
                
                android.util.Log.d("ResultActivity", "이미지 저장 성공: ${imageFile.absolutePath}")
                imageFile.absolutePath
            } else {
                android.util.Log.e("ResultActivity", "이미지 InputStream이 null")
                ""
            }
        } catch (e: Exception) {
            android.util.Log.e("ResultActivity", "이미지 저장 실패: ${e.message}", e)
            ""
        }
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "URL을 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
}

// 근거 자료 데이터 클래스
data class Evidence(
    val number: Int,
    val score: Int,
    val url: String,
    val similarity: Int,
    val support: Int
)