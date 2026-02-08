package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class VoucherActivity : AppCompatActivity() {
    
    private lateinit var btnBack: ImageView
    private lateinit var btnHelp: ImageView
    private lateinit var currentPointsText: TextView
    private lateinit var voucherContainer: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voucher)
        
        initViews()
        setupClickListeners()
        loadUserPoints()
        setupVoucherOptions()
    }
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnHelp = findViewById(R.id.btnHelp)
        currentPointsText = findViewById(R.id.currentPointsText)
        voucherContainer = findViewById(R.id.voucherContainer)
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        btnHelp.setOnClickListener {
            showHelpDialog()
        }
    }
    
    private fun loadUserPoints() {
        val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
        val totalPoints = prefs.getInt("total_points", 0)
        currentPointsText.text = "${totalPoints} P"
    }
    
    private fun setupVoucherOptions() {
        voucherContainer.removeAllViews()
        
        val voucherOptions = listOf(
            VoucherOption("1,000원", 1000, 1000),
            VoucherOption("3,000원", 3000, 3000),
            VoucherOption("5,000원", 5000, 5000),
            VoucherOption("10,000원", 10000, 10000)
        )
        
        for (option in voucherOptions) {
            val optionView = createVoucherOptionView(option)
            voucherContainer.addView(optionView)
        }
    }
    
    private fun createVoucherOptionView(option: VoucherOption): android.view.View {
        val itemLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(48, 40, 48, 40)
            background = getDrawable(R.drawable.rounded_card)
            elevation = 8f
            isClickable = true
            isFocusable = true
        }
        
        // 상품권 아이콘
        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                marginEnd = 32
            }
            setImageResource(R.drawable.ic_ticket)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary_blue))
        }
        
        // 텍스트 컨테이너
        val textContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }
        
        val titleText = TextView(this).apply {
            text = option.name
            textSize = 16f
            setTextColor(getColor(android.R.color.black))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        val pointText = TextView(this).apply {
            text = "${option.requiredPoints} P 필요"
            textSize = 14f
            setTextColor(getColor(android.R.color.darker_gray))
        }
        
        textContainer.addView(titleText)
        textContainer.addView(pointText)
        
        // 교환 버튼
        val exchangeButton = Button(this).apply {
            text = "교환하기"
            textSize = 14f
            setTextColor(getColor(android.R.color.white))
            background = getDrawable(R.drawable.button_primary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 16, 32, 16)
        }
        
        // 사용자 포인트 확인 후 버튼 활성화/비활성화
        val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
        val userPoints = prefs.getInt("total_points", 0)
        
        if (userPoints >= option.requiredPoints) {
            exchangeButton.isEnabled = true
            exchangeButton.alpha = 1.0f
            exchangeButton.background = getDrawable(R.drawable.button_primary)
            exchangeButton.setTextColor(getColor(android.R.color.white))
        } else {
            exchangeButton.isEnabled = false
            exchangeButton.alpha = 1.0f
            exchangeButton.text = "부족"
            exchangeButton.background = getDrawable(R.drawable.button_outline)
            exchangeButton.setTextColor(getColor(R.color.primary_blue))
        }
        
        exchangeButton.setOnClickListener {
            if (userPoints >= option.requiredPoints) {
                showExchangeConfirmDialog(option)
            }
        }
        
        itemLayout.addView(iconView)
        itemLayout.addView(textContainer)
        itemLayout.addView(exchangeButton)
        
        return itemLayout
    }
    
    private fun showExchangeConfirmDialog(option: VoucherOption) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("온누리 상품권 교환 확인")
            .setMessage("${option.requiredPoints}P로 ${option.name}을(를) 교환하시겠습니까?\n\n• 수수료: 무료\n• 전국 온누리 가맹점에서 사용 가능")
            .setPositiveButton("교환하기") { _, _ ->
                performExchange(option)
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    private fun performExchange(option: VoucherOption) {
        val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
        val currentPoints = prefs.getInt("total_points", 0)
        
        if (currentPoints >= option.requiredPoints) {
            // 포인트 차감
            val newPoints = currentPoints - option.requiredPoints
            prefs.edit().putInt("total_points", newPoints).apply()
            
            // 포인트 사용 내역 추가
            addPointUsageHistory(option)
            
            // UI 업데이트
            loadUserPoints()
            setupVoucherOptions()
            
            // 성공 메시지
            Toast.makeText(this, "${option.name} 교환이 완료되었습니다!", Toast.LENGTH_LONG).show()
            
            // 교환 완료 다이얼로그 표시
            showExchangeCompleteDialog(option)
        } else {
            Toast.makeText(this, "포인트가 부족합니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addPointUsageHistory(option: VoucherOption) {
        try {
            val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
            val historyJson = prefs.getString("point_history", "[]") ?: "[]"
            val historyArray = org.json.JSONArray(historyJson)
            
            val usageItem = org.json.JSONObject().apply {
                put("type", "use")
                put("points", -option.requiredPoints)
                put("timestamp", System.currentTimeMillis())
                put("description", option.name)
            }
            
            historyArray.put(usageItem)
            prefs.edit().putString("point_history", historyArray.toString()).apply()
            
        } catch (e: Exception) {
            android.util.Log.e("VoucherActivity", "포인트 사용 내역 추가 실패: ${e.message}")
        }
    }
    
    private fun showExchangeCompleteDialog(option: VoucherOption) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("온누리 상품권 교환 완료")
            .setMessage("${option.name} 교환이 완료되었습니다!\n\n 온누리 디지털 상품권 정보가\n    등록된 이메일로 전송됩니다.\n\n 전송 소요 시간: 영업일 1-2일\n 전국 온누리 가맹점에서 사용 가능")
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showHelpDialog() {
        val helpContent = """
            <div style="text-align: center;"><h2><b>온누리 상품권 교환 안내</b></h2></div><br/>
            
            🎫 교환 가능한 상품권:<br/>
            • 온누리 디지털 상품권 (1,000원 ~ 10,000원)<br/>
            • 1포인트 = 1원 (수수료 없음)<br/>
            • 최소 교환: 1,000원부터<br/><br/>
            
            🏪 온누리 상품권 사용처:<br/>
            • 전국 온누리 상품권 가맹점에서 사용 가능<br/>
            • 편의점, 마트, 약국, 주유소 등<br/>
            • 온라인 쇼핑몰에서도 사용 가능<br/><br/>
            
            📱 교환 프로세스:<br/>
            • 포인트 → 온누리 디지털 상품권 교환<br/>
            • 이메일로 상품권 번호 및 PIN 번호 발송<br/>
            • 가맹점에서 상품권으로 결제<br/><br/>
            
            ⏰ 처리 시간:<br/>
            • 상품권 발송: 영업일 기준 1-2일<br/>
            • 상품권 유효기간: 5년<br/>
            • 부분 사용 가능, 잔액 이월<br/><br/>
            
            ⚠️ 주의사항:<br/>
            • 교환 후 포인트 환불 불가<br/>
            • 상품권 정보 분실 시 재발급 불가<br/>
            • 상품권 번호는 안전하게 보관하세요
        """.trimIndent()
        
        val spannedContent = android.text.Html.fromHtml(helpContent, android.text.Html.FROM_HTML_MODE_LEGACY)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(spannedContent)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    data class VoucherOption(
        val name: String,
        val value: Int,
        val requiredPoints: Int
    )
}