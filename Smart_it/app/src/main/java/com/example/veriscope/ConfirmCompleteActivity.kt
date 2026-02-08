package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ConfirmCompleteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_complete)

        val rewardPoints = intent.getIntExtra("REWARD_POINTS", 5)
        val reliabilityScore = intent.getFloatExtra("RELIABILITY_SCORE", 100f)

        val rewardCard = findViewById<LinearLayout>(R.id.rewardCard)
        val rewardPointsText = findViewById<TextView>(R.id.rewardPointsText)
        val reportButton = findViewById<Button>(R.id.reportButton)
        val goHomeButton = findViewById<Button>(R.id.goHomeButton)

        // 포인트 출력
        rewardPointsText.text = "${rewardPoints} P"
        
        // 자동으로 포인트 적립 (버튼 클릭 없이)
        addPointsToTotal(rewardPoints)

        // 신뢰도 70% 미만일 때 재보 버튼 표시
        if (reliabilityScore < 70f) {
            reportButton.visibility = android.view.View.VISIBLE
        }

        // 카드 클릭 시 리워드 상세 페이지 이동 (포인트는 이미 적립됨)
        rewardCard.setOnClickListener {
            val intent = Intent(this, RewardActivity::class.java)
            intent.putExtra("REWARD_POINTS", rewardPoints)
            startActivity(intent)
        }

        // 재보 버튼 클릭 시 ReportFormActivity로 이동
        reportButton.setOnClickListener {
            val reportIntent = Intent(this, ReportFormActivity::class.java)
            
            // 검사 타입에 따른 데이터 전달
            val checkType = intent.getStringExtra("CHECK_TYPE") ?: "URL"
            reportIntent.putExtra("REPORT_TYPE", checkType)
            
            if (checkType == "IMAGE") {
                // 이미지 검사에서 온 경우 - 저장된 이미지 경로 전달
                val savedImagePath = intent.getStringExtra("SAVED_IMAGE_PATH") ?: ""
                val originalImageUri = intent.getStringExtra("ORIGINAL_IMAGE_URI") ?: ""
                reportIntent.putExtra("SAVED_IMAGE_PATH", savedImagePath)
                reportIntent.putExtra("REPORT_IMAGE", originalImageUri)
                android.util.Log.d("ConfirmComplete", "이미지 경로 전달: $savedImagePath")
            } else {
                // URL 검사에서 온 경우 - 검사한 URL을 자동으로 입력
                val checkedUrl = intent.getStringExtra("CHECKED_URL") ?: ""
                reportIntent.putExtra("REPORT_URL", checkedUrl)
                android.util.Log.d("ConfirmComplete", "URL 자동 입력: $checkedUrl")
            }
            
            startActivity(reportIntent)
        }

        // 홈으로 가기
        goHomeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
    
    private fun addPointsToTotal(points: Int) {
        // 일일 검사 포인트 제한 확인 (최대 50P)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val todayCheckPoints = getTodayCheckPoints()
        
        val actualPoints = if (todayCheckPoints + points > 50) {
            // 일일 제한을 초과하는 경우 남은 포인트만 지급
            val remainingPoints = 50 - todayCheckPoints
            if (remainingPoints > 0) remainingPoints else 0
        } else {
            points
        }
        
        if (actualPoints > 0) {
            val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
            val currentPoints = prefs.getInt("total_points", 0)
            val newTotal = currentPoints + actualPoints
            prefs.edit().putInt("total_points", newTotal).apply()
            
            // 포인트 내역에 추가
            addPointHistory(getPointType(), actualPoints)
            
            android.util.Log.d("ConfirmComplete", "포인트 적립: $actualPoints, 총 포인트: $newTotal")
            
            // 일일 제한에 도달한 경우 알림
            if (actualPoints < points) {
                showDailyLimitMessage(todayCheckPoints + actualPoints)
            }
        } else {
            // 일일 제한 도달 알림
            showDailyLimitMessage(todayCheckPoints)
        }
    }
    
    private fun getPointType(): String {
        val checkType = intent.getStringExtra("CHECK_TYPE") ?: "URL"
        return if (checkType == "IMAGE") "image_check" else "url_check"
    }
    
    private fun addPointHistory(type: String, points: Int) {
        try {
            val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
            val historyJson = prefs.getString("point_history", "[]")
            val historyArray = org.json.JSONArray(historyJson)
            
            val newItem = org.json.JSONObject().apply {
                put("type", type)
                put("points", points)
                put("timestamp", System.currentTimeMillis())
            }
            
            historyArray.put(newItem)
            prefs.edit().putString("point_history", historyArray.toString()).apply()
            
        } catch (e: Exception) {
            android.util.Log.e("ConfirmComplete", "포인트 내역 저장 실패: ${e.message}")
        }
    }
    
    private fun getTodayCheckPoints(): Int {
        try {
            val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
            val historyJson = prefs.getString("point_history", "[]") ?: "[]"
            val historyArray = org.json.JSONArray(historyJson)
            
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val todayStart = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(today)?.time ?: 0L
            val todayEnd = todayStart + 24 * 60 * 60 * 1000L // 다음날 00:00
            
            var todayCheckPoints = 0
            
            for (i in 0 until historyArray.length()) {
                val item = historyArray.getJSONObject(i)
                val timestamp = item.getLong("timestamp")
                val type = item.getString("type")
                val points = item.getInt("points")
                
                // 오늘 날짜의 검사 포인트만 계산 (url_check, image_check)
                if (timestamp >= todayStart && timestamp < todayEnd && 
                    (type == "url_check" || type == "image_check") && points > 0) {
                    todayCheckPoints += points
                }
            }
            
            return todayCheckPoints
            
        } catch (e: Exception) {
            android.util.Log.e("ConfirmComplete", "오늘 포인트 계산 실패: ${e.message}")
            return 0
        }
    }
    
    private fun showDailyLimitMessage(currentDailyPoints: Int) {
        val message = if (currentDailyPoints >= 50) {
            "오늘 검사 포인트 한도(50P)에 도달했습니다.\n내일 다시 검사하여 포인트를 획득하세요!"
        } else {
            "오늘 남은 검사 포인트: ${50 - currentDailyPoints}P\n(일일 최대 50P)"
        }
        
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
