package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.AttendanceDraft
import java.text.SimpleDateFormat
import java.util.*

class AttendanceDraftsAdapter(
    private var draftsList: MutableList<AttendanceDraft>,
    private val onViewClick: (AttendanceDraft) -> Unit,
    private val onDeleteClick: (AttendanceDraft) -> Unit
) : RecyclerView.Adapter<AttendanceDraftsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.card_draft)
        val tvClassName: TextView = view.findViewById(R.id.tv_class_name)
        val tvSubjectName: TextView = view.findViewById(R.id.tv_subject_name)
        val tvDateTime: TextView = view.findViewById(R.id.tv_date_time)
        val tvCreatedBy: TextView = view.findViewById(R.id.tv_created_by)
        val tvStudentCount: TextView = view.findViewById(R.id.tv_student_count)
        val tvPresentCount: TextView = view.findViewById(R.id.tv_present_count)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_draft, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val draft = draftsList[position]

        holder.tvClassName.text = draft.className ?: "Unknown Class"
        holder.tvSubjectName.text = draft.subjectName ?: "Unknown Subject"

        val dateTime = "${draft.date ?: "N/A"} | ${draft.startTime ?: "N/A"} - ${draft.endTime ?: "N/A"}"
        holder.tvDateTime.text = dateTime

        holder.tvCreatedBy.text = "By: ${draft.createdBy ?: "Unknown"}"

        val totalStudents = draft.totalStudents ?: 0
        val presentCount = draft.presentCount ?: 0
        val percentage = if (totalStudents > 0) {
            (presentCount.toDouble() / totalStudents * 100)
        } else 0.0

        holder.tvStudentCount.text = "Total: $totalStudents"
        holder.tvPresentCount.text = "Present: $presentCount (${String.format("%.1f", percentage)}%)"

        // Click on card to view details
        holder.cardView.setOnClickListener {
            onViewClick(draft)
        }

        // Delete button
        holder.btnDelete.setOnClickListener {
            onDeleteClick(draft)
        }
    }

    override fun getItemCount() = draftsList.size

    fun updateList(newList: List<AttendanceDraft>) {
        draftsList.clear()
        draftsList.addAll(newList)
        notifyDataSetChanged()
    }
}