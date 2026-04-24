package com.projectbyyatin.synapsemis.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.OverallAttendanceSummary
import com.projectbyyatin.synapsemis.R
import de.hdodenhof.circleimageview.CircleImageView

class OverallAttendanceAdapter(
    private var summaryList: List<OverallAttendanceSummary>
) : RecyclerView.Adapter<OverallAttendanceAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.profile_image)
        val studentName: TextView = itemView.findViewById(R.id.student_name)
        val studentRollNo: TextView = itemView.findViewById(R.id.student_roll_no)
        val overallPercentage: TextView = itemView.findViewById(R.id.overall_percentage)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        val presentCount: TextView = itemView.findViewById(R.id.present_count)
        val absentCount: TextView = itemView.findViewById(R.id.absent_count)
        val lateCount: TextView = itemView.findViewById(R.id.late_count)
        val totalDays: TextView = itemView.findViewById(R.id.total_days)
        val semesterBreakdownContainer: LinearLayout = itemView.findViewById(R.id.semester_breakdown_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_overall_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val summary = summaryList[position]

        holder.studentName.text = summary.studentName
        holder.studentRollNo.text = "Roll No: ${summary.studentRollNo}"
        holder.overallPercentage.text = "${String.format("%.1f", summary.percentage)}%"
        holder.progressBar.progress = summary.percentage.toInt()

        holder.presentCount.text = "${summary.presentCount}"
        holder.absentCount.text = "${summary.absentCount}"
        holder.lateCount.text = "${summary.lateCount}"
        holder.totalDays.text = "${summary.totalDays}"

        // Color code percentage
        val color = when {
            summary.percentage >= 75 -> Color.parseColor("#4CAF50")
            summary.percentage >= 50 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
        holder.overallPercentage.setTextColor(color)
        holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(color)

        // Display semester-wise breakdown
        holder.semesterBreakdownContainer.removeAllViews()
        summary.semesterWiseData.toSortedMap().forEach { (semester, data) ->
            val semesterView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.item_semester_breakdown, holder.semesterBreakdownContainer, false)

            val semesterText = semesterView.findViewById<TextView>(R.id.semester_text)
            val semesterPercentage = semesterView.findViewById<TextView>(R.id.semester_percentage)
            val semesterDetails = semesterView.findViewById<TextView>(R.id.semester_details)

            semesterText.text = "Sem $semester"
            semesterPercentage.text = "${String.format("%.1f", data.getPercentage())}%"
            semesterDetails.text = "P:${data.present} A:${data.absent} L:${data.late} (${data.total})"

            holder.semesterBreakdownContainer.addView(semesterView)
        }
    }

    override fun getItemCount(): Int = summaryList.size

    fun updateList(newList: List<OverallAttendanceSummary>) {
        summaryList = newList
        notifyDataSetChanged()
    }
}
