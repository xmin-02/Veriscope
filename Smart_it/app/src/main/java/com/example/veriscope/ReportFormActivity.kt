package com.example.veriscope

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.veriscope.utils.UserManager
import com.example.veriscope.utils.ReportedItemsManager

class ReportFormActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var cbAgree: CheckBox
    private lateinit var btnViewPolicy: TextView
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var tvUrlLabel: TextView
    private lateinit var etReportUrl: EditText
    private lateinit var imageContainer: LinearLayout
    private lateinit var ivReportImage: ImageView
    private lateinit var etReason: EditText
    private lateinit var btnSubmitReport: Button
    
    private lateinit var userManager: UserManager
    private lateinit var reportedItemsManager: ReportedItemsManager

    private var reportType: String = "URL" // URL 또는 IMAGE
    private var reportUrl: String = ""
    private var reportImageUri: String = ""
    private var savedImagePath: String = "" // 저장된 이미지 파일 경로

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_form)

        userManager = UserManager(this)
        reportedItemsManager = ReportedItemsManager.getInstance(this)
        
        initViews()
        setupData()
        setupClickListeners()
        loadUserInfo()
    }
    
    private var emailSentFromApp = false
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("ReportForm", "onResume 호출됨, emailSentFromApp: $emailSentFromApp")
        
        // 이메일 앱에서 돌아왔을 때만 확인 다이얼로그 표시
        if (emailSentFromApp) {
            showEmailSentConfirmation()
            emailSentFromApp = false // 한 번만 표시되도록 리셋
        }
    }
    
    private fun showEmailSentConfirmation() {
        // 잠시 후에 다이얼로그 표시 (이메일 앱에서 돌아온 직후 바로 표시되지 않도록)
        android.os.Handler(mainLooper).postDelayed({
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("신고 완료 확인")
                .setMessage("이메일 전송을 완료하셨나요?")
                .setPositiveButton("완료") { _, _ ->
                    // 제보 완료된 항목 저장 (검사 내역과 동일한 식별자 사용)
                    val itemId = when (reportType) {
                        "URL" -> reportUrl
                        "IMAGE" -> {
                            // 이미지의 경우 원본 URI를 우선 사용
                            if (reportImageUri.isNotEmpty()) reportImageUri else savedImagePath
                        }
                        else -> "general_${System.currentTimeMillis()}"
                    }
                    
                    if (itemId.isNotEmpty()) {
                        // 두 곳에 모두 저장하여 동기화 보장
                        reportedItemsManager.addReportedItem(itemId)
                        
                        // 추가로 veriscope_reported에도 직접 저장
                        val prefs = getSharedPreferences("veriscope_reported", MODE_PRIVATE)
                        val reportedItems = prefs.getStringSet("reported_items", setOf()) ?: setOf()
                        val updatedItems = reportedItems.toMutableSet()
                        updatedItems.add(itemId)
                        
                        prefs.edit()
                            .putStringSet("reported_items", updatedItems)
                            .putLong("reported_$itemId", System.currentTimeMillis())
                            .apply()
                            
                        android.util.Log.d("ReportForm", "제보 완료된 항목 저장: $itemId (타입: $reportType)")
                        android.util.Log.d("ReportForm", "저장된 제보 항목들: ${updatedItems.joinToString(", ")}")
                    }
                    
                    // 이미지 타입인 경우 저장된 이미지 삭제
                    if (reportType == "IMAGE" && savedImagePath.isNotEmpty()) {
                        try {
                            val file = java.io.File(savedImagePath)
                            if (file.exists()) {
                                file.delete()
                                android.util.Log.d("ReportForm", "제보 완료 후 이미지 삭제: $savedImagePath")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ReportForm", "이미지 삭제 실패", e)
                        }
                    }
                    
                    // 제보 완료 시 100P 적립 (리워드 페이지로 이동)
                    addReportReward()
                    
                    Toast.makeText(this, "신고가 완료되었습니다! +100P가 적립되었습니다.", Toast.LENGTH_SHORT).show()
                    // 리워드 페이지로 이동하여 포인트 적립 표시
                    val intentReward = Intent(this, RewardActivity::class.java)
                    intentReward.putExtra("REWARD_POINTS", 100)
                    intentReward.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intentReward)
                    finish()
                }
                .setNegativeButton("나중에") { _, _ ->
                    // 현재 화면 유지
                    Toast.makeText(this, "나중에 이메일을 전송해주세요.", Toast.LENGTH_SHORT).show()
                }
                .setCancelable(true)
                .show()
        }, 500) // 0.5초 후에 다이얼로그 표시
    }
    
    private fun loadUserInfo() {
        // 현재 로그인된 사용자 정보 가져오기
        val currentUser = userManager.getCurrentUser()
        if (currentUser != null) {
            etName.setText(currentUser.name)
            etEmail.setText(currentUser.email)
        } else {
            // 로그인되지 않은 경우 기본값
            etName.setText("")
            etEmail.setText("")
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        cbAgree = findViewById(R.id.cbAgree)
        btnViewPolicy = findViewById(R.id.btnViewPolicy)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        tvUrlLabel = findViewById(R.id.tvUrlLabel)
        etReportUrl = findViewById(R.id.etReportUrl)
        imageContainer = findViewById(R.id.imageContainer)
        ivReportImage = findViewById(R.id.ivReportImage)
        etReason = findViewById(R.id.etReason)
        btnSubmitReport = findViewById(R.id.btnSubmitReport)
    }

    private fun setupData() {
        // Intent에서 데이터 받아오기
        reportType = intent.getStringExtra("REPORT_TYPE") ?: "URL"
        reportUrl = intent.getStringExtra("REPORT_URL") ?: ""
        reportImageUri = intent.getStringExtra("REPORT_IMAGE") ?: ""
        savedImagePath = intent.getStringExtra("SAVED_IMAGE_PATH") ?: ""

        // 디버깅용 로그
        android.util.Log.d("ReportForm", "설정된 데이터:")
        android.util.Log.d("ReportForm", "- reportType: $reportType")
        android.util.Log.d("ReportForm", "- reportUrl: $reportUrl")
        android.util.Log.d("ReportForm", "- reportImageUri: $reportImageUri")

        when (reportType) {
            "URL" -> {
                setupUrlReport()
            }
            "IMAGE" -> {
                setupImageReport()
            }
            "GENERAL" -> {
                setupGeneralReport()
            }
        }
    }

    private fun setupUrlReport() {
        // URL 제보 모드
        tvUrlLabel.text = "신고 URL을 입력하세요"
        etReportUrl.hint = "신고 URL을 입력하세요"
        etReportUrl.setText(reportUrl)
        imageContainer.visibility = View.GONE
    }

    private fun setupImageReport() {
        // 이미지 제보 모드
        tvUrlLabel.text = "이미지의 출처 URL을 입력해주세요"
        etReportUrl.hint = "이미지의 출처 URL을 입력해주세요"
        etReportUrl.setText("")
        imageContainer.visibility = View.VISIBLE

        // 이미지 표시
        if (reportImageUri.isNotEmpty()) {
            try {
                android.util.Log.d("ReportForm", "이미지 URI 로딩 시도: $reportImageUri")
                
                // 우선 savedImagePath가 있으면 그것을 사용
                if (savedImagePath.isNotEmpty()) {
                    val file = java.io.File(savedImagePath)
                    if (file.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(savedImagePath)
                        if (bitmap != null) {
                            ivReportImage.setImageBitmap(bitmap)
                            android.util.Log.d("ReportForm", "저장된 파일에서 이미지 로딩 성공: $savedImagePath")
                            return
                        }
                    }
                }
                
                // savedImagePath가 없거나 실패한 경우 URI로 시도
                val uri = Uri.parse(reportImageUri)
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    
                    if (bitmap != null) {
                        ivReportImage.setImageBitmap(bitmap)
                        android.util.Log.d("ReportForm", "URI에서 이미지 로딩 성공")
                    } else {
                        android.util.Log.e("ReportForm", "URI Bitmap 디코딩 실패")
                        showImageLoadError()
                    }
                } else {
                    android.util.Log.e("ReportForm", "InputStream이 null")
                    showImageLoadError()
                }
            } catch (e: Exception) {
                android.util.Log.e("ReportForm", "이미지 로딩 오류: ${e.message}", e)
                showImageLoadError()
            }
        } else {
            android.util.Log.w("ReportForm", "이미지 URI가 비어있음")
        }
    }

    private fun setupGeneralReport() {
        // 일반 제보 모드 (URL이나 이미지 없이)
        tvUrlLabel.text = "관련 URL을 입력하세요 (선택사항)"
        etReportUrl.hint = "관련 URL이 있다면 입력해주세요"
        etReportUrl.setText("")
        imageContainer.visibility = View.GONE
    }

    private fun showImageLoadError() {
        // 이미지 로딩 실패 시 대체 이미지나 메시지 표시
        ivReportImage.setImageResource(android.R.drawable.ic_menu_gallery)
        Toast.makeText(this, "이미지를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        // 개인정보 동의 체크박스 클릭 시 개인정보 처리방침 팝업 표시
        cbAgree.setOnClickListener {
            if (cbAgree.isChecked) {
                // 체크된 상태에서 클릭하면 체크 해제하고 팝업 표시
                cbAgree.isChecked = false
                showPrivacyPolicy()
            } else {
                // 체크되지 않은 상태에서 클릭하면 팝업 표시
                showPrivacyPolicy()
            }
        }

        btnViewPolicy.setOnClickListener {
            // 개인정보 처리방침 페이지로 이동 (웹뷰나 다이얼로그)
            showPrivacyPolicy()
        }

        btnSubmitReport.setOnClickListener {
            submitReport()
        }
    }

    private fun showPrivacyPolicy() {
        val privacyContent = """
            <h3><b>개인정보 처리방침</b></h3><br/>
            
            <b>1. 개인정보 수집 및 이용목적</b><br/>
            • 허위정보 신고 접수 및 처리<br/>
            • 신고자 확인 및 결과 통보<br/>
            • 서비스 개선 및 통계 분석<br/>
            • 법적 분쟁 대응<br/><br/>
            
            <b>2. 수집하는 개인정보 항목</b><br/>
            • 필수항목: 이름, 이메일 주소<br/>
            • 선택항목: 신고 URL, 이미지 자료<br/>
            • 자동수집: 신고 일시, IP 주소<br/><br/>
            
            <b>3. 개인정보 보유 및 이용기간</b><br/>
            • 신고 처리 완료 후 <font color='#2196F3'><b>3년간 보관</b></font><br/>
            • 법적 분쟁 발생 시 분쟁 해결 시까지<br/>
            • 관련 법령에 따른 보관의무 기간<br/><br/>
            
            <b>4. 개인정보 제3자 제공</b><br/>
            • 원칙적으로 제3자에게 제공하지 않음<br/>
            • 법령에 의한 요구 시에만 관련 기관에 제공<br/>
            • 신고 처리를 위한 관계 기관 협조 시 제공 가능<br/><br/>
            
            <b>5. 개인정보 보호 조치</b><br/>
            • 개인정보 암호화 저장<br/>
            • 접근 권한 제한 및 관리<br/>
            • 정기적인 보안 점검 실시<br/><br/>
            
            <b>6. 개인정보 처리 책임자</b><br/>
            • 이메일: smartit.ngms@gmail.com<br/>
            • 개인정보 관련 문의 및 권리행사 요청 가능<br/><br/>
            
            <font color='#FF5722'><b>※ 본 방침은 2024년 11월부터 적용됩니다.</b></font>
        """.trimIndent()
        
        val spannedContent = android.text.Html.fromHtml(privacyContent, android.text.Html.FROM_HTML_MODE_LEGACY)
        
        // 스크롤 가능한 텍스트뷰 생성
        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                800 // 높이 제한
            )
        }
        
        val textView = android.widget.TextView(this).apply {
            text = spannedContent
            setPadding(40, 20, 40, 20)
            textSize = 14f
            setLineSpacing(6f, 1f)
        }
        
        scrollView.addView(textView)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("동의") { dialog, _ -> 
                // 개인정보 처리방침에 동의하면 체크박스 체크
                cbAgree.isChecked = true
                dialog.dismiss() 
            }
            .setNegativeButton("취소") { dialog, _ -> 
                // 취소하면 체크박스 체크 해제
                cbAgree.isChecked = false
                dialog.dismiss() 
            }
            .setCancelable(true)
            .show()
    }

    private fun submitReport() {
        if (!validateInput()) {
            return
        }

        if (!cbAgree.isChecked) {
            Toast.makeText(this, "개인정보 수집 및 이용에 동의해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        // 중복 제보 확인
        val itemId = when (reportType) {
            "URL" -> reportUrl
            "IMAGE" -> {
                if (reportImageUri.isNotEmpty()) reportImageUri else savedImagePath
            }
            else -> "general_${System.currentTimeMillis()}"
        }
        
        if (itemId.isNotEmpty()) {
            val prefs = getSharedPreferences("veriscope_reported", MODE_PRIVATE)
            val reportedItems = prefs.getStringSet("reported_items", setOf()) ?: setOf()
            
            if (reportedItems.contains(itemId)) {
                Toast.makeText(this, "이미 제보 완료된 콘텐츠입니다.", Toast.LENGTH_LONG).show()
                return
            }
        }

        // 제보 데이터 수집
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val url = etReportUrl.text.toString().trim()
        val reason = etReason.text.toString().trim()

        // 자동으로 이메일 발송
        sendAutoEmail(name, email, url, reason)
    }

    private fun sendAutoEmail(name: String, email: String, url: String, reason: String) {
        // 로딩 표시
        Toast.makeText(this, "신고서를 자동으로 발송하고 있습니다...", Toast.LENGTH_SHORT).show()
        
        // 관리자 이메일 주소
        val adminEmail = "smartit.ngms@gmail.com"
        
        // 현재 로그인된 사용자 정보 가져오기
        val currentUser = userManager.getCurrentUser()
        val senderEmail = currentUser?.email ?: email
        
        // Intent에서 검사 결과 가져오기 (만약 있다면)
        val checkResult = intent.getStringExtra("CHECK_RESULT") ?: "검사 결과 없음"
        val reliabilityScore = intent.getStringExtra("RELIABILITY_SCORE") ?: "정보 없음"
        
        // 신고 유형에 따른 제목과 내용 구성
        val reportTypeText = when (reportType) {
            "URL" -> "허위정보 URL 신고"
            "IMAGE" -> "허위정보 이미지 신고"
            "GENERAL" -> "허위정보 일반 신고"
            else -> "허위정보 신고"
        }
        
        val subject = "[$reportTypeText] Veriscope 허위정보 신고"
        
        val emailBody = buildString {
            appendLine("=== Veriscope 허위정보 신고서 ===")
            appendLine()
            appendLine("▶ 발신자: $senderEmail")
            appendLine("▶ 수신자: smartit.ngms@gmail.com")
            appendLine("▶ 신고 유형: $reportTypeText")
            appendLine("▶ 신고 일시: ${getCurrentDateTime()}")
            appendLine()
            appendLine("=== 신고자 정보 ===")
            appendLine("• 이름: $name")
            appendLine("• 이메일: $email")
            appendLine()
            when (reportType) {
                "URL" -> {
                    appendLine("=== 신고 URL ===")
                    appendLine("• URL: $url")
                    if (checkResult != "검사 결과 없음") {
                        appendLine("• 검사 결과: $checkResult")
                        appendLine("• 신뢰도: $reliabilityScore")
                    }
                }
                "IMAGE" -> {
                    appendLine("=== 신고 이미지 ===")
                    appendLine("• 이미지 출처 URL: ${if (url.isNotEmpty()) url else "없음"}")
                    if (reportImageUri.isNotEmpty()) {
                        appendLine("• 첨부 이미지: 이메일에 첨부됨")
                    } else {
                        appendLine("• 첨부 이미지: 없음")
                    }
                    if (checkResult != "검사 결과 없음") {
                        appendLine("• 검사 결과: $checkResult")
                        appendLine("• 신뢰도: $reliabilityScore")
                    }
                }
                "GENERAL" -> {
                    appendLine("=== 관련 정보 ===")
                    appendLine("• 관련 URL: ${if (url.isNotEmpty()) url else "없음"}")
                }
            }
            appendLine()
            appendLine("=== 신고 사유 ===")
            appendLine(reason)
            appendLine()
            appendLine("=== 추가 정보 ===")
            appendLine("• 신고 앱: Veriscope")
            appendLine("• 신고 경로: Android 앱")
            appendLine("• 발신자 계정: $senderEmail")
            if (checkResult != "검사 결과 없음" && reportType == "GENERAL") {
                appendLine("• 검사 결과: $checkResult")
                appendLine("• 신뢰도: $reliabilityScore")
            }
            appendLine()
            appendLine("※ 본 신고는 Veriscope 앱을 통해 발송되었습니다.")
        }

        // 사용자의 이메일로 Gmail 앱을 통해 자동 발송
        sendEmailWithUserAccount(adminEmail, subject, emailBody)
    }

    private fun getCurrentDateTime(): String {
        val formatter = java.text.SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", java.util.Locale.KOREA)
        return formatter.format(java.util.Date())
    }

    private fun validateInput(): Boolean {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val url = etReportUrl.text.toString().trim()
        val reason = etReason.text.toString().trim()

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

        if (!isValidEmail(email)) {
            etEmail.error = "올바른 이메일 형식을 입력해주세요"
            etEmail.requestFocus()
            return false
        }

        if (url.isEmpty() && reportType != "GENERAL") {
            val errorMessage = when (reportType) {
                "IMAGE" -> "이미지의 출처 URL을 입력해주세요"
                else -> "신고 URL을 입력해주세요"
            }
            etReportUrl.error = errorMessage
            etReportUrl.requestFocus()
            return false
        }

        if (reason.isEmpty()) {
            etReason.error = "신고 사유를 입력해주세요"
            etReason.requestFocus()
            return false
        }

        return true
    }

    private fun sendEmailWithUserAccount(toEmail: String, subject: String, body: String) {
        try {
            // 현재 로그인된 사용자 정보 가져오기
            val currentUser = userManager.getCurrentUser()
            val userEmail = currentUser?.email ?: etEmail.text.toString().trim()
            
            android.util.Log.d("ReportForm", "이메일 앱 실행 시도 시작")
            
            // 이미지 첨부 여부에 따른 이메일 Intent 생성
            val emailIntent = Intent(Intent.ACTION_SEND)
            
            if (reportType == "IMAGE" && reportImageUri.isNotEmpty()) {
                // 이미지가 있는 경우
                android.util.Log.d("ReportForm", "이미지 첨부가 있는 이메일 Intent 생성")
                emailIntent.type = "message/rfc822" // 이미지 첨부를 위해 message/rfc822 사용
                emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
                emailIntent.putExtra(Intent.EXTRA_TEXT, body)
                
                // 이미지 첫부
                try {
                    val imageUri = Uri.parse(reportImageUri)
                    emailIntent.putExtra(Intent.EXTRA_STREAM, imageUri)
                    emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    
                    android.util.Log.d("ReportForm", "이미지 첫부 성공: $reportImageUri")
                } catch (e: Exception) {
                    android.util.Log.e("ReportForm", "이미지 첫부 실패: ${e.message}")
                    // 이미지 첫부 실패해도 이메일은 전송
                }
            } else {
                // 이미지가 없는 경우
                android.util.Log.d("ReportForm", "텍스트만 있는 이메일 Intent 생성")
                emailIntent.type = "text/plain"
                emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
                emailIntent.putExtra(Intent.EXTRA_TEXT, body)
            }
            
            android.util.Log.d("ReportForm", "수신자: $toEmail")
            android.util.Log.d("ReportForm", "제목: $subject")
            
            // Gmail 우선 실행 시도
            try {
                // 먼저 Gmail 직접 실행 시도
                val gmailIntent = emailIntent.clone() as Intent
                gmailIntent.setPackage("com.google.android.gm")
                
                if (gmailIntent.resolveActivity(packageManager) != null) {
                    android.util.Log.d("ReportForm", "Gmail 앱으로 직접 실행")
                    
                    try {
                        // 이미지 첨부 상태 로그
                        if (reportType == "IMAGE" && reportImageUri.isNotEmpty()) {
                            android.util.Log.d("ReportForm", "이미지 첨부 상태: 포함됨")
                            Toast.makeText(this, "Gmail에서 이미지가 첨부된 이메일을 $userEmail 계정으로 전송해주세요!", Toast.LENGTH_SHORT).show()
                        } else {
                            android.util.Log.d("ReportForm", "이미지 첨부 상태: 없음")
                            Toast.makeText(this, "Gmail에서 $userEmail 계정으로 전송해주세요!", Toast.LENGTH_SHORT).show()
                        }
                        
                        startActivity(gmailIntent)
                        emailSentFromApp = true
                        android.util.Log.d("ReportForm", "Gmail 실행 성공")
                    } catch (securityException: SecurityException) {
                        android.util.Log.w("ReportForm", "Gmail 권한 오류, 일반 선택기로 전환: ${securityException.message}")
                        // Gmail 권한 오류 시 일반 선택기 사용
                        throw securityException
                    }
                } else {
                    // Gmail이 없으면 일반 이메일 앱 선택기 사용
                    android.util.Log.d("ReportForm", "Gmail 없음, 일반 이메일 앱 선택기 사용")
                    emailIntent.setPackage(null) // 패키지 제한 해제
                    
                    if (emailIntent.resolveActivity(packageManager) != null) {
                        // 이미지 첨부 상태 로그
                        if (reportType == "IMAGE" && reportImageUri.isNotEmpty()) {
                            Toast.makeText(this, "이미지가 첨부된 이메일을 $userEmail 계정으로 전송해주세요!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "$userEmail 계정으로 전송해주세요!", Toast.LENGTH_SHORT).show()
                        }
                        
                        startActivity(Intent.createChooser(emailIntent, "이메일 앱 선택"))
                        emailSentFromApp = true
                        android.util.Log.d("ReportForm", "이메일 앱 선택기 실행 성공")
                    } else {
                        android.util.Log.e("ReportForm", "이메일 앱을 찾을 수 없음")
                        Toast.makeText(this, "이메일 앱이 설치되어 있지 않습니다.", Toast.LENGTH_LONG).show()
                        emailSentFromApp = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ReportForm", "이메일 앱 실행 오류: ${e.message}")
                Toast.makeText(this, "이메일 앱을 열 수 없습니다: ${e.message}", Toast.LENGTH_LONG).show()
                emailSentFromApp = false
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ReportForm", "전체 이메일 전송 오류: ${e.message}")
            Toast.makeText(this, "이메일 전송 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 권한 해제 (메모리 누수 방지)
        if (reportType == "IMAGE" && reportImageUri.isNotEmpty()) {
            try {
                val imageUri = Uri.parse(reportImageUri)
                revokeUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                android.util.Log.d("ReportForm", "URI 권한 해제됨: $reportImageUri")
            } catch (e: Exception) {
                android.util.Log.w("ReportForm", "URI 권한 해제 실패: ${e.message}")
            }
        }
    }

    private fun saveReportedItem() {
        // 제보된 항목의 고유 식별자 생성
        val itemId = when (reportType) {
            "URL" -> reportUrl
            "IMAGE" -> reportImageUri
            "GENERAL" -> "general_${System.currentTimeMillis()}"
            else -> "unknown_${System.currentTimeMillis()}"
        }
        
        // SharedPreferences에 제보된 항목 저장
        val prefs = getSharedPreferences("veriscope_reported", MODE_PRIVATE)
        val reportedItems = prefs.getStringSet("reported_items", mutableSetOf()) ?: mutableSetOf()
        
        // 새로운 Set을 생성하여 수정
        val updatedItems = reportedItems.toMutableSet()
        updatedItems.add(itemId)
        
        prefs.edit()
            .putStringSet("reported_items", updatedItems)
            .putLong("reported_${itemId}", System.currentTimeMillis())
            .apply()
            
        android.util.Log.d("ReportForm", "제보된 항목 저장: $itemId")
    }

    private fun addReportReward() {
        try {
            // SharedPreferences에서 포인트 내역 불러오기
            val prefs = getSharedPreferences("veriscope_rewards", MODE_PRIVATE)
            val historyJson = prefs.getString("point_history", "[]") ?: "[]"
            val historyArray = org.json.JSONArray(historyJson)
            
            // 새로운 제보 완료 내역 추가
            val reportItem = org.json.JSONObject()
            reportItem.put("type", "report")
            reportItem.put("points", 100)
            reportItem.put("timestamp", System.currentTimeMillis())
            historyArray.put(reportItem)
            
            // 포인트 내역 저장
            prefs.edit()
                .putString("point_history", historyArray.toString())
                .apply()
                
            android.util.Log.d("ReportForm", "제보 완료 100P 적립 완료")
            
        } catch (e: Exception) {
            android.util.Log.e("ReportForm", "제보 포인트 적립 실패: ${e.message}")
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}