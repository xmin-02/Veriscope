package com.example.veriscope.utils

import android.content.Context
import android.content.SharedPreferences

class ReportedItemsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "reported_items"
        private const val KEY_REPORTED_URLS = "reported_urls"
        
        @Volatile
        private var INSTANCE: ReportedItemsManager? = null
        
        fun getInstance(context: Context): ReportedItemsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReportedItemsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun addReportedItem(url: String) {
        val reportedItems = getReportedItems().toMutableSet()
        reportedItems.add(url)
        
        prefs.edit()
            .putStringSet(KEY_REPORTED_URLS, reportedItems)
            .apply()
            
        android.util.Log.d("ReportedItemsManager", "제보된 항목 추가: $url")

    }

    fun isItemReported(url: String): Boolean {
        return getReportedItems().contains(url)
    }

    fun getReportedItems(): Set<String> {
        return prefs.getStringSet(KEY_REPORTED_URLS, emptySet()) ?: emptySet()
    }

    fun removeReportedItem(url: String) {
        val reportedItems = getReportedItems().toMutableSet()
        reportedItems.remove(url)
        
        prefs.edit()
            .putStringSet(KEY_REPORTED_URLS, reportedItems)
            .apply()
    }

    fun clearAllReportedItems() {
        prefs.edit()
            .remove(KEY_REPORTED_URLS)
            .apply()
    }
}