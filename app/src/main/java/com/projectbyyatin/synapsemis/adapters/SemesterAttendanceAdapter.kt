package com.projectbyyatin.synapsemis.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.StudentAttendanceSummary
import de.hdodenhof.circleimageview.CircleImageView

class SemesterAttendanceAdapter(
    private var summaryList: List<StudentAttendanceSummary>
) : RecyclerView.Adapter<SemesterAttendanceAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.profile_image)
        val studentName: TextView = itemView.findViewById(R.id.student_name)
        val studentRollNo: TextView = itemView.findViewById(R.id.student_roll_no)
        val percentageText: TextView = itemView.findViewById(R.id.percentage_text)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        val presentCount: TextView = itemView.findViewById(R.id.present_count)
        val absentCount: TextView = itemView.findViewById(R.id.absent_count)
        val lateCount: TextView = itemView.findViewById(R.id.late_count)
        val totalDays: TextView = itemView.findViewById(R.id.total_days)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_semester_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val summary = summaryList[position]

        holder.studentName.text = summary.studentName
        holder.studentRollNo.text = "Roll No: ${summary.studentRollNo}"
        holder.percentageText.text = "${String.format("%.1f", summary.percentage)}%"
        holder.progressBar.progress = summary.percentage.toInt()

        holder.presentCount.text = "P: ${summary.presentCount}"
        holder.absentCount.text = "A: ${summary.absentCount}"
        holder.lateCount.text = "L: ${summary.lateCount}"
        holder.totalDays.text = "Total: ${summary.totalDays}"

        // Color code percentage
        val color = when {
            summary.percentage >= 75 -> Color.parseColor("#4CAF50")
            summary.percentage >= 50 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
        holder.percentageText.setTextColor(color)
        holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
    }

    override fun getItemCount(): Int = summaryList.size

    fun updateList(newList: List<StudentAttendanceSummary>) {
        summaryList = newList
        notifyDataSetChanged()
    }
}
