package com.projectbyyatin.synapsemis.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.StudentAttendanceAnalyticsActivity
import com.projectbyyatin.synapsemis.views.AttendanceSparklineView

class StudentAnalyticsAdapter(
    private val list: List<StudentAttendanceAnalyticsActivity.StudentSummary>
) : RecyclerView.Adapter<StudentAnalyticsAdapter.ViewHolder>() {

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val card        : MaterialCardView        = v.findViewById(R.id.cardStudentAnalytics)
        val tvName      : TextView                = v.findViewById(R.id.tvAnalyticsName)
        val tvRoll      : TextView                = v.findViewById(R.id.tvAnalyticsRoll)
        val tvPct       : TextView                = v.findViewById(R.id.tvAnalyticsPct)
        val tvStatus    : TextView                = v.findViewById(R.id.tvAnalyticsStatus)
        val tvTrend     : TextView                = v.findViewById(R.id.tvAnalyticsTrend)
        val tvPresent   : TextView                = v.findViewById(R.id.tvAnalyticsPresent)
        val progressBar : LinearProgressIndicator = v.findViewById(R.id.progressAnalytics)
        val sparkline   : AttendanceSparklineView = v.findViewById(R.id.sparklineView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_student_analytics, parent, false)
    )

    override fun getItemCount() = list.size

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val s = list[pos]

        h.tvName.text    = s.studentName
        h.tvRoll.text    = "Roll: ${s.rollNo}"
        h.tvPct.text     = "${"%.1f".format(s.percentage)}%"
        h.tvPresent.text = "${s.present}/${s.total} classes attended"
        h.progressBar.progress = s.percentage.toInt()

        val (riskLabel, riskColor) = when (s.risk) {
            "high"   -> "🔴 HIGH RISK" to Color.parseColor("#FF5252")
            "medium" -> "🟡 AT RISK"   to Color.parseColor("#FFB300")
            else     -> "🟢 SAFE"      to Color.parseColor("#00C853")
        }
        h.tvStatus.text = riskLabel
        h.tvStatus.setTextColor(riskColor)
        h.tvPct.setTextColor(riskColor)
        h.progressBar.setIndicatorColor(riskColor)
        h.card.strokeColor = riskColor
        h.card.strokeWidth = 2

        h.tvTrend.text = when (s.trend) {
            "improving" -> "📈 Improving"
            "declining" -> "📉 Declining"
            else        -> "➡️ Stable"
        }

        if (s.monthlyPcts.size >= 2) {
            h.sparkline.visibility = View.VISIBLE
            h.sparkline.setData(s.monthlyPcts)
        } else {
            h.sparkline.visibility = View.GONE
        }
    }
}