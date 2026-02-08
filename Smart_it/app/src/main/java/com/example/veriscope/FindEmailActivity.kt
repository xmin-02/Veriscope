package com.example.veriscope

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.veriscope.data.ApiClient
import com.example.veriscope.utils.UserManager
import kotlinx.coroutines.launch

class FindEmailActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnFindEmail: Button
    private lateinit var tvResult: TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultIcon: TextView
    private lateinit var layoutResult: LinearLayout
    private lateinit var cardResult: androidx.cardview.widget.CardView
    private lateinit var btnBack: ImageButton
    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_email)

        userManager = UserManager(this)
        initViews()
        setupListeners()
    }

    private fun initViews() {
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        btnFindEmail = findViewById(R.id.btnFindEmail)
        tvResult = findViewById(R.id.tvResult)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultIcon = findViewById(R.id.tvResultIcon)
        layoutResult = findViewById(R.id.layoutResult)
        cardResult = findViewById(R.id.cardResult)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnFindEmail.setOnClickListener {
            findEmail()
        }
    }

    private fun findEmail() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "이름을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            Toast.makeText(this, "전화번호를 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        // 전화번호 형식 검증
        if (!isValidPhone(phone)) {
            Toast.makeText(this, "올바른 전화번호 형식을 입력해주세요 (010-1234-5678)", Toast.LENGTH_LONG).show()
            return
        }

        btnFindEmail.isEnabled = false
        btnFindEmail.text = "이메일 찾는 중..."

        lifecycleScope.launch {
            try {
                android.util.Log.d("FindEmail", "이메일 찾기 시작 - name: $name, phone: $phone")
                
                val apiClient = ApiClient.getInstance()
                val request = mapOf(
                    "name" to name,
                    "phone" to phone
                )
                
                android.util.Log.d("FindEmail", "API 요청 전송 중...")
                val response = apiClient.findEmail(request)
                
                android.util.Log.d("FindEmail", "이메일 찾기 응답 - success: ${response.success}")
                android.util.Log.d("FindEmail", "이메일 찾기 응답 - message: ${response.message}")
                android.util.Log.d("FindEmail", "이메일 찾기 응답 - data: ${response.data}")
                
                // UI 업데이트는 메인 스레드에서 실행
                runOnUiThread {
                    if (response.success) {
                        val foundEmail = response.data?.get("full_email") ?: response.data?.get("email") ?: "알 수 없음"
                        val maskedEmail = response.data?.get("email") ?: foundEmail
                        
                        android.util.Log.d("FindEmail", "UI 업데이트 시작 - maskedEmail: $maskedEmail")
                        
                        // 성공 시 결과 표시
                        showResult(
                            icon = "🎉",
                            title = "이메일을 찾았어요!",
                            message = "📧 $maskedEmail",
                            type = ResultType.SUCCESS
                        )
                        
                        android.util.Log.d("FindEmail", "결과 표시 완료 - tvResult visibility: ${tvResult.visibility}")
                        android.util.Log.d("FindEmail", "결과 표시 완료 - layoutResult visibility: ${layoutResult.visibility}")
                        android.util.Log.d("FindEmail", "결과 텍스트: ${tvResult.text}")
                        
                        Toast.makeText(this@FindEmailActivity, "이메일을 찾았어요! 🎉", Toast.LENGTH_SHORT).show()
                    } else {
                        android.util.Log.d("FindEmail", "실패 처리 - ${response.message}")
                        
                        // 실패 시 결과 표시
                        showResult(
                            icon = "🔍",
                            title = "일치하는 정보를 찾을 수 없어요",
                            message = "입력하신 이름과 전화번호를\n다시 한번 확인해주세요",
                            type = ResultType.INFO
                        )
                        
                        Toast.makeText(this@FindEmailActivity, 
                            "일치하는 정보를 찾을 수 없어요", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FindEmail", "예외 발생", e)
                
                // 오류 시 결과 영역에 메시지 표시
                runOnUiThread {
                    showResult(
                        icon = "📶",
                        title = "연결에 문제가 있어요",
                        message = "인터넷 연결을 확인하고\n잠시 후 다시 시도해주세요",
                        type = ResultType.WARNING
                    )
                    
                    Toast.makeText(this@FindEmailActivity, 
                        "연결에 문제가 있어요", 
                        Toast.LENGTH_SHORT).show()
                }
            } finally {
                btnFindEmail.isEnabled = true
                btnFindEmail.text = "이메일 찾기"
            }
        }
    }

    private fun isValidPhone(phone: String): Boolean {
        // 010-1234-5678 또는 01012345678 형식 허용
        val phoneRegex = Regex("^010-?\\d{4}-?\\d{4}$")
        return phoneRegex.matches(phone)
    }

    private enum class ResultType {
        SUCCESS, INFO, WARNING
    }

    private fun showResult(icon: String, title: String, message: String, type: ResultType) {
        tvResultIcon.text = icon
        tvResultTitle.text = title
        tvResult.text = message

        val (bgColor, textColor) = when (type) {
            ResultType.SUCCESS -> Pair(R.color.result_success_bg, R.color.result_success_text)
            ResultType.INFO -> Pair(R.color.result_info_bg, R.color.result_info_text)
            ResultType.WARNING -> Pair(R.color.result_warning_bg, R.color.result_warning_text)
        }

        layoutResult.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, bgColor))
        tvResultTitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, textColor))
        tvResult.setTextColor(androidx.core.content.ContextCompat.getColor(this, textColor))

        // 부드러운 애니메이션으로 표시
        cardResult.alpha = 0f
        cardResult.visibility = android.view.View.VISIBLE
        cardResult.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
}