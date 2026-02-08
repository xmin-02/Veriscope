package com.example.veriscope

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.veriscope.utils.ReportedItemsManager

class EvidenceAdapter(
    private val onItemClick: (Evidence) -> Unit
) : RecyclerView.Adapter<EvidenceAdapter.EvidenceViewHolder>() {
    
    private var evidenceList = mutableListOf<Evidence>()
    private var filteredEvidenceList = mutableListOf<Evidence>()
    private var reportedItemsManager: ReportedItemsManager? = null
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EvidenceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_evidence, parent, false)
        return EvidenceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: EvidenceViewHolder, position: Int) {
        holder.bind(filteredEvidenceList[position])
    }
    
    override fun getItemCount(): Int = filteredEvidenceList.size
    
    fun updateEvidence(newList: List<Evidence>) {
        evidenceList.clear()
        evidenceList.addAll(newList)
        filterEvidence()
        notifyDataSetChanged()
    }
    
    fun setReportedItemsManager(manager: ReportedItemsManager) {
        this.reportedItemsManager = manager
        filterEvidence()
        notifyDataSetChanged()
    }
    
    private fun filterEvidence() {
        filteredEvidenceList.clear()
        
        if (reportedItemsManager == null) {
            // ReportedItemsManager가 없으면 모든 항목 표시
            filteredEvidenceList.addAll(evidenceList)
        } else {
            // 제보되지 않은 항목만 필터링
            filteredEvidenceList.addAll(
                evidenceList.filter { evidence ->
                    !reportedItemsManager!!.isItemReported(evidence.url)
                }
            )
        }
    }
    
    inner class EvidenceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val evidenceNumber: TextView = itemView.findViewById(R.id.evidenceNumber)
        private val evidenceScore: TextView = itemView.findViewById(R.id.evidenceScore)
        private val evidenceUrl: TextView = itemView.findViewById(R.id.evidenceUrl)
        private val similarityText: TextView = itemView.findViewById(R.id.similarityText)
        private val supportText: TextView = itemView.findViewById(R.id.supportText)
        
        fun bind(evidence: Evidence) {
            evidenceNumber.text = evidence.number.toString()
            
            // score를 안전하게 변환 (0-1 범위를 0-100%로)
            val safeScore = when (val score = evidence.score) {
                is Int -> score
                is Double -> {
                    if (score <= 1.0) (score * 100).toInt() else score.toInt()
                }
                is Float -> {
                    if (score <= 1.0f) (score * 100).toInt() else score.toInt()
                }
                is String -> score.toIntOrNull() ?: 0
                else -> 0
            }
            
            // similarity를 안전하게 변환
            val safeSimilarity = evidence.similarity?.let { sim ->
                if (sim <= 1.0) (sim * 100).toInt() else sim.toInt()
            } ?: 0
            
            // support를 안전하게 변환
            val safeSupport = evidence.support?.let { sup ->
                if (sup <= 1.0) (sup * 100).toInt() else sup.toInt()
            } ?: 0
            
            evidenceScore.text = "${safeScore}%"
            evidenceUrl.text = formatUrl(evidence.url)
            similarityText.text = "${safeSimilarity}%"
            supportText.text = "${safeSupport}%"
            
            // 점수에 따른 색상 설정
            val scoreColor = when {
                safeScore >= 80 -> ContextCompat.getColor(itemView.context, R.color.primary_blue)
                safeScore >= 60 -> ContextCompat.getColor(itemView.context, R.color.orange)
                else -> ContextCompat.getColor(itemView.context, R.color.red)
            }
            evidenceScore.setTextColor(scoreColor)
            
            // 클릭 리스너 설정
            itemView.setOnClickListener {
                onItemClick(evidence)
            }
        }
        
        private fun formatUrl(url: String): String {
            // URL을 사용자 친화적으로 포맷팅
            return try {
                val uri = java.net.URI(url)
                val host = uri.host ?: url
                val path = uri.path
                
                if (host.length > 30) {
                    "${host.substring(0, 27)}..."
                } else if (path.isNotEmpty() && path != "/") {
                    "$host$path"
                } else {
                    host
                }
            } catch (e: Exception) {
                if (url.length > 40) {
                    "${url.substring(0, 37)}..."
                } else {
                    url
                }
            }
        }
    }
}