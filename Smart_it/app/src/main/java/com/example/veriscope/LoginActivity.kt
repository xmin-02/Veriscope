package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.example.veriscope.data.ApiClient
import com.example.veriscope.data.LoginRequest
import com.example.veriscope.utils.UserManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var tvSignUp: TextView
    private lateinit var tvFindEmail: TextView
    private lateinit var tvForgotPassword: TextView
    private lateinit var cbRememberEmail: CheckBox
    private lateinit var cbAutoLogin: CheckBox
    private lateinit var btnBack: ImageButton
    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        userManager = UserManager(this)
        
        initViews()
        setupClickListeners()
        loadSavedSettings()
        
        // 회원가입 완료 후 온 경우 이메일 설정
        intent.getStringExtra("verified_email")?.let { email ->
            etEmail.setText(email)
            cbRememberEmail.isChecked = true
        }
        
        // 자동 로그인이 활성화된 경우에만 자동 로그인 시도
        if (userManager.isLoggedIn() && userManager.isAutoLoginEnabled()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignUp = findViewById(R.id.tvSignUp)
        tvFindEmail = findViewById(R.id.tvFindEmail)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        cbRememberEmail = findViewById(R.id.cbRememberEmail)
        cbAutoLogin = findViewById(R.id.cbAutoLogin)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            handleLogin()
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        tvFindEmail.setOnClickListener {
            startActivity(Intent(this, FindEmailActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun loadSavedSettings() {
        // 이메일 기억 설정 로드
        if (userManager.isRememberEmailEnabled()) {
            cbRememberEmail.isChecked = true
            etEmail.setText(userManager.getRememberedEmail())
        }
        
        // 자동 로그인 설정 로드
        cbAutoLogin.isChecked = userManager.isAutoLoginEnabled()
    }

    private fun handleLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (validateInput(email, password)) {
            // 로딩 상태 표시
            btnLogin.isEnabled = false
            btnLogin.text = "로그인 중..."
            
            // 임시로 데모 계정 사용 (실제 API 연동 전까지)
            if (email == "demo@example.com" && password == "demo123") {
                // 사용자 설정 저장
                saveUserSettings(email)
                
                // 임시 사용자 정보 저장
                val demoUser = com.example.veriscope.data.User(1, "데모 사용자", email, "")
                userManager.saveUser(demoUser)
                
                Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                // 실제 API 호출 (서버 구축 후 활성화)
                performApiLogin(email, password)
            }
        }
    }

    private fun performApiLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.login(LoginRequest(email, password))
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true) {
                        val user = apiResponse.data
                        if (user != null) {
                            // 사용자 설정 저장
                            saveUserSettings(user.email)
                            
                            userManager.saveUser(user)
                            Toast.makeText(this@LoginActivity, "로그인 성공!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                    } else {
                        // 로그인 실패 처리
                        val errorMessage = apiResponse?.message ?: "로그인에 실패했습니다."
                        
                        // 이메일 인증이 필요한 경우
                        if (response.code() == 403 && apiResponse?.data?.verification_required == true) {
                            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                            
                            // 이메일 인증 화면으로 이동
                            val intent = Intent(this@LoginActivity, EmailVerificationActivity::class.java)
                            intent.putExtra("email", email)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "서버 오류가 발생했습니다.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_LONG).show()
            } finally {
                btnLogin.isEnabled = true
                btnLogin.text = "로그인"
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            etEmail.error = "이메일을 입력해주세요"
            etEmail.requestFocus()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "올바른 이메일 형식을 입력해주세요"
            etEmail.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            etPassword.error = "비밀번호를 입력해주세요"
            etPassword.requestFocus()
            return false
        }

        if (password.length < 6) {
            etPassword.error = "비밀번호는 6자 이상이어야 합니다"
            etPassword.requestFocus()
            return false
        }

        return true
    }

    private fun saveUserSettings(email: String) {
        // 이메일 기억 설정 저장
        if (cbRememberEmail.isChecked) {
            userManager.setRememberEmail(true, email)
        } else {
            userManager.setRememberEmail(false)
        }
        
        // 자동 로그인 설정 저장
        userManager.setAutoLogin(cbAutoLogin.isChecked)
    }
}