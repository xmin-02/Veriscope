package com.example.veriscope

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
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

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnResetPassword: Button
    private lateinit var tvMessage: TextView
    
    private var userEmail: String = ""
    private var resetToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        // Intent에서 데이터 받기
        userEmail = intent.getStringExtra("email") ?: ""
        resetToken = intent.getStringExtra("reset_token") ?: ""
        
        if (userEmail.isEmpty() || resetToken.isEmpty()) {
            Toast.makeText(this, "잘못된 접근입니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        tvMessage = findViewById(R.id.tvMessage)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnResetPassword.setOnClickListener {
            handleResetPassword()
        }
    }

    private fun handleResetPassword() {
        val newPassword = etNewPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        if (!validatePasswords(newPassword, confirmPassword)) {
            return
        }

        btnResetPassword.isEnabled = false
        btnResetPassword.text = "재설정 중..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://d66684315458.ngrok-free.app/auth/reset-password")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("ngrok-skip-browser-warning", "true")
                connection.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("email", userEmail)
                    put("reset_token", resetToken)
                    put("new_password", newPassword)
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
                    btnResetPassword.isEnabled = true
                    btnResetPassword.text = "비밀번호 재설정"
                    
                    val jsonResponse = JSONObject(response)
                    
                    if (jsonResponse.getBoolean("success")) {
                        Toast.makeText(this@ResetPasswordActivity, 
                            "비밀번호가 성공적으로 변경되었습니다", Toast.LENGTH_SHORT).show()
                        
                        // 로그인 화면으로 돌아가기
                        finish()
                    } else {
                        val message = jsonResponse.getString("message")
                        showMessage(message, isError = true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnResetPassword.isEnabled = true
                    btnResetPassword.text = "비밀번호 재설정"
                    showMessage("네트워크 오류가 발생했습니다", isError = true)
                }
            }
        }
    }

    private fun validatePasswords(newPassword: String, confirmPassword: String): Boolean {
        if (newPassword.isEmpty()) {
            etNewPassword.error = "새 비밀번호를 입력해주세요"
            etNewPassword.requestFocus()
            return false
        }

        if (newPassword.length < 6) {
            etNewPassword.error = "비밀번호는 6자 이상이어야 합니다"
            etNewPassword.requestFocus()
            return false
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.error = "비밀번호 확인을 입력해주세요"
            etConfirmPassword.requestFocus()
            return false
        }

        if (newPassword != confirmPassword) {
            etConfirmPassword.error = "비밀번호가 일치하지 않습니다"
            etConfirmPassword.requestFocus()
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
    }
}