package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.veriscope.data.*
import kotlinx.coroutines.launch

class EmailVerificationActivity : AppCompatActivity() {
    
    private lateinit var etVerificationCode: EditText
    private lateinit var tvError: TextView
    private lateinit var btnVerify: Button
    private lateinit var btnResend: Button
    private lateinit var tvEmail: TextView
    private lateinit var tvTimer: TextView
    private lateinit var progressBar: ProgressBar
    
    private var userEmail: String = ""
    private var actionType: String = "" // "signup" or "change_email"
    private var currentEmail: String = ""
    private var resendTimer: CountDownTimer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_verification)
        
        println("DEBUG: EmailVerificationActivity 시작됨!")
        
        // Intent에서 정보 받기
        userEmail = intent.getStringExtra("email") ?: ""
        val userName = intent.getStringExtra("user_name") ?: ""
        actionType = intent.getStringExtra("action_type") ?: "signup"
        currentEmail = intent.getStringExtra("current_email") ?: ""
        
        println("DEBUG: 받은 이메일: $userEmail")
        println("DEBUG: 받은 이름: $userName")
        println("DEBUG: 액션 타입: $actionType")
        
        if (userEmail.isEmpty()) {
            println("DEBUG: 이메일 정보가 없음!")
            Toast.makeText(this, "이메일 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        initViews()
        setupViews()
        setupListeners()
        startResendTimer()
        
        println("DEBUG: EmailVerificationActivity 초기화 완료")
    }
    
    private fun initViews() {
        etVerificationCode = findViewById(R.id.et_verification_code)
        tvError = findViewById(R.id.tv_error)
        btnVerify = findViewById(R.id.btn_verify)
        btnResend = findViewById(R.id.btn_resend)
        tvEmail = findViewById(R.id.tv_email)
        tvTimer = findViewById(R.id.tv_timer)
        progressBar = findViewById(R.id.progress_bar)
    }
    
    private fun setupViews() {
        tvEmail.text = userEmail
        btnResend.isEnabled = false
        
        // 인증 코드 입력 시 자동으로 버튼 활성화
        etVerificationCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnVerify.isEnabled = s?.length == 6
            }
        })
    }
    
    private fun setupListeners() {
        btnVerify.setOnClickListener {
            val code = etVerificationCode.text.toString().trim()
            when {
                code.isEmpty() -> showError("인증 코드를 입력해주세요.")
                code.length < 6 -> showError("인증 코드 6자리를 모두 입력해주세요.")
                code.length > 6 -> showError("인증 코드는 6자리입니다.")
                !code.all { it.isDigit() } -> showError("인증 코드는 숫자만 입력해주세요.")
                else -> verifyEmail(code)
            }
        }
        
        btnResend.setOnClickListener {
            resendVerificationCode()
        }
        
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }
    
    private fun verifyEmail(code: String) {
        showLoading(true)
        tvError.visibility = android.view.View.GONE
        
        // 데모 환경에서는 간단한 코드 검증으로 처리
        if (code == "123456") {
            // 성공적인 인증
            showLoading(false)
            handleVerificationSuccess()
        } else {
            // 실제 API 호출 시도
            lifecycleScope.launch {
                try {
                    val request = VerifyEmailRequest(userEmail, code)
                    val response = ApiClient.apiService.verifyEmail(request)
                    
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.success == true) {
                            handleVerificationSuccess()
                        } else {
                            showError(apiResponse?.message ?: "인증에 실패했습니다.")
                        }
                    } else {
                        // API 실패 시 데모 안내 메시지
                        showError("인증 코드가 올바르지 않습니다.\n(데모용 코드: 123456)")
                    }
                } catch (e: Exception) {
                    // 네트워크 오류 시 데모 안내
                    showError("인증 코드가 올바르지 않습니다.\n(데모용 코드: 123456)")
                } finally {
                    showLoading(false)
                }
            }
        }
    }
    
    private fun resendVerificationCode() {
        showLoading(true)
        btnResend.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val request = ResendVerificationRequest(userEmail)
                val response = ApiClient.apiService.resendVerification(request)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true) {
                        Toast.makeText(this@EmailVerificationActivity, 
                            "인증 코드를 재발송했습니다.", Toast.LENGTH_SHORT).show()
                        startResendTimer()
                    } else {
                        showError(apiResponse?.message ?: "재발송에 실패했습니다.")
                        btnResend.isEnabled = true
                    }
                } else {
                    // 서버에서 상세한 오류 메시지가 있다면 그것을 사용
                    val errorBody = response.errorBody()?.string()
                    if (errorBody != null) {
                        try {
                            val errorJson = com.google.gson.Gson().fromJson(errorBody, 
                                com.example.veriscope.data.ApiResponse::class.java)
                            showError(errorJson.message ?: "재발송에 실패했습니다.")
                        } catch (e: Exception) {
                            showError("서버 오류가 발생했습니다.")
                        }
                    } else {
                        showError("서버 오류가 발생했습니다.")
                    }
                    btnResend.isEnabled = true
                }
            } catch (e: Exception) {
                showError("네트워크 오류: ${e.message}")
                btnResend.isEnabled = true
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun startResendTimer() {
        resendTimer?.cancel()
        
        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvTimer.text = "${seconds}초 후 재발송 가능"
                btnResend.isEnabled = false
            }
            
            override fun onFinish() {
                tvTimer.text = ""
                btnResend.isEnabled = true
            }
        }.start()
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        btnVerify.isEnabled = !show && etVerificationCode.text?.length == 6
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = android.view.View.VISIBLE
    }
    
    private fun handleVerificationSuccess() {
        if (actionType == "change_email") {
            // 이메일 변경 완료
            Toast.makeText(this, "✅ 이메일 인증이 완료되었습니다!", Toast.LENGTH_LONG).show()
            
            // 결과를 반환하여 AccountManagementActivity에서 처리하도록 함
            val resultIntent = Intent()
            resultIntent.putExtra("verified_email", userEmail)
            setResult(RESULT_OK, resultIntent)
            finish()
        } else {
            // 회원가입 완료 (기존 로직)
            Toast.makeText(this, "🎉 회원가입이 완료되었습니다!\n이제 로그인할 수 있습니다.", Toast.LENGTH_LONG).show()
            
            // 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("verified_email", userEmail)
            intent.putExtra("show_success_message", true)
            startActivity(intent)
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}