package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.StudentAttendance

class StudentAttendanceAdapter(
    private var studentList: MutableList<StudentAttendance>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tv_student_name)
        val tvRollNumber: TextView = view.findViewById(R.id.tv_roll_number)
        val tvEmail: TextView = view.findViewById(R.id.tv_email)
        val btnToggleStatus: MaterialButton = view.findViewById(R.id.btn_toggle_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_attendance, parent, false)
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
        holder.tvEmail.text = student.email

        updateStatusButton(holder.btnToggleStatus, student.status)

        holder.btnToggleStatus.setOnClickListener {
            // Toggle status
            student.status = if (student.status == "present") "absent" else "present"
            updateStatusButton(holder.btnToggleStatus, student.status)
            onStatusChanged()
        }
    }

    private fun updateStatusButton(button: MaterialButton, status: String) {
        if (status == "present") {
            button.text = "Present"
            button.setBackgroundColor(
                ContextCompat.getColor(button.context, android.R.color.holo_green_dark)
            )
            button.setTextColor(
                ContextCompat.getColor(button.context, android.R.color.white)
            )
        } else {
            button.text = "Absent"
            button.setBackgroundColor(
                ContextCompat.getColor(button.context, android.R.color.holo_red_dark)
            )
            button.setTextColor(
                ContextCompat.getColor(button.context, android.R.color.white)
            )
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

    fun getStudentList(): List<StudentAttendance> = studentList

    override fun getItemCount() = studentList.size
}