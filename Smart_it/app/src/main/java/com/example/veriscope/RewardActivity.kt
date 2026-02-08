package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class RewardActivity : AppCompatActivity() {
    
    private lateinit var btnBack: ImageView
    private lateinit var btnHelp: ImageView
    private lateinit var totalPointsText: TextView
    private lateinit var btnUsePoints: Button
    private lateinit var pointHistoryContainer: LinearLayout
    private lateinit var moreButton: TextView
    
    // 하단 탭들
    private lateinit var tabHome: LinearLayout
    private lateinit var tabReport: LinearLayout
    private lateinit var tabReward: LinearLayout
    private lateinit var tabProfile: LinearLayout
    

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reward)
        
        initViews()
        setupClickListeners()
        loadRewardData()
    }
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnHelp = findViewById(R.id.btnHelp)
        totalPointsText = findViewById(R.id.totalPointsText)
        btnUsePoints = findViewById(R.id.btnUsePoints)
        pointHistoryContainer = findViewById(R.id.pointHistoryContainer)
        moreButton = findViewById(R.id.moreButton)
        
        // 하단 탭들
        tabHome = findViewById(R.id.tabHome)
        tabReport = findViewById(R.id.tabReport)
        tabReward = findViewById(R.id.tabReward)
        tabProfile = findViewById(R.id.tabProfile)
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        btnHelp.setOnClickListener {
            showHelpDialog()
        }
        
        btnUsePoints.setOnClickListener {
            val intent = Intent(this, VoucherActivity::class.java)
            startActivity(intent)
        }
        
        moreButton.setOnClickListener {
            showFullHistoryDialog()
        }
        
        // 하단 탭 클릭 리스너들
        tabHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        
        tabReport.setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            startActivity(intent)
        }
        
        tabReward.setOnClickListener {
            // 현재 페이지이므로 아무것도 하지 않음
        }
        
        tabProfile.setOnClickListener {
            val intent = Intent(this, MyPageActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun loadRewardData() {
        // Intent에서 포인트 정보 받기
        val rewardPoints = intent.getIntExtra("REWARD_POINTS", 0)
        
        // SharedPreferences에서 총 포인트 정보 불러오기 (초기값 0)
        val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
        
        // 회원가입 여부 확인 (회원가입 시에만 0P로 초기화)
        val isNewUser = intent.getBooleanExtra("IS_NEW_USER", false)
        if (isNewUser) {
            // 회원가입한 새 사용자의 경우 포인트를 0으로 초기화
            prefs.edit()
                .putInt("total_points", 0)
                .putString("point_history", "[]")
                .apply()
        }
        
        // 새로운 리워드가 있다면 추가
        if (rewardPoints > 0) {
            Toast.makeText(this, "+${rewardPoints}P가 적립되었습니다!", Toast.LENGTH_SHORT).show()
        }
        
        // 포인트 내역 로딩
        loadPointHistory()
        
        // 실제 포인트 내역을 기반으로 총 포인트 계산
        calculateTotalPointsFromHistory()
    }
    
    private fun loadPointHistory() {
        val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
        val historyJson = prefs.getString("point_history", "[]")
        
        try {
            val historyArray = org.json.JSONArray(historyJson)
            val historyList = mutableListOf<PointHistoryItem>()
            
            for (i in 0 until historyArray.length()) {
                val item = historyArray.getJSONObject(i)
                historyList.add(
                    PointHistoryItem(
                        type = item.getString("type"),
                        points = item.getInt("points"),
                        timestamp = item.getLong("timestamp")
                    )
                )
            }
            
            // 2025-11-17 이후 데이터만 필터링 (예시 데이터 완전 제거)
            val cutoffDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse("2025-11-17")?.time ?: 0L
            val filteredList = historyList.filter { it.timestamp >= cutoffDate }
            
            // 필터링된 데이터를 다시 저장 (예시 데이터 영구 삭제)
            val filteredArray = org.json.JSONArray()
            for (item in filteredList) {
                val jsonItem = org.json.JSONObject().apply {
                    put("type", item.type)
                    put("points", item.points)
                    put("timestamp", item.timestamp)
                }
                filteredArray.put(jsonItem)
            }
            val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
            prefs.edit().putString("point_history", filteredArray.toString()).apply()
            
            // 최신순으로 정렬
            val sortedList = filteredList.sortedByDescending { it.timestamp }
            
            // UI 업데이트
            displayPointHistory(sortedList)
            
        } catch (e: Exception) {
            android.util.Log.e("RewardActivity", "포인트 내역 로딩 실패: ${e.message}")
        }
    }
    
    private fun displayPointHistory(historyList: List<PointHistoryItem>) {
        pointHistoryContainer.removeAllViews()
        
        // 항상 최근 5개만 표시
        val displayList = historyList.take(5)
        
        for (item in displayList) {
            val historyItemView = createHistoryItemView(item)
            pointHistoryContainer.addView(historyItemView)
        }
        
        // 더보기 버튼 항상 표시 (내역이 있을 때만)
        if (historyList.isNotEmpty()) {
            moreButton.visibility = android.view.View.VISIBLE
            moreButton.text = "더보기"
        } else {
            moreButton.visibility = android.view.View.GONE
        }
    }
    
    private fun createHistoryItemView(item: PointHistoryItem): android.view.View {
        val itemLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                180
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(32, 16, 32, 16)
        }
        
        // 아이콘 (마이페이지 스타일과 동일)
        val iconView = android.widget.ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                marginEnd = 24
            }
            setImageResource(getIconForType(item.type))
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            // 아이콘 색상을 primary_blue로 통일
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary_blue))
        }
        
        // 텍스트 컨테이너
        val textContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }
        
        val titleText = TextView(this).apply {
            text = getDisplayNameForType(item.type)
            textSize = 16f
            setTextColor(getColor(android.R.color.black))
        }
        
        val timeText = TextView(this).apply {
            text = formatTimestamp(item.timestamp)
            textSize = 12f
            setTextColor(getColor(android.R.color.darker_gray))
        }
        
        textContainer.addView(titleText)
        textContainer.addView(timeText)
        
        // 포인트 텍스트
        val pointText = TextView(this).apply {
            text = if (item.points > 0) "+${item.points} P" else "${item.points} P"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (item.points > 0) getColor(R.color.primary_blue) else getColor(R.color.red))
        }
        
        itemLayout.addView(iconView)
        itemLayout.addView(textContainer)
        itemLayout.addView(pointText)
        
        return itemLayout
    }
    
    private fun getIconForType(type: String): Int {
        return when (type) {
            "image_check" -> R.drawable.ic_image
            "url_check" -> R.drawable.ic_link
            "report" -> R.drawable.ic_info
            "use" -> R.drawable.ic_ticket
            else -> R.drawable.ic_veriscope
        }
    }
    
    private fun getDisplayNameForType(type: String): String {
        return when (type) {
            "image_check" -> "이미지 검사"
            "url_check" -> "URL 검사"
            "report" -> "제보하기"
            "use" -> "포인트 사용"
            else -> "기타"
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
    
    private fun showFullHistoryDialog() {
        val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
        val historyJson = prefs.getString("point_history", "[]")
        
        try {
            val historyArray = org.json.JSONArray(historyJson)
            val historyList = mutableListOf<PointHistoryItem>()
            
            for (i in 0 until historyArray.length()) {
                val item = historyArray.getJSONObject(i)
                historyList.add(
                    PointHistoryItem(
                        type = item.getString("type"),
                        points = item.getInt("points"),
                        timestamp = item.getLong("timestamp")
                    )
                )
            }
            
            // 2025-11-17 이후 데이터만 필터링 (예시 데이터 제거)
            val cutoffDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse("2025-11-17")?.time ?: 0L
            val filteredList = historyList.filter { it.timestamp >= cutoffDate }
            
            // 최신순으로 정렬
            val sortedDialogList = filteredList.sortedByDescending { it.timestamp }
            
            if (sortedDialogList.isEmpty()) {
                Toast.makeText(this, "포인트 내역이 없습니다", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 다이얼로그 생성
            val dialogView = layoutInflater.inflate(R.layout.dialog_point_history, null)
            val historyContainer = dialogView.findViewById<LinearLayout>(R.id.dialogHistoryContainer)
            
            // 전체 내역 표시 (필터링된 데이터)
            for (item in sortedDialogList) {
                val itemView = createDialogHistoryItemView(item)
                historyContainer.addView(itemView)
            }
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("전체 포인트 내역 (${sortedDialogList.size}개)")
                .setView(dialogView)
                .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                .show()
                
        } catch (e: Exception) {
            android.util.Log.e("RewardActivity", "전체 내역 표시 실패: ${e.message}")
            Toast.makeText(this, "내역을 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun createDialogHistoryItemView(item: PointHistoryItem): android.view.View {
        val itemLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 24)
        }
        
        // 아이콘 (마이페이지 스타일과 동일)
        val iconView = android.widget.ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(32, 32).apply {
                marginEnd = 16
            }
            setImageResource(getIconForType(item.type))
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            // 아이콘 색상을 primary_blue로 통일
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary_blue))
        }
        
        // 텍스트 컨테이너
        val textContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }
        
        val titleText = TextView(this).apply {
            text = getDisplayNameForType(item.type)
            textSize = 14f
            setTextColor(getColor(android.R.color.black))
        }
        
        val timeText = TextView(this).apply {
            text = formatTimestamp(item.timestamp)
            textSize = 11f
            setTextColor(getColor(android.R.color.darker_gray))
        }
        
        textContainer.addView(titleText)
        textContainer.addView(timeText)
        
        // 포인트 텍스트
        val pointText = TextView(this).apply {
            text = if (item.points > 0) "+${item.points} P" else "${item.points} P"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (item.points > 0) getColor(R.color.primary_blue) else getColor(R.color.red))
        }
        
        itemLayout.addView(iconView)
        itemLayout.addView(textContainer)
        itemLayout.addView(pointText)
        
        return itemLayout
    }
    
    data class PointHistoryItem(
        val type: String,
        val points: Int,
        val timestamp: Long
    )
    
    private fun calculateTotalPointsFromHistory() {
        try {
            val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
            val historyJson = prefs.getString("point_history", "[]") ?: "[]"
            val jsonArray = JSONArray(historyJson)
            
            var totalPoints = 0
            
            // 2025-11-17 이후 데이터만 계산 (예시 데이터 제외)
            val cutoffDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse("2025-11-17")?.time ?: 0L
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val timestamp = item.getLong("timestamp")
                
                if (timestamp >= cutoffDate) {
                    val points = item.getInt("points")
                    totalPoints += points
                }
            }
            
            // UI 업데이트
            totalPointsText.text = "${totalPoints} P"
            
            // SharedPreferences의 total_points도 실제 계산된 값으로 업데이트
            prefs.edit().putInt("total_points", totalPoints).apply()
            
        } catch (e: Exception) {
            e.printStackTrace()
            totalPointsText.text = "0 P"
        }
    }
    
    private fun showHelpDialog() {
        val helpContent = """
            <div style="text-align: center;"><h2><b>리워드 시스템 안내</b></h2></div><br/>
            
            🎁 포인트 적립 방법:<br/>
            • 뉴스 검사 완료: <font color='#2196F3'><b>+5P</b></font><br/>
            • 허위 뉴스 제보 완료: <font color='#2196F3'><b>+100P</b></font><br/>
            • 검사 포인트 일일 한도: <font color='#fc5230'><b>최대 50P</b></font><br/><br/>
            
            🎫 온누리 상품권 교환:<br/>
            • '온누리 상품권 교환하기' 버튼을 통해 이동<br/>
            • 온누리 디지털 상품권으로 교환 (수수료 무료)<br/>
            • 전국 온누리 가맹점에서 사용 가능<br/><br/>
            
            📊 포인트 내역:<br/>
            • 검사 및 제보 활동으로 얻은 포인트 내역 확인<br/>
            • 최근 5개 내역 표시, '더보기'로 전체 내역 조회<br/>
            • 온누리 상품권 교환 내역도 함께 확인 가능<br/><br/>
        """.trimIndent()
        
        val spannedContent: Spanned = Html.fromHtml(helpContent, Html.FROM_HTML_MODE_LEGACY)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(spannedContent)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
