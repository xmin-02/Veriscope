package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    
    // 1단계: 이메일 입력
    private lateinit var layoutEmailStep: LinearLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSendCode: Button
    
    // 2단계: 인증번호 입력
    private lateinit var layoutVerificationStep: LinearLayout
    private lateinit var etVerificationCode: TextInputEditText
    private lateinit var btnVerifyCode: Button
    private lateinit var btnResendCode: Button
    private lateinit var tvEmailAddress: TextView
    
    private lateinit var tvMessage: TextView
    
    private var userEmail: String = ""
    private var resetToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        initViews()
        setupClickListeners()
        showEmailStep()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        
        // 1단계
        layoutEmailStep = findViewById(R.id.layoutEmailStep)
        etEmail = findViewById(R.id.etEmail)
        btnSendCode = findViewById(R.id.btnSendCode)
        
        // 2단계
        layoutVerificationStep = findViewById(R.id.layoutVerificationStep)
        etVerificationCode = findViewById(R.id.etVerificationCode)
        btnVerifyCode = findViewById(R.id.btnVerifyCode)
        btnResendCode = findViewById(R.id.btnResendCode)
        tvEmailAddress = findViewById(R.id.tvEmailAddress)
        
        tvMessage = findViewById(R.id.tvMessage)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            if (layoutVerificationStep.visibility == View.VISIBLE) {
                showEmailStep()
            } else {
                finish()
            }
        }

        btnSendCode.setOnClickListener {
            handleSendVerificationCode()
        }
        
        btnVerifyCode.setOnClickListener {
            handleVerifyCode()
        }
        
        btnResendCode.setOnClickListener {
            handleSendVerificationCode()
        }
    }
    
    private fun showEmailStep() {
        layoutEmailStep.visibility = View.VISIBLE
        layoutVerificationStep.visibility = View.GONE
        tvMessage.visibility = View.GONE
    }
    
    private fun showVerificationStep() {
        layoutEmailStep.visibility = View.GONE
        layoutVerificationStep.visibility = View.VISIBLE
        tvEmailAddress.text = userEmail
        tvMessage.visibility = View.GONE
    }

    private fun handleSendVerificationCode() {
        val email = etEmail.text.toString().trim()

        if (!validateEmail(email)) {
            return
        }
        
        userEmail = email
        btnSendCode.isEnabled = false
        btnSendCode.text = "전송 중..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://d66684315458.ngrok-free.app/auth/forgot-password")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("ngrok-skip-browser-warning", "true")
                connection.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("email", email)
                }

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                val reader = if (responseCode == 200) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }

                val response = reader.readText()
                reader.close()

                withContext(Dispatchers.Main) {
                    btnSendCode.isEnabled = true
                    btnSendCode.text = "인증번호 전송"
                    
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.getBoolean("success")) {
                        showVerificationStep()
                        Toast.makeText(this@ForgotPasswordActivity, 
                            "인증번호를 이메일로 전송했습니다", Toast.LENGTH_SHORT).show()
                    } else {
                        val message = jsonResponse.getString("message")
                        showMessage(message, isError = true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnSendCode.isEnabled = true
                    btnSendCode.text = "인증번호 전송"
                    showMessage("네트워크 오류가 발생했습니다", isError = true)
                }
            }
        }
    }
    
    private fun handleVerifyCode() {
        val code = etVerificationCode.text.toString().trim()
        
        if (code.isEmpty()) {
            etVerificationCode.error = "인증번호를 입력해주세요"
            etVerificationCode.requestFocus()
            return
        }
        
        btnVerifyCode.isEnabled = false
        btnVerifyCode.text = "확인 중..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://d66684315458.ngrok-free.app/auth/verify-reset-code")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("ngrok-skip-browser-warning", "true")
                connection.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("email", userEmail)
                    put("verification_code", code)
                }

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                val reader = if (responseCode == 200) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }

                val response = reader.readText()
                reader.close()

                withContext(Dispatchers.Main) {
                    btnVerifyCode.isEnabled = true
                    btnVerifyCode.text = "확인"
                    
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.getBoolean("success")) {
                        resetToken = jsonResponse.getString("reset_token")
                        
                        // 비밀번호 재설정 화면으로 이동
                        val intent = Intent(this@ForgotPasswordActivity, ResetPasswordActivity::class.java)
                        intent.putExtra("email", userEmail)
                        intent.putExtra("reset_token", resetToken)
                        startActivity(intent)
                        finish()
                    } else {
                        val message = jsonResponse.getString("message")
                        showMessage(message, isError = true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnVerifyCode.isEnabled = true
                    btnVerifyCode.text = "확인"
                    showMessage("네트워크 오류가 발생했습니다", isError = true)
                }
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
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

        return true
    }

    private fun showMessage(message: String, isError: Boolean = false) {
        tvMessage.text = message
        tvMessage.setTextColor(
            if (isError) getColor(android.R.color.holo_red_dark)
            else getColor(android.R.color.holo_green_dark)
        )
        tvMessage.visibility = View.VISIBLE
    }
}