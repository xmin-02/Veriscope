package com.example.veriscope

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.veriscope.data.CheckHistory
import android.graphics.BitmapFactory
import java.io.File

class ReportHistoryAdapter(
    private val onReportClick: (CheckHistory) -> Unit
) : RecyclerView.Adapter<ReportHistoryAdapter.ReportHistoryViewHolder>() {

    private var historyList = mutableListOf<CheckHistory>()
    private var reportedItems = setOf<String>()

    fun updateHistory(newHistory: List<CheckHistory>) {
        historyList.clear()
        historyList.addAll(newHistory)
        notifyDataSetChanged()
    }
    
    fun updateReportedItems(reportedItems: Set<String>) {
        this.reportedItems = reportedItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_history, parent, false)
        return ReportHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportHistoryViewHolder, position: Int) {
        holder.bind(historyList[position])
    }

    override fun getItemCount(): Int = historyList.size

    inner class ReportHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeIcon: ImageView = itemView.findViewById(R.id.typeIcon)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val reliabilityBadge: TextView = itemView.findViewById(R.id.reliabilityBadge)
        private val levelText: TextView = itemView.findViewById(R.id.levelText)
        private val reportButton: Button = itemView.findViewById(R.id.reportButton)
        private val previewImage: ImageView = itemView.findViewById(R.id.previewImage)

        fun bind(history: CheckHistory) {
            // 타입별 아이콘 설정
            if (history.type == "IMAGE") {
                typeIcon.setImageResource(R.drawable.ic_image)
                typeIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.orange))
            } else {
                typeIcon.setImageResource(R.drawable.ic_link)
                typeIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.primary_blue))
            }

            titleText.text = history.title
            dateText.text = history.checkedAt

            // 이미지 타입인 경우 저장된 이미지 표시
            if (history.type == "IMAGE" && !history.savedImagePath.isNullOrEmpty()) {
                previewImage.visibility = View.VISIBLE
                loadImageFromPath(history.savedImagePath!!)
            } else {
                previewImage.visibility = View.GONE
            }

            // 신뢰도 배지 설정
            val score = history.reliabilityScore.toInt()
            reliabilityBadge.text = "${score}%"
            
            when {
                score >= 70 -> {
                    reliabilityBadge.background = ContextCompat.getDrawable(itemView.context, R.drawable.button_primary)
                    levelText.text = "신뢰할 수 있음"
                    levelText.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary_blue))
                }
                score >= 40 -> {
                    reliabilityBadge.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.orange))
                    levelText.text = "주의 필요"
                    levelText.setTextColor(ContextCompat.getColor(itemView.context, R.color.orange))
                }
                else -> {
                    reliabilityBadge.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.red))
                    levelText.text = "신뢰하기 어려움"
                    levelText.setTextColor(ContextCompat.getColor(itemView.context, R.color.red))
                }
            }

            // 제보 가능한 항목만 버튼 표시 (70% 미만)
            if (score < 70) {
                reportButton.visibility = View.VISIBLE
                
                // 제보 완료 여부 확인
                val isReported = checkIfReported(history)
                
                if (isReported) {
                    reportButton.text = "제보 완료"
                    reportButton.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                    reportButton.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    reportButton.isEnabled = false
                    reportButton.setOnClickListener(null) // 클릭 리스너 완전 제거
                    reportButton.alpha = 0.6f // 시각적으로 비활성화 표시
                    
                    android.util.Log.d("ReportHistoryAdapter", "제보 완료된 항목: ${history.title}")
                } else {
                    reportButton.text = "제보하기"
                    reportButton.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.primary_blue))
                    reportButton.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    reportButton.isEnabled = true
                    reportButton.alpha = 1.0f // 완전히 활성화 표시
                    reportButton.setOnClickListener {
                        android.util.Log.d("ReportHistoryAdapter", "제보하기 버튼 클릭: ${history.title}")
                        onReportClick(history)
                    }
                }
            } else {
                reportButton.visibility = View.GONE
            }
        }

        private fun checkIfReported(history: CheckHistory): Boolean {
            val possibleIds = mutableListOf<String>()
            
            when (history.type) {
                "URL" -> {
                    if (!history.url.isNullOrEmpty()) {
                        possibleIds.add(history.url!!)
                    }
                    possibleIds.add("unknown_${history.id}")
                }
                "IMAGE" -> {
                    if (!history.url.isNullOrEmpty()) {
                        possibleIds.add(history.url!!)
                    }
                    if (!history.savedImagePath.isNullOrEmpty()) {
                        possibleIds.add(history.savedImagePath!!)
                    }
                    possibleIds.add("unknown_${history.id}")
                }
                else -> {
                    possibleIds.add("general_${history.id}")
                }
            }
            
            return possibleIds.any { id -> reportedItems.contains(id) }
        }

        private fun loadImageFromPath(imagePath: String) {
            try {
                val file = File(imagePath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    previewImage.setImageBitmap(bitmap)
                } else {
                    previewImage.visibility = View.GONE
                    android.util.Log.w("ReportHistoryAdapter", "이미지 파일이 존재하지 않음: $imagePath")
                }
            } catch (e: Exception) {
                previewImage.visibility = View.GONE
                android.util.Log.e("ReportHistoryAdapter", "이미지 로딩 실패", e)
            }
        }
    }
}