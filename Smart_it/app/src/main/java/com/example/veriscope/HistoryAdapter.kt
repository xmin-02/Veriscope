package com.example.veriscope

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.veriscope.data.CheckHistory

class HistoryAdapter(
    private val onItemClick: (CheckHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var historyList = listOf<CheckHistory>()

    fun updateHistory(newHistory: List<CheckHistory>) {
        historyList = newHistory
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val history = historyList[position]
        holder.bind(history)
    }

    override fun getItemCount(): Int = historyList.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeIcon: ImageView = itemView.findViewById(R.id.typeIcon)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val reliabilityScore: TextView = itemView.findViewById(R.id.reliabilityScore)
        private val reliabilityLabel: TextView = itemView.findViewById(R.id.reliabilityLabel)
        private val checkedAtText: TextView = itemView.findViewById(R.id.checkedAtText)
        private val reliabilityIndicator: View = itemView.findViewById(R.id.reliabilityIndicator)

        fun bind(history: CheckHistory) {
            titleText.text = history.title
            checkedAtText.text = history.checkedAt
            
            // 타입 아이콘 설정
            when (history.type) {
                "URL" -> {
                    typeIcon.setImageResource(R.drawable.ic_link)
                    typeIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.primary_blue))
                }
                "IMAGE" -> {
                    typeIcon.setImageResource(R.drawable.ic_image)
                    typeIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.orange))
                }
            }

            // 신뢰도 점수 및 라벨 설정
            reliabilityScore.text = "${history.reliabilityScore.toInt()}%"
            
            when {
                history.reliabilityScore >= 70 -> {
                    reliabilityLabel.text = "신뢰할 수 있음"
                    val color = ContextCompat.getColor(itemView.context, R.color.green)
                    reliabilityLabel.setTextColor(color)
                    reliabilityScore.setTextColor(color)
                    ViewCompat.setBackgroundTintList(reliabilityIndicator, ColorStateList.valueOf(color))
                }
                history.reliabilityScore >= 40 -> {
                    reliabilityLabel.text = "주의 필요"
                    val color = ContextCompat.getColor(itemView.context, R.color.orange)
                    reliabilityLabel.setTextColor(color)
                    reliabilityScore.setTextColor(color)
                    ViewCompat.setBackgroundTintList(reliabilityIndicator, ColorStateList.valueOf(color))
                }
                else -> {
                    reliabilityLabel.text = "신뢰하기 어려움"
                    val color = ContextCompat.getColor(itemView.context, R.color.red)
                    reliabilityLabel.setTextColor(color)
                    reliabilityScore.setTextColor(color)
                    ViewCompat.setBackgroundTintList(reliabilityIndicator, ColorStateList.valueOf(color))
                }
            }

            // 클릭 리스너
            itemView.setOnClickListener {
                onItemClick(history)
            }
        }
    }
}