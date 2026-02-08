package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.veriscope.data.*
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etPasswordConfirm: TextInputEditText
    private lateinit var cbTerms: CheckBox
    private lateinit var cbPrivacy: CheckBox
    private lateinit var btnSignUp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        cbTerms = findViewById(R.id.cbTerms)
        cbPrivacy = findViewById(R.id.cbPrivacy)
        btnSignUp = findViewById(R.id.btnSignUp)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        // 이용약관 체크박스 클릭 시 약관 페이지 표시
        cbTerms.setOnClickListener {
            if (cbTerms.isChecked) {
                // 체크 해제하고 약관 페이지 표시
                cbTerms.isChecked = false
                val intent = Intent(this, TermsActivity::class.java)
                intent.putExtra("agreement_type", "terms")
                startActivityForResult(intent, REQUEST_TERMS_AGREEMENT)
            }
        }

        // 개인정보 처리방침 체크박스 클릭 시 개인정보 페이지 표시
        cbPrivacy.setOnClickListener {
            if (cbPrivacy.isChecked) {
                // 체크 해제하고 개인정보 페이지 표시
                cbPrivacy.isChecked = false
                val intent = Intent(this, TermsActivity::class.java)
                intent.putExtra("agreement_type", "privacy")
                startActivityForResult(intent, REQUEST_PRIVACY_AGREEMENT)
            }
        }

        btnSignUp.setOnClickListener {
            handleSignUp()
        }
    }

    companion object {
        private const val REQUEST_TERMS_AGREEMENT = 1001
        private const val REQUEST_PRIVACY_AGREEMENT = 1002
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_TERMS_AGREEMENT -> {
                    // 이용약관 동의 완료
                    cbTerms.isChecked = true
                }
                REQUEST_PRIVACY_AGREEMENT -> {
                    // 개인정보 처리방침 동의 완료
                    cbPrivacy.isChecked = true
                }
            }
        }
    }

    private fun handleSignUp() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val passwordConfirm = etPasswordConfirm.text.toString().trim()

        if (validateInput(name, email, phone, password, passwordConfirm)) {
            performSignUp(name, email, phone, password)
        }
    }
    
    private fun performSignUp(name: String, email: String, phone: String, password: String) {
        // 로딩 상태 표시
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                val request = SignUpRequest(name, email, phone, password)
                val response = ApiClient.apiService.signUp(request)
                
                println("DEBUG: Response isSuccessful: ${response.isSuccessful}")
                println("DEBUG: Response code: ${response.code()}")
                println("DEBUG: Response body: ${response.body()}")
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    println("DEBUG: API Response success: ${apiResponse?.success}")
                    
                    if (apiResponse?.success == true) {
                        println("DEBUG: 회원가입 성공! 인증 화면으로 이동 중...")
                        
                        // 메인 스레드에서 UI 업데이트 보장
                        runOnUiThread {
                            // 회원가입 성공 시 무조건 이메일 인증 화면으로 이동
                            Toast.makeText(this@SignUpActivity, 
                                "회원가입 정보가 등록되었습니다.\n이메일 인증을 완료해주세요.", Toast.LENGTH_LONG).show()
                            
                            // 이메일 인증 화면으로 이동
                            val intent = Intent(this@SignUpActivity, EmailVerificationActivity::class.java)
                            intent.putExtra("email", email)
                            intent.putExtra("user_name", name)  // 이름도 전달
                            println("DEBUG: Intent 생성됨. 이메일: $email")
                            println("DEBUG: startActivity 호출 중...")
                            
                            try {
                                startActivity(intent)
                                println("DEBUG: startActivity 성공!")
                                finish()
                                println("DEBUG: finish() 호출됨")
                            } catch (e: Exception) {
                                println("DEBUG: startActivity 실패: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    } else {
                        println("DEBUG: API 응답 실패: ${apiResponse?.message}")
                        showError(apiResponse?.message ?: "회원가입에 실패했습니다.")
                    }
                } else {
                    println("DEBUG: HTTP 응답 실패: ${response.code()}")
                    showError("서버 오류가 발생했습니다.")
                }
            } catch (e: Exception) {
                println("DEBUG: Exception 발생: ${e.message}")
                e.printStackTrace()
                showError("네트워크 오류: ${e.message}")
            } finally {
                println("DEBUG: API 호출 완료")
                setLoadingState(false)
            }
        }
    }
    
    private fun setLoadingState(loading: Boolean) {
        btnSignUp.isEnabled = !loading
        btnSignUp.text = if (loading) "가입 중..." else "회원가입"
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun validateInput(name: String, email: String, phone: String, password: String, passwordConfirm: String): Boolean {
        if (name.isEmpty()) {
            etName.error = "이름을 입력해주세요"
            etName.requestFocus()
            return false
        }

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

        if (phone.isEmpty()) {
            etPhone.error = "전화번호를 입력해주세요"
            etPhone.requestFocus()
            return false
        }

        if (!isValidPhone(phone)) {
            etPhone.error = "올바른 전화번호 형식을 입력해주세요 (010-1234-5678)"
            etPhone.requestFocus()
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

        if (passwordConfirm.isEmpty()) {
            etPasswordConfirm.error = "비밀번호 확인을 입력해주세요"
            etPasswordConfirm.requestFocus()
            return false
        }

        if (password != passwordConfirm) {
            etPasswordConfirm.error = "비밀번호가 일치하지 않습니다"
            etPasswordConfirm.requestFocus()
            return false
        }

        if (!cbTerms.isChecked) {
            Toast.makeText(this, "이용약관에 동의해주세요", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!cbPrivacy.isChecked) {
            Toast.makeText(this, "개인정보 처리방침에 동의해주세요", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun isValidPhone(phone: String): Boolean {
        // 010-1234-5678 또는 01012345678 형식 허용
        val phoneRegex = Regex("^010-?\\d{4}-?\\d{4}$")
        return phoneRegex.matches(phone)
    }
}