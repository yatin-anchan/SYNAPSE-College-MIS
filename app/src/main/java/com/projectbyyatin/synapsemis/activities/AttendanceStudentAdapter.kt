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
import com.projectbyyatin.synapsemis.models.ClassStudent

/**
 * Adapter for the student list in AttendanceReportsActivity.
 * Shows each student with their attendance % for the selected subject + month.
 */
class AttendanceStudentAdapter(
    private val items: MutableList<Pair<ClassStudent, Float>>,
    private val onItemClick: (ClassStudent, Float) -> Unit
) : RecyclerView.Adapter<AttendanceStudentAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName        : TextView                = v.findViewById(R.id.tvStudentName)
        val tvRoll        : TextView                = v.findViewById(R.id.tvStudentRolls)
        val tvPercent     : TextView                = v.findViewById(R.id.tvAttendancePercents)
        val tvStatus      : TextView                = v.findViewById(R.id.tvAttendanceStatusChips)
        val progressBar   : LinearProgressIndicator = v.findViewById(R.id.attendanceProgressBars)
        val card          : MaterialCardView        = v.findViewById(R.id.cardStudentAttendances)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance_list, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (student, percent) = items[position]

        holder.tvName.text    = student.studentName
        holder.tvRoll.text    = "Roll: ${student.rollNumber}"
        holder.tvPercent.text = "${"%.1f".format(percent)}%"
        holder.progressBar.progress = percent.toInt()

        when {
            percent >= 75f -> {
                holder.tvStatus.text = "Safe"
                holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_green)
                holder.progressBar.setIndicatorColor(Color.parseColor("#43A047"))
            }
            percent >= 60f -> {
                holder.tvStatus.text = "At Risk"
                holder.tvStatus.setTextColor(Color.parseColor("#E65100"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_orange)
                holder.progressBar.setIndicatorColor(Color.parseColor("#FB8C00"))
            }
            else -> {
                holder.tvStatus.text = "Critical"
                holder.tvStatus.setTextColor(Color.parseColor("#B71C1C"))
                holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_red)
                holder.progressBar.setIndicatorColor(Color.parseColor("#E53935"))
            }
        }

        holder.card.setOnClickListener { onItemClick(student, percent) }
    }
}