package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TermsActivity : AppCompatActivity() {

    private lateinit var cbAgree: CheckBox
    private lateinit var layoutButtons: LinearLayout
    private lateinit var btnProceed: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvSectionTitle: TextView
    private lateinit var layoutTermsContent: LinearLayout
    private var agreementType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        // 약관 타입 확인
        agreementType = intent.getStringExtra("agreement_type")
        
        // 액션바에 뒤로가기 버튼 추가
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 제목 설정
        when (agreementType) {
            "terms" -> title = ""
            "privacy" -> title = ""
            else -> title = ""
        }

        initViews()
        setupContent()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun initViews() {
        cbAgree = findViewById(R.id.cbAgree)
        layoutButtons = findViewById(R.id.layoutButtons)
        btnProceed = findViewById(R.id.btnProceed)
        tvTitle = findViewById(R.id.tvTitle)
        tvSectionTitle = findViewById(R.id.tvSectionTitle)
        layoutTermsContent = findViewById(R.id.layoutTermsContent)
        
        // 초기에는 진행 버튼 숨김
        btnProceed.visibility = Button.GONE
    }

    private fun setupContent() {
        when (agreementType) {
            "terms" -> {
                setupTermsContent()
                cbAgree.text = "위 내용을 읽고 이해했으며 동의합니다"
            }
            "privacy" -> {
                setupPrivacyContent()
                cbAgree.text = "위 개인정보 내용을 읽고 이해했으며 동의합니다"
            }
            else -> {
                setupDefaultContent()
                cbAgree.text = "위 내용을 이해했으며 동의합니다"
            }
        }
    }
    
    private fun setupTermsContent() {
        tvTitle.text = "서비스 이용약관"
        tvSectionTitle.text = "이용약관"
        
        // 기존 내용 제거하고 새 내용 추가
        layoutTermsContent.removeAllViews()
        layoutTermsContent.addView(tvSectionTitle)
        
        addTermsContentView("1. VERISCOPE 서비스 소개", 
            "• VERISCOPE는 AI 기반 뉴스·이미지 신뢰도 검증\n플랫폼입니다.\n" +
            "• URL 또는 이미지를 통해 콘텐츠의 진위 판단 기능을\n제공합니다.\n" +
            "• 허위정보 제보 기능을 제공합니다.\n" +
            "• 포인트 시스템으로 사용자 참여를 유도합니다.")
            
        addTermsContentView("2. 서비스 이용 규칙",
            "• 서비스는 뉴스·이미지 검증 목적에 한해 이용해야\n합니다.\n" +
            "• 검증 결과는 참고용이며, 최종 판단 책임은 사용자에게 있습니다.\n" +
            "• 허위·악의적 제보는 금지됩니다.\n" +
            "• 개인정보·사생활 침해 콘텐츠 업로드는 금지됩니다.")
            
        addTermsContentView("3. 포인트 및 리워드 정책",
            "• 뉴스 검증 완료 시 5포인트가 지급됩니다.\n" +
            "• 허위뉴스 제보 승인 시 100포인트가 지급됩니다.\n" +
            "• 포인트 적립은 하루 최대 50포인트까지 가능합니다.\n" +
            "• 포인트는 지정된 리워드로 교환할 수 있습니다.\n" +
            "• 부정하게 획득한 포인트는 회수됩니다.")
            
        addTermsContentView("4. 허위정보 제보 시스템",
            "• 신뢰도 70% 미만 콘텐츠에 대해 제보할 수 있습니다.\n" +
            "• 제보 시 개인정보 수집·이용에 동의해야 합니다.\n" +
            "• 제보 내용은 관련 기관에 전달될 수 있습니다.\n" +
            "• 악의적 제보 시 서비스 이용이 제한될 수 있습니다.")
            
        addTermsContentView("5. 면책 조항",
            "• 검증 결과의 정확성은 보장되지 않습니다.\n" +
            "• 서비스 이용으로 발생한 손해에 대해 회사는 책임지지\n않습니다.\n" +
            "• 외부 링크 및 제3자 콘텐츠에 대한 책임은 사용자에게\n있습니다.\n" +
            "• 서비스는 사전 예고 없이 변경되거나 중단될 수\n있습니다.")
    }
    
    private fun setupPrivacyContent() {
        tvTitle.text = "개인정보 처리방침"
        tvSectionTitle.text = "개인정보 보호"
        
        // 기존 내용 제거하고 새 내용 추가
        layoutTermsContent.removeAllViews()
        layoutTermsContent.addView(tvSectionTitle)
        
        addTermsContentView("1. 개인정보 수집 및 이용목적",
            "• 회원가입 및 VERISCOPE 서비스 제공\n" +
            "• 뉴스 검증 이력 관리 및 포인트 적립\n" +
            "• 허위정보 제보 처리 및 관련 기관 전달\n" +
            "• 온누리 상품권 교환 서비스 제공\n" +
            "• 서비스 개선 및 사용자 지원\n" +
            "• 부정 이용 방지 및 보안 강화")
            
        addTermsContentView("2. 수집하는 개인정보 항목",
            "• 회원가입: 이름, 이메일, 전화번호, 비밀번호\n" +
            "• 뉴스 검증: 검증한 URL, 이미지 데이터\n" +
            "• 허위정보 제보: 제보자 이름, 이메일, 제보 내용\n" +
            "• 포인트 교환: 상품권 발급을 위한 연락처\n" +
            "• 자동수집: 접속 IP, 이용 시간, 기기 정보")
            
        addTermsContentView("3. 개인정보 보유 및 이용기간",
            "• 회원 정보: 탈퇴 시까지 또는 최종 로그인 후 3년\n" +
            "• 검증 이력: 서비스 이용 중 보관\n" +
            "• 제보 정보: 처리 완료 후 3년간 보관\n" +
            "• 포인트 내역: 교환 완료 후 5년간 보관\n" +
            "• 법령 보존: 관련 법률에 따른 의무 보관 기간")
            
        addTermsContentView("4. 개인정보 제3자 제공",
            "• 원칙적으로 사용자 동의 없이 제3자에게 제공하지 않음\n" +
            "• 허위정보 제보 시 방송통신위원회, 언론진흥재단 등 관련 기관에 제공\n" +
            "• 온누리 상품권 발급을 위한 상품권 발행사에 필요 정보 제공\n" +
            "• 법원, 검찰, 경찰 등의 수사기관 요청 시 제공\n" +
            "• 기타 법령에 의한 요구가 있는 경우")
            
        addTermsContentView("5. 개인정보 보호 조치",
            "• 개인정보 암호화 및 안전한 저장\n" +
            "• 접근 권한 제한 및 관리자 인증 시스템\n" +
            "• 정기적인 보안 점검 및 취약점 분석\n" +
            "• 개인정보 처리 직원 교육 및 서약서 작성\n" +
            "• 개인정보 침해신고센터 연계 및 신속 대응")
            
        addTermsContentView("6. 개인정보 처리 책임자",
            "• 개인정보 보호책임자: VERISCOPE 운영팀\n" +
            "• 연락처: smartit.ngms@gmail.com\n" +
            "• 개인정보 관련 문의, 불만 처리, 피해 구제 등에 관한 사항\n" +
            "• 개인정보 열람, 정정, 삭제, 처리정지 요구 등 권리 행사")
    }
    
    private fun setupDefaultContent() {
        // 기존 기본 내용 유지
        tvTitle.text = "📋 서비스 이용 안내"
        tvSectionTitle.text = "⚠️ 중요 안내사항"
    }
    
    private fun addTermsContentView(title: String, content: String) {
        // 제목 텍스트뷰
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(android.R.color.black, null))
            setPadding(0, 0, 0, 8)
        }
        
        // 내용 텍스트뷰
        val contentView = TextView(this).apply {
            text = content
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
            setPadding(0, 0, 0, 16)
            setLineSpacing(4f, 1f)
        }
        
        layoutTermsContent.addView(titleView)
        layoutTermsContent.addView(contentView)
    }

    private fun setupListeners() {
        cbAgree.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showProceedButton()
            } else {
                hideProceedButton()
            }
        }

        btnProceed.setOnClickListener {
            // 회원가입에서 온 경우만 결과 반환
            if (agreementType != null) {
                setResult(RESULT_OK)
                finish()
            } else {
                // 일반적인 경우 메인 화면으로 이동
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun showProceedButton() {
        // 체크박스는 그대로 두고 버튼만 자연스럽게 등장
        btnProceed.visibility = Button.VISIBLE
        val naturalAppear = AnimationUtils.loadAnimation(this, R.anim.button_natural_appear)
        btnProceed.startAnimation(naturalAppear)
    }

    private fun hideProceedButton() {
        // 버튼을 자연스럽게 사라지게 함
        val naturalDisappear = AnimationUtils.loadAnimation(this, R.anim.button_natural_disappear)
        btnProceed.startAnimation(naturalDisappear)
        
        // 애니메이션 완료 후 버튼 숨김
        naturalDisappear.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                btnProceed.visibility = Button.GONE
            }
        })
    }
}