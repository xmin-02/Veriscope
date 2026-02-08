package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.example.veriscope.data.CheckHistory
import com.example.veriscope.data.CheckHistoryLocal
import com.example.veriscope.utils.UserManager

class ReportActivity : AppCompatActivity() {
    
    // UI 컴포넌트들
    private lateinit var backButton: ImageView
    private lateinit var titleText: TextView
    private lateinit var totalChecksText: TextView
    private lateinit var unreliableCountText: TextView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var reportHistoryAdapter: ReportHistoryAdapter
    private lateinit var progressBar: ProgressBar
    
    // 하단 탭들
    private lateinit var tabHome: LinearLayout
    private lateinit var tabReport: LinearLayout
    private lateinit var tabHistory: LinearLayout
    private lateinit var tabProfile: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        
        initViews()
        setupClickListeners()
        loadReportableHistory()
    }
    
    override fun onResume() {
        super.onResume()
        // 제보하기 페이지로 돌아올 때마다 목록 새로고침 (신고 완료된 항목 제거 포함)
        loadReportableHistory()
    }
    
    private fun initViews() {
        // 상단 헤더
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        titleText.text = "제보하기"
        
        // 통계 섹션
        totalChecksText = findViewById(R.id.totalChecksText)
        unreliableCountText = findViewById(R.id.unreliableCountText)
        
        // 검사 내역 리스트
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        reportHistoryAdapter = ReportHistoryAdapter { history ->
            openReportForm(history)
        }
        historyRecyclerView.adapter = reportHistoryAdapter
        
        progressBar = findViewById(R.id.progressBar)
        
        // 하단 탭들
        tabHome = findViewById(R.id.tabHome)
        tabReport = findViewById(R.id.tabReport)
        tabHistory = findViewById(R.id.tabHistory)
        tabProfile = findViewById(R.id.tabProfile)
    }
    
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
        
        // 하단 탭 클릭 리스너들
        tabHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        
        tabReport.setOnClickListener {
            // 현재 제보하기 화면이므로 같은 탭을 누렀을 때는 아무 동작도 하지 않음
            Toast.makeText(this, "이미 제보하기 화면입니다", Toast.LENGTH_SHORT).show()
        }
        
        tabHistory.setOnClickListener {
            // 리워드 페이지로 이동
            val intent = Intent(this, RewardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        
        tabProfile.setOnClickListener {
            val intent = Intent(this, MyPageActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
    
    private fun loadReportableHistory() {
        progressBar.visibility = View.VISIBLE
        
        // 로컬 저장된 검사 내역 불러오기
        val localHistory = loadLocalHistory()
        
        // 전체 히스토리에서 URL 기준 중복 제거
        val dedupedHistory = localHistory.distinctBy { it.url ?: "__id_${it.id}" }
        
        // 주의 필요한 항목들만 필터링 (신뢰도 70% 미만)
        val lowReliabilityHistory = dedupedHistory.filter { it.reliabilityScore < 70 }
        
        // 제보된 항목 목록 가져오기
        val prefs = getSharedPreferences("veriscope_reported", MODE_PRIVATE)
        val reportedItems = prefs.getStringSet("reported_items", setOf()) ?: setOf()
        
        progressBar.visibility = View.GONE

        // 통계 업데이트 (제보된 항목 제외한 개수로)
        val reportableHistory = filterOutReportedItems(lowReliabilityHistory)
        totalChecksText.text = dedupedHistory.size.toString()
        unreliableCountText.text = reportableHistory.size.toString()

        // 어댑터에 전체 목록과 제보된 항목 목록 모두 전달
        reportHistoryAdapter.updateHistory(lowReliabilityHistory)
        reportHistoryAdapter.updateReportedItems(reportedItems)
        
        // 제보 가능한 항목이 없는 경우 사용자에게 안내
        if (reportableHistory.isEmpty()) {
            android.util.Log.d("ReportActivity", "제보 가능한 항목이 없습니다")
        }
    }
    
    private fun loadLocalHistory(): List<CheckHistory> {
        val userManager = UserManager(this)
        val currentUser = userManager.getCurrentUser()
        if (currentUser == null) {
            android.util.Log.w("ReportActivity", "로그인되지 않은 상태에서 이력 로드 시도")
            return emptyList()
        }
        
        val prefs = getSharedPreferences("veriscope_history_${currentUser.email}", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", "[]") ?: "[]"
        
        return try {
            val gson = Gson()
            val localHistoryArray = gson.fromJson(historyJson, Array<CheckHistoryLocal>::class.java)
            
            // 로컬 히스토리를 CheckHistory로 변환
            localHistoryArray?.map { local ->
                CheckHistory(
                    id = local.id,
                    title = local.title,
                    type = local.type,
                    reliabilityScore = local.reliabilityScore,
                    isReliable = local.isReliable,
                    checkedAt = local.checkedAt,
                    url = local.url,
                    savedImagePath = local.savedImagePath,
                    evidence = local.evidence // 근거 자료 포함
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun filterOutReportedItems(historyList: List<CheckHistory>): List<CheckHistory> {
        return try {
            // 제보된 항목 목록 가져오기
            val prefs = getSharedPreferences("veriscope_reported", MODE_PRIVATE)
            val reportedItems = prefs.getStringSet("reported_items", setOf()) ?: setOf()
            
            android.util.Log.d("ReportActivity", "제보된 항목 수: ${reportedItems.size}")
            android.util.Log.d("ReportActivity", "제보된 항목들: ${reportedItems.joinToString(", ")}")
            
            // 제보되지 않은 항목들만 필터링
            historyList.filter { history ->
                // 여러 가지 가능한 식별자로 확인
                val possibleIds = mutableListOf<String>()
                
                when (history.type) {
                    "URL" -> {
                        if (!history.url.isNullOrEmpty()) {
                            possibleIds.add(history.url!!)
                        }
                        possibleIds.add("unknown_${history.id}")
                    }
                    "IMAGE" -> {
                        // 이미지의 경우 여러 가능한 식별자 확인
                        if (!history.url.isNullOrEmpty()) {
                            possibleIds.add(history.url!!)
                        }
                        if (!history.savedImagePath.isNullOrEmpty()) {
                            possibleIds.add(history.savedImagePath!!)
                        }
                        possibleIds.add("unknown_${history.id}")
                    }
                    else -> {
                        possibleIds.add("general_${history.id}")
                    }
                }
                
                // 가능한 식별자 중 하나라도 제보된 목록에 있으면 제외
                val isReported = possibleIds.any { id -> reportedItems.contains(id) }
                
                if (isReported) {
                    val matchedId = possibleIds.find { id -> reportedItems.contains(id) }
                    android.util.Log.d("ReportActivity", "제보된 항목 제외: ${history.title} (매칭된 ID: $matchedId)")
                } else {
                    android.util.Log.d("ReportActivity", "제보되지 않은 항목: ${history.title} (확인된 ID들: ${possibleIds.joinToString(", ")})")
                }
                
                !isReported
            }
        } catch (e: Exception) {
            android.util.Log.e("ReportActivity", "제보된 항목 필터링 오류", e)
            historyList // 오류 발생 시 원본 리스트 반환
        }
    }

    private fun openReportForm(history: CheckHistory) {
        val intent = Intent(this, ReportFormActivity::class.java)
        
        // 검사 이력에서 타입과 URL/이미지 정보 전달
        intent.putExtra("REPORT_TYPE", history.type)
        
        if (history.type == "URL") {
            intent.putExtra("REPORT_URL", history.url ?: "")
        } else if (history.type == "IMAGE") {
            // 저장된 이미지 경로가 있으면 FileProvider URI 사용, 없으면 원본 URI 사용
            val imageToSend = if (!history.savedImagePath.isNullOrEmpty()) {
                try {
                    val file = java.io.File(history.savedImagePath!!)
                    if (file.exists()) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            "com.example.veriscope.fileprovider",
                            file
                        )
                        uri.toString()
                    } else {
                        history.url ?: ""
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReportActivity", "FileProvider URI 생성 실패", e)
                    history.url ?: ""
                }
            } else {
                history.url ?: ""
            }
            intent.putExtra("REPORT_IMAGE", imageToSend)
            intent.putExtra("SAVED_IMAGE_PATH", history.savedImagePath) // 원본 파일 경로도 전달
            android.util.Log.d("ReportActivity", "이미지 전달: $imageToSend")
        }
        
        startActivity(intent)
    }
}