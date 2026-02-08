package com.example.veriscope

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.text.InputType

class AccountManagementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_management)

        setupUI()
    }

    private fun setupUI() {
        // 뒤로가기 버튼
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // 이름 변경
        findViewById<LinearLayout>(R.id.nameChangeOption).setOnClickListener {
            showNameChangeDialog()
        }

        // 이메일 변경
        findViewById<LinearLayout>(R.id.emailChangeOption).setOnClickListener {
            showEmailChangeDialog()
        }

        // 비밀번호 변경
        findViewById<LinearLayout>(R.id.passwordChangeOption).setOnClickListener {
            showPasswordChangeDialog()
        }

        // 계정 초기화 버튼
        findViewById<LinearLayout>(R.id.accountResetOption).setOnClickListener {
            showResetConfirmDialog()
        }
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("계정 초기화")
            .setMessage("정말로 계정을 초기화하시겠습니까?\n\n• 검사 내역이 모두 삭제됩니다\n• 제보 내역이 모두 삭제됩니다\n• 포인트 내역 및 점수가 모두 삭제됩니다\n• 계정 정보(이메일, 비밀번호)는 유지됩니다\n\n이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("초기화") { _, _ ->
                performAccountReset()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performAccountReset() {
        try {
            // 현재 로그인된 사용자 정보 백업
            val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
            val userName = userPrefs.getString("user_name", null)
            val userEmail = userPrefs.getString("user_email", null)
            val userPassword = userPrefs.getString("user_password", null)
            val isLoggedIn = userPrefs.getBoolean("is_logged_in", false)

            // 1. 모든 SharedPreferences 파일 완전 삭제
            clearAllSharedPreferences()

            // 2. 내부 저장소의 모든 파일 삭제
            deleteAllInternalFiles()

            // 3. 계정 정보만 복원 (이메일, 비밀번호, 로그인 상태)
            if (userName != null && userEmail != null && isLoggedIn) {
                userPrefs.edit()
                    .putString("user_name", userName)
                    .putString("user_email", userEmail)
                    .putBoolean("is_logged_in", true)
                    .apply()

                // veriscope_user에도 복원
                val veriscopeUserPrefs = getSharedPreferences("veriscope_user", MODE_PRIVATE)
                veriscopeUserPrefs.edit()
                    .putString("name", userName)
                    .putString("email", userEmail)
                    .apply()

                // 비밀번호가 있다면 복원 (보안상 필요시에만)
                userPassword?.let {
                    userPrefs.edit().putString("user_password", it).apply()
                }
            }

            // 성공 메시지 표시 후 메인으로 이동
            showResetSuccessDialog()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "초기화 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearAllSharedPreferences() {
        try {
            // 앱에서 사용되는 모든 SharedPreferences 파일 목록
            val prefsNames = listOf(
                "veriscope_rewards",        // 포인트 및 리워드 데이터
                "veriscope_main",          // 메인 앱 데이터
                "veriscope_app",           // 앱 설정 데이터
                "veriscope_history",       // 검사 내역 (전역)
                "veriscope_reports",       // 제보 데이터
                "veriscope_cache",         // 캐시 데이터
                "veriscope_settings",      // 사용자 설정
                "app_preferences",         // 일반 앱 설정
                "user_activity",           // 사용자 활동 데이터
                "check_results",           // 검사 결과 데이터
                "scan_data"                // 스캔 데이터
            )

            // 기본 SharedPreferences 파일들 삭제
            for (prefsName in prefsNames) {
                try {
                    val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
                    prefs.edit().clear().apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 사용자별 히스토리 파일들 삭제 (모든 가능한 이메일 패턴)
            val currentUser = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("user_email", null)
            if (currentUser != null) {
                try {
                    val userHistoryPrefs = getSharedPreferences("veriscope_history_$currentUser", MODE_PRIVATE)
                    userHistoryPrefs.edit().clear().apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 기본 SharedPreferences도 삭제 (패키지명 기반)
            try {
                val defaultPrefs = getSharedPreferences(packageName + "_preferences", MODE_PRIVATE)
                defaultPrefs.edit().clear().apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deleteAllInternalFiles() {
        try {
            // 내부 저장소의 모든 디렉토리 삭제
            val directories = listOf(
                "report_images",      // 리포트 이미지
                "cache_images",       // 캐시된 이미지
                "check_images",       // 검사 결과 이미지
                "temp_files",         // 임시 파일
                "user_data",          // 사용자 데이터
                "app_cache",          // 앱 캐시
                "downloads",          // 다운로드 파일
                "uploads"             // 업로드 파일
            )

            for (dirName in directories) {
                try {
                    val dir = java.io.File(filesDir, dirName)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 앱의 캐시 디렉토리도 삭제
            try {
                cacheDir?.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 외부 캐시 디렉토리도 삭제 (있다면)
            try {
                externalCacheDir?.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 개별 파일들도 삭제
            try {
                val filesToDelete = filesDir.listFiles()
                filesToDelete?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showResetSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("초기화 완료")
            .setMessage("계정이 성공적으로 초기화되었습니다.\n앱을 다시 시작합니다.")
            .setPositiveButton("확인") { _, _ ->
                // 메인 액티비티로 이동하고 모든 이전 액티비티 제거
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showNameChangeDialog() {
        val input = EditText(this).apply {
            hint = "새 이름을 입력하세요"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("이름 변경")
            .setView(input)
            .setPositiveButton("변경") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    changeName(newName)
                } else {
                    Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEmailChangeDialog() {
        val input = EditText(this).apply {
            hint = "새 이메일을 입력하세요"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(this)
            .setTitle("이메일 변경")
            .setMessage("새 이메일로 인증 메일이 발송됩니다.")
            .setView(input)
            .setPositiveButton("인증 메일 발송") { _, _ ->
                val newEmail = input.text.toString().trim()
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    sendEmailVerification(newEmail)
                } else {
                    Toast.makeText(this, "올바른 이메일 형식을 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showPasswordChangeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val currentPasswordInput = EditText(this).apply {
            hint = "현재 비밀번호"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val newPasswordInput = EditText(this).apply {
            hint = "새 비밀번호"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val confirmPasswordInput = EditText(this).apply {
            hint = "새 비밀번호 확인"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(currentPasswordInput)
        layout.addView(newPasswordInput)
        layout.addView(confirmPasswordInput)

        AlertDialog.Builder(this)
            .setTitle("비밀번호 변경")
            .setView(layout)
            .setPositiveButton("변경") { _, _ ->
                val currentPassword = currentPasswordInput.text.toString().trim()
                val newPassword = newPasswordInput.text.toString().trim()
                val confirmPassword = confirmPasswordInput.text.toString().trim()

                if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(this, "모든 필드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                } else if (newPassword != confirmPassword) {
                    Toast.makeText(this, "새 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                } else if (newPassword.length < 6) {
                    Toast.makeText(this, "비밀번호는 최소 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                } else {
                    changePassword(currentPassword, newPassword)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun changeName(newName: String) {
        val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val veriscopeUserPrefs = getSharedPreferences("veriscope_user", MODE_PRIVATE)
        
        userPrefs.edit().putString("user_name", newName).apply()
        veriscopeUserPrefs.edit().putString("name", newName).apply()
        
        Toast.makeText(this, "이름이 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun sendEmailVerification(newEmail: String) {
        // 이메일 인증 화면으로 이동
        val intent = Intent(this, EmailVerificationActivity::class.java)
        intent.putExtra("email", newEmail)
        intent.putExtra("action_type", "change_email") // 이메일 변경임을 알리는 플래그
        intent.putExtra("current_email", getCurrentUserEmail()) // 현재 이메일도 전달
        startActivityForResult(intent, 100)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val verifiedEmail = data?.getStringExtra("verified_email")
            if (verifiedEmail != null) {
                // 이메일 변경 완료
                val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
                val veriscopeUserPrefs = getSharedPreferences("veriscope_user", MODE_PRIVATE)
                
                userPrefs.edit().putString("user_email", verifiedEmail).apply()
                veriscopeUserPrefs.edit().putString("email", verifiedEmail).apply()
                
                Toast.makeText(this, "이메일이 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getCurrentUserEmail(): String {
        val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        return userPrefs.getString("user_email", "") ?: ""
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val savedPassword = userPrefs.getString("user_password", "")
        
        if (savedPassword == currentPassword) {
            userPrefs.edit().putString("user_password", newPassword).apply()
            Toast.makeText(this, "비밀번호가 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "현재 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
        }
    }


}