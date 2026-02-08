package com.example.veriscope

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {

    private lateinit var tvLoadingText: TextView
    private lateinit var ivLogoFinal: ImageView
    private lateinit var tvAppName: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var layoutContent: LinearLayout
    private lateinit var tvSignUp: TextView
    private lateinit var tvDivider: TextView
    private lateinit var tvLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // 시스템 스플래시 스크린 설치
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        initViews()
        startLoadingAnimation()
    }

    private fun initViews() {
        tvLoadingText = findViewById(R.id.tvLoadingText)
        ivLogoFinal = findViewById(R.id.ivLogoFinal)
        tvAppName = findViewById(R.id.tvAppName)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        layoutContent = findViewById(R.id.layoutContent)
        tvSignUp = findViewById(R.id.tvSignUp)
        tvDivider = findViewById(R.id.tvDivider)
        tvLogin = findViewById(R.id.tvLogin)

        // 초기에 모든 요소를 숨김
        ivLogoFinal.visibility = View.INVISIBLE
        tvAppName.visibility = View.INVISIBLE
        tvSubtitle.visibility = View.INVISIBLE
        tvSignUp.visibility = View.INVISIBLE
        tvDivider.visibility = View.INVISIBLE
        tvLogin.visibility = View.INVISIBLE

        // 클릭 리스너 설정
        tvSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
        
        tvLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startLoadingAnimation() {
        // 2초 후 로고 변형 시작 (회전 애니메이션 제거)
        Handler(Looper.getMainLooper()).postDelayed({
            animateLogoTransform()
        }, 2000)
    }

    private fun animateLogoTransform() {
        // 중앙 텍스트를 페이드 아웃
        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 400
            fillAfter = true
        }
        
        tvLoadingText.startAnimation(fadeOut)
        
        // 페이드 아웃 완료 후 최종 로고를 최종 위치에 표시
        Handler(Looper.getMainLooper()).postDelayed({
            tvLoadingText.visibility = View.GONE
            showFinalLogo()
        }, 400)
    }
    
    private fun showFinalLogo() {
        // 로고 이미지 표시
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 600
            fillAfter = true
        }
        
        val scaleIn = ScaleAnimation(0.8f, 1f, 0.8f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 600
            fillAfter = true
        }
        
        val animationSet = AnimationSet(false).apply {
            addAnimation(fadeIn)
            addAnimation(scaleIn)
        }
        
        ivLogoFinal.visibility = View.VISIBLE
        ivLogoFinal.startAnimation(animationSet)
        
        // 로고 표시 후 텍스트들을 순차적으로 표시
        Handler(Looper.getMainLooper()).postDelayed({
            showAppName()
        }, 400)
    }

    private fun showAppName() {
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 600
            fillAfter = true
        }

        tvAppName.visibility = View.VISIBLE
        tvAppName.startAnimation(fadeIn)

        // 0.5초 후 부제목 표시
        Handler(Looper.getMainLooper()).postDelayed({
            showSubtitle()
        }, 500)
    }

    private fun showSubtitle() {
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 600
            fillAfter = true
        }

        tvSubtitle.visibility = View.VISIBLE
        tvSubtitle.startAnimation(fadeIn)

        // 0.5초 후 버튼 표시
        Handler(Looper.getMainLooper()).postDelayed({
            showButton()
        }, 500)
    }

    private fun showButton() {
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 600
            fillAfter = true
        }

        val slideUp = TranslateAnimation(0f, 0f, 100f, 0f).apply {
            duration = 600
            fillAfter = true
        }

        val scaleUp = ScaleAnimation(0.9f, 1f, 0.9f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 600
            fillAfter = true
        }

        val animationSet = AnimationSet(false).apply {
            addAnimation(fadeIn)
            addAnimation(slideUp)
            addAnimation(scaleUp)
        }

        // 회원가입 버튼 애니메이션
        tvSignUp.visibility = View.VISIBLE
        tvSignUp.startAnimation(animationSet)
        
        // 구분선 및 로그인 버튼 애니메이션: 지연 제거 — 회원가입 버튼과 동시에 표시
        tvDivider.visibility = View.VISIBLE
        tvDivider.startAnimation(animationSet)

        tvLogin.visibility = View.VISIBLE
        tvLogin.startAnimation(animationSet)
    }
}