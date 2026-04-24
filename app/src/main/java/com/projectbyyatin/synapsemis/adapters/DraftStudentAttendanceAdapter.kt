package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.StudentAttendance

class DraftStudentAttendanceAdapter(
    private var studentList: MutableList<StudentAttendance>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<DraftStudentAttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.card_student)
        val tvStudentName: TextView = view.findViewById(R.id.tv_student_name)
        val tvRollNumber: TextView = view.findViewById(R.id.tv_roll_number)
        val tvStatus: TextView = view.findViewById(R.id.tv_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_draft_student_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = studentList[position]

        holder.tvStudentName.text = student.studentName
        holder.tvRollNumber.text = if (student.rollNumber.isEmpty()) {
            "Roll: Not Assigned"
        } else {
            "Roll: ${student.rollNumber}"
        }

        updateStatusView(holder, student.status)

        // Click to toggle status
        holder.cardView.setOnClickListener {
            student.status = if (student.status == "present") "absent" else "present"
            updateStatusView(holder, student.status)
            onStatusChanged()
        }
    }

    private fun updateStatusView(holder: ViewHolder, status: String) {
        if (status == "present") {
            holder.tvStatus.text = "Present"
            holder.tvStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.splash_success)
            )
            holder.cardView.strokeColor =
                ContextCompat.getColor(holder.itemView.context, R.color.splash_success)
        } else {
            holder.tvStatus.text = "Absent"
            holder.tvStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.splash_error)
            )
            holder.cardView.strokeColor =
                ContextCompat.getColor(holder.itemView.context, R.color.splash_error)
        }
    }

    fun markAllPresent() {
        studentList.forEach { it.status = "present" }
        notifyDataSetChanged()
        onStatusChanged()
    }

    fun markAllAbsent() {
        studentList.forEach { it.status = "absent" }
        notifyDataSetChanged()
        onStatusChanged()
    }

    override fun getItemCount() = studentList.size
}