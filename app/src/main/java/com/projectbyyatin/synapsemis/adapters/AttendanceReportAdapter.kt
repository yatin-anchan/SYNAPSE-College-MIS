package com.projectbyyatin.synapsemis.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.AttendanceReport
import java.text.SimpleDateFormat
import java.util.*

class AttendanceReportAdapter(
    private val reports: List<AttendanceReport>,
    private val onItemClick: (AttendanceReport) -> Unit
) : RecyclerView.Adapter<AttendanceReportAdapter.ReportViewHolder>() {

    inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate         : TextView = itemView.findViewById(R.id.tv_date)
        val tvSubjectName  : TextView = itemView.findViewById(R.id.tv_subject_name)
        val tvPercentage   : TextView = itemView.findViewById(R.id.tv_percentage)
        val tvTotalStudents: TextView = itemView.findViewById(R.id.tv_total_students)
        val tvPresentCount : TextView = itemView.findViewById(R.id.tv_present_count)
        val tvAbsentCount  : TextView = itemView.findViewById(R.id.tv_absent_count)
        val tvFacultyName  : TextView = itemView.findViewById(R.id.tv_faculty_name)
        val tvRecordedAt   : TextView = itemView.findViewById(R.id.tv_recorded_at)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]

        // ── Header: student name in tv_date, roll • subject in tv_subject_name ──
        holder.tvDate.text        = report.studentName.ifBlank { "—" }
        holder.tvSubjectName.text = buildRollSubjectLine(report.rollNumber, report.subjectName)

        // ── Stats grid ──
        holder.tvTotalStudents.text = report.totalStudents.toString()
        holder.tvPresentCount.text  = report.presentCount.toString()
        // absentCount = totalStudents - presentCount (handles late as present)
        holder.tvAbsentCount.text   = (report.totalStudents - report.presentCount).toString()

        // ── Percentage with colour ──
        val pct = report.percentage.toDouble()
        holder.tvPercentage.text = "%.1f%%".format(pct)
        holder.tvPercentage.setTextColor(percentageColor(holder.itemView, pct))

        // ── Footer ──
        holder.tvFacultyName.text = report.facultyName.ifBlank { "N/A" }
        holder.tvRecordedAt.text  = formatRecordedAt(report.recordedAt)

        holder.itemView.setOnClickListener { onItemClick(report) }
    }

    override fun getItemCount() = reports.size

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** "Roll: 23CS001  •  Data Structures" or just subject name if roll is blank */
    private fun buildRollSubjectLine(roll: String, subject: String): String {
        val r = roll.ifBlank { null }
        val s = subject.ifBlank { "—" }
        return if (r != null) "Roll: $r  •  $s" else s
    }

    /**
     * recordedAt is a String in your model (e.g. "03 Jan, 11:30 AM").
     * If it's already formatted just return it; otherwise try to parse as epoch ms.
     */
    private fun formatRecordedAt(recordedAt: String): String {
        if (recordedAt.isBlank()) return "—"
        // If it already looks like a formatted string (contains letters) return as-is
        if (recordedAt.any { it.isLetter() }) return recordedAt
        // Otherwise treat as epoch millis
        return try {
            val ms = recordedAt.toLong()
            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(ms))
        } catch (e: Exception) {
            recordedAt
        }
    }

    private fun percentageColor(view: View, pct: Double): Int {
        val ctx = view.context
        return when {
            pct >= 75.0 -> ctx.getColor(R.color.splash_success)
            pct >= 60.0 -> ctx.getColor(R.color.splash_warning)
            else        -> ctx.getColor(R.color.splash_error)
        }
    }
}