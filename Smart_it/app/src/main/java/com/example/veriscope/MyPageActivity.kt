package com.example.veriscope

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.example.veriscope.data.ApiClient
import com.example.veriscope.data.HistoryResponse
import com.example.veriscope.data.ProfileResponse
import com.example.veriscope.data.CheckHistory
import com.example.veriscope.data.CheckHistoryLocal
import com.example.veriscope.utils.UserManager
import com.example.veriscope.HistoryAdapter
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MyPageActivity : AppCompatActivity() {

    private lateinit var profileSection: LinearLayout
    private lateinit var userNameText: TextView
    private lateinit var userEmailText: TextView
    private lateinit var userJoinDateText: TextView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var refreshButton: ImageButton
    
    // 통계 관련
    private lateinit var totalChecksText: TextView
    private lateinit var reliableCountText: TextView
    private lateinit var unreliableCountText: TextView
    
    private lateinit var userManager: com.example.veriscope.utils.UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_page)

        userManager = com.example.veriscope.utils.UserManager(this)
        
        initViews()
        setupListeners()
        loadUserProfile()
        loadCheckHistory()
    }

    override fun onResume() {
        super.onResume()
        // 마이페이지로 돌아올 때마다 데이터 새로고침
        loadCheckHistory()
    }

    private fun initViews() {
        // 뒤로가기 버튼
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // 프로필 섹션
        profileSection = findViewById(R.id.profileSection)
        userNameText = findViewById(R.id.userNameText)
        userEmailText = findViewById(R.id.userEmailText)
        userJoinDateText = findViewById(R.id.userJoinDateText)

        // 통계 섹션
        totalChecksText = findViewById(R.id.totalChecksText)
        reliableCountText = findViewById(R.id.reliableCountText)
        unreliableCountText = findViewById(R.id.unreliableCountText)

        // 검사 내역
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // HistoryAdapter 초기화
        val adapter = HistoryAdapter { history ->
            // 검사 상세보기
            showHistoryDetail(history)
        }
        historyRecyclerView.adapter = adapter

        refreshButton = findViewById(R.id.refreshButton)
        
        // 제보 섹션
        val reportSection = findViewById<LinearLayout>(R.id.reportSection)
    }

    private fun setupListeners() {
        refreshButton.setOnClickListener {
            loadCheckHistory()
        }

        // 로그아웃 버튼
        findViewById<LinearLayout>(R.id.logoutSection).setOnClickListener {
            logout()
        }

        // 계정 관리
        findViewById<LinearLayout>(R.id.accountManageSection).setOnClickListener {
            val intent = Intent(this, AccountManagementActivity::class.java)
            startActivity(intent)
        }

        // 문의하기
        findViewById<LinearLayout>(R.id.contactSection).setOnClickListener {
            openContactUs()
        }

        // 앱 정보
        findViewById<LinearLayout>(R.id.appInfoSection).setOnClickListener {
            showAppInfo()
        }
        
        // 제보하기 페이지
        findViewById<LinearLayout>(R.id.reportSection).setOnClickListener {
            openReportPage()
        }
    }

    private fun loadUserProfile() {
        // 먼저 로컬 사용자 정보를 우선적으로 로드
        loadLocalUserInfo()
        
        // 그 다음 서버에서 추가 정보 가져오기 (선택적)
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getUserProfile()
                
                if (response.isSuccessful) {
                    val profileResponse = response.body()
                    if (profileResponse != null) {
                        try {
                            val profile = profileResponse.profile
                            // 서버 데이터가 더미가 아닌 경우에만 사용
                            if (profile.name != "사용자" && profile.email != "user@veriscope.com") {
                                userNameText.text = profile.name
                                userEmailText.text = profile.email
                                userJoinDateText.text = "가입일: ${profile.joinDate}"
                                android.util.Log.d("MyPageActivity", "서버에서 실제 사용자 정보 로드: ${profile.name} (${profile.email})")
                            } else {
                                android.util.Log.d("MyPageActivity", "서버 더미 데이터 무시, 로컬 정보 유지")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("MyPageActivity", "서버 응답 파싱 실패: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MyPageActivity", "서버 연결 실패: ${e.message}")
            }
        }
    }

    private fun loadLocalUserInfo() {
        android.util.Log.d("MyPageActivity", "사용자 정보 로드 시작")
        android.util.Log.d("MyPageActivity", "UserManager 로그인 상태: ${userManager.isLoggedIn()}")
        
        val currentUser = userManager.getCurrentUser()
        if (currentUser != null) {
            userNameText.text = currentUser.name
            userEmailText.text = currentUser.email
            userJoinDateText.text = "가입일: 2024.01.15"
            android.util.Log.d("MyPageActivity", "UserManager에서 사용자 정보 로드: ${currentUser.name} (${currentUser.email})")
            return  // 여기서 함수 종료
        } 
        
        android.util.Log.w("MyPageActivity", "UserManager에 사용자 정보 없음, SharedPreferences 확인")
        
        // UserManager에 사용자 정보가 없으면 user_prefs에서 직접 확인
        val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userPrefName = userPrefs.getString("user_name", null)
        val userPrefEmail = userPrefs.getString("user_email", null)
        val isLoggedIn = userPrefs.getBoolean("is_logged_in", false)
        
        android.util.Log.d("MyPageActivity", "user_prefs 상태 - 로그인: $isLoggedIn, 이름: $userPrefName, 이메일: $userPrefEmail")
        
        if (userPrefName != null && userPrefEmail != null && isLoggedIn) {
            userNameText.text = userPrefName
            userEmailText.text = userPrefEmail
            userJoinDateText.text = "가입일: 2024.01.15"
            android.util.Log.d("MyPageActivity", "user_prefs에서 사용자 정보 로드: $userPrefName ($userPrefEmail)")
            return  // 여기서 함수 종료
        }
        
        // 다른 SharedPreferences도 확인
        val sharedPrefs = getSharedPreferences("veriscope_user", MODE_PRIVATE)
        val savedEmail = sharedPrefs.getString("email", null)
        val savedName = sharedPrefs.getString("name", null)
        
        if (savedEmail != null && savedName != null) {
            userNameText.text = savedName
            userEmailText.text = savedEmail
            userJoinDateText.text = "가입일: 2024.01.15"
            android.util.Log.d("MyPageActivity", "veriscope_user SharedPrefs에서 사용자 정보 로드: $savedName ($savedEmail)")
            return  // 여기서 함수 종료
        }
        
        // 모든 곳에 사용자 정보가 없으면 기본값 표시
        userNameText.text = "사용자"
        userEmailText.text = "로그인이 필요합니다"
        userJoinDateText.text = "가입일: -"
        android.util.Log.w("MyPageActivity", "모든 위치에서 로그인된 사용자 정보를 찾을 수 없음")
    }

    private fun loadCheckHistory() {
        android.util.Log.d("MyPageActivity", "검사 이력 로드 시작")
        
        // 로컬 데이터 우선 로드
        val localHistory = loadLocalHistory()
        android.util.Log.d("MyPageActivity", "로드된 로컬 이력 개수: ${localHistory.size}")
        
        if (localHistory.isNotEmpty()) {
            // 로컬 데이터가 있으면 URL 기준으로 중복 제거 후 사용 (최근 3개만 표시)
            val dedupedLocal = localHistory.distinctBy { it.url ?: "__id_${it.id}" }
            android.util.Log.d("MyPageActivity", "중복 제거 후 이력 개수: ${dedupedLocal.size}")
            
            updateLocalStatistics(dedupedLocal)
            val recentHistory = dedupedLocal.take(3) // 최근 3개만 표시

            // 안전하게 historyAdapter 업데이트
            val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
            (recyclerView?.adapter as? HistoryAdapter)?.updateHistory(recentHistory)
            android.util.Log.d("MyPageActivity", "RecyclerView 업데이트 완료, 표시할 항목 수: ${recentHistory.size}")
            return
        }
        
        // 로컬 데이터가 없으면 서버에서 가져오기 시도
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getUserHistory()
                
                if (response.isSuccessful) {
                    val historyResponse = response.body()
                    if (historyResponse != null) {
                        // 서버에서 받은 히스토리도 URL 기준으로 중복 제거
                        val dedupedServer = historyResponse.history.distinctBy { it.url ?: "__id_${it.id}" }
                        updateStatisticsFromList(dedupedServer)
                        val recentHistory = dedupedServer.take(3) // 최근 3개만 표시

                        // 안전하게 historyAdapter 업데이트
                        val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
                        (recyclerView?.adapter as? HistoryAdapter)?.updateHistory(recentHistory)
                    }
                } else {
                    showError("검사 내역을 불러올 수 없습니다")
                    // 로컬 데이터 우선 표시, 없으면 더미 데이터
                    showLocalDataOrDummy()
                }
            } catch (e: Exception) {
                showError("네트워크 오류: ${e.message}")
                // 로컬 데이터 우선 표시, 없으면 더미 데이터
                showLocalDataOrDummy()
            }
        }
    }

    private fun updateStatistics(historyResponse: HistoryResponse) {
        val reliable = historyResponse.history.count { it.isReliable }
        val unreliable = historyResponse.history.size - reliable
        
        totalChecksText.text = historyResponse.history.size.toString()
        reliableCountText.text = reliable.toString()
        unreliableCountText.text = unreliable.toString()
    }

    // 중복 제거된 리스트로부터 통계 업데이트
    private fun updateStatisticsFromList(list: List<CheckHistory>) {
        val reliable = list.count { it.isReliable }
        val unreliable = list.size - reliable

        totalChecksText.text = list.size.toString()
        reliableCountText.text = reliable.toString()
        unreliableCountText.text = unreliable.toString()
    }
    
    private fun updateLocalStatistics(localHistory: List<CheckHistory>) {
        val reliable = localHistory.count { it.isReliable }
        val unreliable = localHistory.size - reliable
        
        totalChecksText.text = localHistory.size.toString()
        reliableCountText.text = reliable.toString()
        unreliableCountText.text = unreliable.toString()
        
        android.util.Log.d("MyPageActivity", "로컬 통계 업데이트: 총 ${localHistory.size}, 신뢰 $reliable, 불신뢰 $unreliable")
    }

    private fun showLocalDataOrDummy() {
        // 먼저 로컬 데이터 확인
        val localHistory = loadLocalHistory()
        
        if (localHistory.isNotEmpty()) {
            // 로컬 데이터가 있으면 로컬 데이터 사용
            android.util.Log.d("MyPageActivity", "로컬 데이터 사용: ${localHistory.size}개 항목")
            val dedupedLocal = localHistory.distinctBy { it.url ?: "__id_${it.id}" }
            updateLocalStatistics(dedupedLocal)
            val recentHistory = dedupedLocal.take(3)

            val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
            (recyclerView?.adapter as? HistoryAdapter)?.updateHistory(recentHistory)
        } else {
            // 로컬 데이터가 없으면 더미 데이터 표시
            android.util.Log.d("MyPageActivity", "로컬 데이터 없음, 더미 데이터 사용")
            showDummyData()
        }
    }

    private fun showDummyData() {
        val dummyHistory = listOf(
            CheckHistory(
                id = 1,
                title = "코로나19 백신 관련 뉴스",
                url = "https://news.example.com/covid19-vaccine",
                reliabilityScore = 85.5f,
                isReliable = true,
                checkedAt = "2024.11.14 15:30",
                type = "URL"
            ),
            CheckHistory(
                id = 2,
                title = "경제 정책 발표 관련",
                url = null,
                reliabilityScore = 42.3f,
                isReliable = false,
                checkedAt = "2024.11.13 09:15",
                type = "IMAGE"
            ),
            CheckHistory(
                id = 3,
                title = "스포츠 경기 결과",
                url = "https://sports.example.com/result",
                reliabilityScore = 91.2f,
                isReliable = true,
                checkedAt = "2024.11.12 20:45",
                type = "URL"
            )
        )
        
        // 안전하게 historyAdapter 업데이트
        val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
        (recyclerView?.adapter as? HistoryAdapter)?.updateHistory(dummyHistory)
        
        // 로컬 저장된 데이터 먼저 확인하여 병합하고 URL 기준으로 중복 제거
        val localHistory = loadLocalHistory()
        val combinedHistory = (localHistory + dummyHistory).distinctBy { it.url ?: "__id_${it.id}" }

        // 기존 recyclerView 변수 재사용
        (recyclerView?.adapter as? HistoryAdapter)?.updateHistory(combinedHistory)

        // 통계 업데이트 (중복 제거된 목록 기준)
        val reliable = combinedHistory.count { it.isReliable }
        val unreliable = combinedHistory.size - reliable

        totalChecksText.text = combinedHistory.size.toString()
        reliableCountText.text = reliable.toString()
        unreliableCountText.text = unreliable.toString()
    }
    
    private fun loadLocalHistory(): List<CheckHistory> {
        val currentUser = userManager.getCurrentUser()
        if (currentUser == null) {
            android.util.Log.w("MyPageActivity", "로그인된 사용자가 없어 히스토리를 로드할 수 없음")
            return emptyList()
        }
        
        // 사용자별 SharedPreferences 사용
        val userSpecificPrefsName = "veriscope_history_${currentUser.email}"
        val prefs = getSharedPreferences(userSpecificPrefsName, MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", "[]") ?: "[]"
        
        android.util.Log.d("MyPageActivity", "사용자 ${currentUser.email}의 로컬 히스토리 JSON: $historyJson")
        
        return try {
            val gson = com.google.gson.Gson()
            val localHistoryArray = gson.fromJson(historyJson, Array<CheckHistoryLocal>::class.java)
            
            android.util.Log.d("MyPageActivity", "사용자 ${currentUser.email}의 로컬 히스토리 개수: ${localHistoryArray?.size ?: 0}")
            
            // 로컬 히스토리를 CheckHistory로 변환
            localHistoryArray?.map { local ->
                android.util.Log.d("MyPageActivity", "히스토리 항목: ${local.title}, 점수: ${local.reliabilityScore}, 신뢰: ${local.isReliable}")
                CheckHistory(
                    id = local.id,
                    title = local.title,
                    type = local.type,
                    reliabilityScore = local.reliabilityScore,
                    isReliable = local.isReliable,
                    checkedAt = local.checkedAt,
                    url = local.url,
                    evidence = local.evidence // 근거 자료 포함
                )
            } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("MyPageActivity", "로컬 히스토리 로드 실패: ${e.message}")
            emptyList()
        }
    }

    private fun showHistoryDetail(history: CheckHistory) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("title", history.title)
            putExtra("reliability_score", history.reliabilityScore)
            putExtra("is_reliable", history.isReliable)
            putExtra("checked_at", history.checkedAt)
            putExtra("type", history.type)
            history.url?.let { putExtra("url", it) }
            // 근거 자료 포함
            putExtra("evidence", history.evidence ?: "[]")
            // 마이페이지의 최근 검사 내역에서 진입했음을 표시
            putExtra("FROM_HISTORY", true)
        }
        startActivity(intent)
    }

    private fun logout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("로그아웃")
            .setMessage("정말 로그아웃 하시겠습니까?")
            .setPositiveButton("로그아웃") { _, _ ->
                // UserManager를 통해 로그아웃 처리
                userManager.logout()
                
                Toast.makeText(this, "로그아웃되었습니다", Toast.LENGTH_SHORT).show()
                
                // 로그인 화면으로 이동
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun openContactUs() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@veriscope.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Veriscope 문의사항")
            putExtra(Intent.EXTRA_TEXT, "안녕하세요. Veriscope 관련 문의사항이 있습니다.\n\n")
        }
        
        try {
            startActivity(Intent.createChooser(intent, "이메일 앱 선택"))
        } catch (e: Exception) {
            Toast.makeText(this, "이메일 앱을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppInfo() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("앱 정보")
            .setMessage("""
                Veriscope - AI 뉴스 신뢰도 평가
                
                버전: 1.0.0
                개발: Smart IT Team
                
                AI 기반으로 뉴스와 정보의 신뢰도를 
                평가하여 가짜뉴스를 탐지합니다.
                
                사용된 AI 모델:
                • 텍스트 임베딩 (2개)
                • NLI 자연어 추론 (1개)  
                • OCR 광학문자인식 (2개)
                
                총 5개의 AI 모델로 정확한 분석을 제공합니다.
            """.trimIndent())
            .setPositiveButton("확인", null)
            .show()
    }

    private fun openReportPage() {
        val intent = Intent(this, ReportActivity::class.java)
        startActivity(intent)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showAccountManageDialog() {
        val options = arrayOf("신고 기록 초기화", "검사 이력 초기화", "모든 데이터 초기화", "계정 정보 확인")
        
        AlertDialog.Builder(this)
            .setTitle("계정 관리")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> clearReportHistory()
                    1 -> clearCheckHistory()
                    2 -> clearAllData()
                    3 -> showAccountInfo()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAccountInfo() {
        val userInfo = userManager.getCurrentUser()
        
        val message = if (userInfo != null) {
            "사용자: ${userInfo.name}\n이메일: ${userInfo.email}"
        } else {
            "로그인된 사용자 정보가 없습니다."
        }
        
        AlertDialog.Builder(this)
            .setTitle("계정 정보")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun clearReportHistory() {
        AlertDialog.Builder(this)
            .setTitle("신고 기록 초기화")
            .setMessage("모든 신고 기록을 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                val sharedPrefs = getSharedPreferences("veriscope_reports", Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .remove("reported_items")
                    .apply()
                
                Toast.makeText(this, "신고 기록이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun clearCheckHistory() {
        val currentUser = userManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "로그인된 사용자가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 사용자별 SharedPreferences 확인
        val userSpecificPrefsName = "veriscope_history_${currentUser.email}"
        val historyPrefs = getSharedPreferences(userSpecificPrefsName, Context.MODE_PRIVATE)
        val currentData = historyPrefs.getString("history_list", "[]")
        android.util.Log.d("MyPageActivity", "사용자 ${currentUser.email}의 현재 저장된 검사 이력: $currentData")
        
        AlertDialog.Builder(this)
            .setTitle("검사 이력 초기화")
            .setMessage("${currentUser.name}님의 모든 검사 이력을 삭제하시겠습니까?\n총 검사, 신뢰할 수 있음, 추의 필요 등의 모든 기록이 삭제됩니다.\n\n현재 ${loadLocalHistory().size}개의 기록이 있습니다.\n\n이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                try {
                    // 사용자별 검사 이력 SharedPreferences 초기화
                    historyPrefs.edit().clear().apply()
                    
                    // 기존의 전역 SharedPreferences도 초기화 (하위 호환성)
                    val legacyPrefs = getSharedPreferences("veriscope_history", Context.MODE_PRIVATE)
                    legacyPrefs.edit().clear().apply()
                    
                    android.util.Log.d("MyPageActivity", "사용자 ${currentUser.email}의 검사 이력 초기화 완료")
                    Toast.makeText(this, "${currentUser.name}님의 검사 이력이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
                    
                    // 페이지 새로고침을 위해 Activity 재시작
                    recreate()
                } catch (e: Exception) {
                    android.util.Log.e("MyPageActivity", "초기화 실패: ${e.message}")
                    Toast.makeText(this, "초기화 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun clearAllData() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 모든 데이터 초기화")
            .setMessage("다음 모든 데이터가 삭제됩니다:\n\n• 검사 이력 (총 검사, 신뢰도 등)\n• 신고 기록\n• 사용자 설정\n\n이 작업은 되돌릴 수 없습니다.\n정말로 진행하시겠습니까?")
            .setPositiveButton("모두 삭제") { _, _ ->
                try {
                    // 모든 SharedPreferences 초기화
                    val historyPrefs = getSharedPreferences("veriscope_history", Context.MODE_PRIVATE)
                    val reportsPrefs = getSharedPreferences("veriscope_reports", Context.MODE_PRIVATE)
                    
                    historyPrefs.edit().clear().apply()
                    reportsPrefs.edit().clear().apply()
                    
                    Toast.makeText(this, "모든 데이터가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
                    
                    // 페이지 새로고침
                    recreate()
                } catch (e: Exception) {
                    Toast.makeText(this, "초기화 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}