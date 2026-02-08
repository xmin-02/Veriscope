package com.example.veriscope

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.graphics.Typeface
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.veriscope.utils.UserManager
import java.io.File
import android.text.Html
import android.text.Spanned

class MainActivity : AppCompatActivity() {
    
    private lateinit var etNewsUrl: EditText
    private lateinit var btnUrlCheck: Button
    private lateinit var btnImageCheck: Button
    private lateinit var imageUploadArea: LinearLayout
    
    // 탭 관련 뷰들
    private lateinit var tabUrlCheck: LinearLayout
    private lateinit var tabImageCheck: LinearLayout
    private lateinit var tabUrlText: TextView
    private lateinit var tabImageText: TextView
    
    // 이미지 관련 뷰들
    private lateinit var selectedImageView: ImageView
    private lateinit var imageStatusText: TextView
    private lateinit var btnHelp: ImageView
    
    // 선택된 이미지 URI
    private var selectedImageUri: Uri? = null
    
    private lateinit var userManager: UserManager
    
    // 이미지 선택 런처
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        android.util.Log.d("MainActivity", "이미지 선택 결과: $uri")
        uri?.let {
            selectedImageUri = it
            displaySelectedImage(it)
        } ?: run {
            android.util.Log.d("MainActivity", "이미지 선택 취소됨")
        }
    }
    private lateinit var urlCheckView: ScrollView
    private lateinit var imageCheckView: ScrollView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        userManager = UserManager(this)
        
        // 로그인 상태 확인
        checkLoginStatus()
        
        initViews()
        setupClickListeners()
        showUrlTab() // 기본으로 URL 탭 표시
    }
    
    private fun checkLoginStatus() {
        if (!userManager.isLoggedIn()) {
            // 로그인되지 않은 경우 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
            return
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 앱이 다시 활성화될 때 로그인 상태 확인
        checkLoginStatus()
        
        // URL 입력 필드만 초기화 (이미지는 유지)
        etNewsUrl.setText("")
    }
    
    private fun clearInputFields() {
        android.util.Log.d("MainActivity", "입력 필드 초기화")
        
        // URL 입력 필드 초기화
        etNewsUrl.setText("")
        
        // 이미지 선택 초기화
        selectedImageUri = null
        selectedImageView.visibility = View.GONE
        selectedImageView.setImageURI(null)
        
        // 업로드 프롬프트 다시 표시
        findViewById<LinearLayout>(R.id.uploadPrompt).visibility = View.VISIBLE
        imageStatusText.text = "이미지를 선택해주세요"
    }
    
    private fun initViews() {
        etNewsUrl = findViewById(R.id.etNewsUrl)
        btnUrlCheck = findViewById(R.id.btnUrlCheck)
        btnImageCheck = findViewById(R.id.btnImageCheck)
        imageUploadArea = findViewById(R.id.imageUploadArea)
        
        // 탭 뷰들
        tabUrlCheck = findViewById(R.id.tabUrlCheck)
        tabImageCheck = findViewById(R.id.tabImageCheck)
        tabUrlText = findViewById(R.id.tabUrlText)
        tabImageText = findViewById(R.id.tabImageText)
        urlCheckView = findViewById(R.id.urlCheckView)
        imageCheckView = findViewById(R.id.imageCheckView)
        
        // 이미지 관련 뷰들
        selectedImageView = findViewById(R.id.selectedImageView)
        imageStatusText = findViewById(R.id.imageStatusText)
        
        // 도움말 버튼
        btnHelp = findViewById(R.id.btnNotification)
    }
    
    private fun setupClickListeners() {
        // 탭 클릭 리스너
        tabUrlCheck.setOnClickListener {
            showUrlTab()
        }
        
        tabImageCheck.setOnClickListener {
            showImageTab()
        }
        
        // 기능 버튼 클릭 리스너
        btnUrlCheck.setOnClickListener {
            handleUrlCheck()
        }
        
        btnImageCheck.setOnClickListener {
            handleImageCheck()
        }
        
        // 이미지 업로드 영역 클릭 리스너
        imageUploadArea.setOnClickListener {
            openImagePicker()
        }
        
        // 리워드 확인 탭 클릭 리스너
        findViewById<LinearLayout>(R.id.tabHistory).setOnClickListener {
            openRewardPage()
        }
        
        // 마이페이지 탭 클릭 리스너
        findViewById<LinearLayout>(R.id.tabProfile).setOnClickListener {
            openMyPage()
        }
        
        // 제보하기 탭 클릭 리스너
        findViewById<LinearLayout>(R.id.tabReport).setOnClickListener {
            openReportPage()
        }
        
        // 도움말 버튼 클릭 리스너
        btnHelp.setOnClickListener {
            showHelpDialog()
        }
    }
    
    private fun showUrlTab() {
        // 탭 색상 변경
        tabUrlCheck.setBackgroundColor(resources.getColor(R.color.primary, null))
        tabImageCheck.setBackgroundColor(resources.getColor(R.color.tab_inactive_bg, null))
        tabUrlText.setTextColor(resources.getColor(android.R.color.white, null))
        tabImageText.setTextColor(resources.getColor(R.color.tab_inactive_text, null))
        // 텍스트 스타일: URL 탭은 굵게, 이미지 탭은 보통
        tabUrlText.setTypeface(null, Typeface.BOLD)
        tabImageText.setTypeface(null, Typeface.NORMAL)
        
        // 뷰 전환
        urlCheckView.visibility = View.VISIBLE
        imageCheckView.visibility = View.GONE
    }
    
    private fun showImageTab() {
        // 탭 색상 변경
        tabImageCheck.setBackgroundColor(resources.getColor(R.color.primary, null))
        tabUrlCheck.setBackgroundColor(resources.getColor(R.color.tab_inactive_bg, null))
        tabImageText.setTextColor(resources.getColor(android.R.color.white, null))
        tabUrlText.setTextColor(resources.getColor(R.color.tab_inactive_text, null))
        // 텍스트 스타일: 이미지 탭은 굵게, URL 탭은 보통
        tabImageText.setTypeface(null, Typeface.BOLD)
        tabUrlText.setTypeface(null, Typeface.NORMAL)
        
        // 뷰 전환
        imageCheckView.visibility = View.VISIBLE
        urlCheckView.visibility = View.GONE
    }
    
    private fun handleUrlCheck() {
        val url = etNewsUrl.text.toString().trim()
        
        if (url.isEmpty()) {
            Toast.makeText(this, "URL을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!url.startsWith("http")) {
            Toast.makeText(this, "올바른 URL을 입력해주세요 (http:// 또는 https://)", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 로딩 화면으로 이동하면서 URL 전달
        val intent = Intent(this, LoadingActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra("type", "url")
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        
        // URL 입력 필드 초기화
        etNewsUrl.setText("")
    }
    
    private fun handleImageCheck() {
        android.util.Log.d("MainActivity", "이미지 검사 시작 - selectedImageUri: $selectedImageUri")
        
        if (selectedImageUri != null) {
            android.util.Log.d("MainActivity", "이미지 URI 유효: $selectedImageUri")
            
            // 선택된 이미지가 있으면 검사 시작
            val intent = Intent(this, LoadingActivity::class.java)
            intent.putExtra("type", "image")
            intent.putExtra("imageUri", selectedImageUri.toString())
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            
            // 이미지 초기화는 하지 않음 (결과에서 돌아올 때를 대비)
        } else {
            android.util.Log.d("MainActivity", "이미지가 선택되지 않음")
            Toast.makeText(this, "먼저 이미지를 선택해주세요", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openMyPage() {
        // 마이페이지로 이동
        val intent = Intent(this, MyPageActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
    
    private fun openReportPage() {
        // 제보하기 페이지로 이동 (검사 내역 목록)
        val intent = Intent(this, ReportActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
    
    private fun openRewardPage() {
        // 리워드 확인 페이지로 이동
        val intent = Intent(this, RewardActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
    
    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }
    
    private fun displaySelectedImage(uri: Uri) {
        try {
            android.util.Log.d("MainActivity", "이미지 표시 시작: $uri")
            
            // 업로드 프롬프트 먼저 숨기기
            findViewById<LinearLayout>(R.id.uploadPrompt).visibility = View.GONE
            
            // 이미지 뷰에 선택한 이미지 표시
            selectedImageView.setImageURI(uri)
            selectedImageView.visibility = View.VISIBLE
            
            // 상태 텍스트 업데이트
            imageStatusText.text = "이미지가 선택되었습니다"
            
            android.util.Log.d("MainActivity", "이미지 표시 완료")
            Toast.makeText(this, "이미지가 선택되었습니다", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "이미지 표시 실패: ${e.message}", e)
            Toast.makeText(this, "이미지 로드 중 오류가 발생했습니다", Toast.LENGTH_LONG).show()
            
            // 오류 시 상태 복구
            findViewById<LinearLayout>(R.id.uploadPrompt).visibility = View.VISIBLE
            selectedImageView.visibility = View.GONE
            selectedImageUri = null
        }
    }
    
    private fun showHelpDialog() {
        val helpContent = """
            <div style="text-align: center;"><h2><b>VERISCOPE 사용법</b></h2></div><br/>
            
            🔍 URL로 검사하기:<br/>
            • 확인하고 싶은 뉴스 기사의 URL을 복사하여 입력창에 붙여넣으세요<br/>
            • '신뢰도 평가 시작' 버튼을 누르면 AI가 뉴스의 신뢰도를 분석합니다<br/>
            • 결과에서 신뢰도 점수와 근거 자료를 확인할 수 있습니다<br/><br/>
            
            📸 이미지로 검사하기:<br/>
            • '이미지로 검사' 탭을 선택하세요<br/>
            • 카메라로 촬영하거나 갤러리에서 뉴스<br\>스크린샷을 선택하세요<br/>
            • '신뢰도 평가 시작' 버튼으로 이미지 내<br\>텍스트를 분석합니다<br/><br/>
            
            🎯 신뢰도 점수:<br/>
            • <font color='#2196F3'><b>70% 이상: 신뢰할 수 있음</b></font><br/>
            • <font color='#FF9800'><b>40-69%: 주의 필요</b></font><br/>
            • <font color='#F44336'><b>40% 미만: 신뢰하기 어려움</b></font><br/><br/>
            
            📊 추가 기능:<br/>
            • 제보하기: 70% 미만의 의심스러운 콘텐츠를 신고할 수 있습니다<br/>
            • 리워드 확인: 검사 및 제보 활동으로 포인트를 적립하세요<br/>
            • 마이페이지: 검사 이력과 통계를 확인할 수<br\>있습니다
        """.trimIndent()
        
        val spannedContent: Spanned = Html.fromHtml(helpContent, Html.FROM_HTML_MODE_LEGACY)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(spannedContent)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
