package com.example.veriscope.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.veriscope.data.User

class UserManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_REMEMBER_EMAIL = "remember_email"
        private const val KEY_REMEMBERED_EMAIL = "remembered_email"
        private const val KEY_AUTO_LOGIN = "auto_login"
    }
    
    fun saveUser(user: User) {
        sharedPreferences.edit().apply {
            putInt(KEY_USER_ID, user.id ?: 0)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_EMAIL, user.email)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }
    
    fun getCurrentUser(): User? {
        if (!isLoggedIn()) return null
        
        val id = sharedPreferences.getInt(KEY_USER_ID, 0)
        val name = sharedPreferences.getString(KEY_USER_NAME, "") ?: ""
        val email = sharedPreferences.getString(KEY_USER_EMAIL, "") ?: ""
        
        return if (id > 0 && name.isNotEmpty() && email.isNotEmpty()) {
            User(id, name, email, "")
        } else null
    }
    
    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun logout() {
        // 로그아웃 시 사용자 정보만 삭제하고, 이메일 기억 설정은 유지
        val rememberEmail = sharedPreferences.getBoolean(KEY_REMEMBER_EMAIL, false)
        val rememberedEmail = sharedPreferences.getString(KEY_REMEMBERED_EMAIL, "")
        
        sharedPreferences.edit().clear().apply()
        
        // 이메일 기억 설정이 있었다면 복원
        if (rememberEmail && !rememberedEmail.isNullOrEmpty()) {
            sharedPreferences.edit().apply {
                putBoolean(KEY_REMEMBER_EMAIL, true)
                putString(KEY_REMEMBERED_EMAIL, rememberedEmail)
                apply()
            }
        }
    }
    
    fun setRememberEmail(remember: Boolean, email: String = "") {
        sharedPreferences.edit().apply {
            putBoolean(KEY_REMEMBER_EMAIL, remember)
            if (remember && email.isNotEmpty()) {
                putString(KEY_REMEMBERED_EMAIL, email)
            } else if (!remember) {
                remove(KEY_REMEMBERED_EMAIL)
            }
            apply()
        }
    }
    
    fun isRememberEmailEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_REMEMBER_EMAIL, false)
    }
    
    fun getRememberedEmail(): String {
        return sharedPreferences.getString(KEY_REMEMBERED_EMAIL, "") ?: ""
    }
    
    fun setAutoLogin(autoLogin: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_AUTO_LOGIN, autoLogin)
            apply()
        }
    }
    
    fun isAutoLoginEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_LOGIN, false)
    }
}