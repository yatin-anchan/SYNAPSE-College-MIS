package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.CoeMarksPublishActivity.SubjectStatusItem

class PublishSubjectsAdapter(
    private var subjectsList: List<SubjectStatusItem>,
    private val onModerateClick: (SubjectStatusItem) -> Unit
) : RecyclerView.Adapter<PublishSubjectsAdapter.SubjectViewHolder>() {

    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subjectCode: TextView = itemView.findViewById(R.id.subject_code)
        val subjectName: TextView = itemView.findViewById(R.id.subject_name)
        val courseName: TextView = itemView.findViewById(R.id.course_name)
        val statusChip: Chip = itemView.findViewById(R.id.status_chip)
        val studentsCount: TextView = itemView.findViewById(R.id.students_count)
        val submittedBy: TextView = itemView.findViewById(R.id.submitted_by)
        val moderatedBy: TextView = itemView.findViewById(R.id.moderated_by)
        val moderateButton: ImageView = itemView.findViewById(R.id.moderate_button)
        val checkIcon: ImageView = itemView.findViewById(R.id.check_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_publish_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val item = subjectsList[position]
        val status = item.status

        holder.subjectCode.text = item.subjectCode
        holder.subjectName.text = item.subjectName
        holder.courseName.text = item.courseName

        // Students count
        if (status.totalStudents > 0) {
            holder.studentsCount.text = "${status.marksEntered} / ${status.totalStudents} students"
        } else {
            holder.studentsCount.text = "No students enrolled"
        }

        // Submitted by info
        if (status.marksSubmitted) {
            holder.submittedBy.visibility = View.VISIBLE
            holder.submittedBy.text = "Submitted: ${formatDate(status.submittedAt)}"
        } else {
            holder.submittedBy.visibility = View.GONE
        }

        // Moderated by info
        if (status.moderated) {
            holder.moderatedBy.visibility = View.VISIBLE
            holder.moderatedBy.text = "Moderated: ${formatDate(status.moderatedAt)}"
        } else {
            holder.moderatedBy.visibility = View.GONE
        }

        // Status chip and icons
        when {
            status.readyForPublish -> {
                holder.statusChip.text = "Ready"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
                holder.checkIcon.visibility = View.VISIBLE
                holder.moderateButton.visibility = View.VISIBLE
            }
            status.marksSubmitted -> {
                holder.statusChip.text = "Pending Moderation"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
                holder.checkIcon.visibility = View.GONE
                holder.moderateButton.visibility = View.VISIBLE
            }
            else -> {
                holder.statusChip.text = "Not Submitted"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.darker_gray)
                holder.checkIcon.visibility = View.GONE
                holder.moderateButton.visibility = View.GONE
            }
        }

        // Moderate button click
        holder.moderateButton.setOnClickListener {
            onModerateClick(item)
        }

        holder.itemView.setOnClickListener {
            onModerateClick(item)
        }
    }

    override fun getItemCount(): Int = subjectsList.size

    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun updateList(newList: List<SubjectStatusItem>) {
        subjectsList = newList
        notifyDataSetChanged()
    }
}