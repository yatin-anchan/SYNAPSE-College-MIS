package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Student
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class StudentsAdapter(
    private var studentsList: List<Student>,
    private val onViewClick: (Student) -> Unit
) : RecyclerView.Adapter<StudentsAdapter.StudentViewHolder>() {

    inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.profile_image)
        val studentName: TextView = itemView.findViewById(R.id.student_name)
        val studentId: TextView = itemView.findViewById(R.id.student_id)
        val rollNumber: TextView = itemView.findViewById(R.id.roll_number)
        val email: TextView = itemView.findViewById(R.id.email)
        val department: TextView = itemView.findViewById(R.id.department)
        val className: TextView = itemView.findViewById(R.id.class_name)
        val genderChip: Chip = itemView.findViewById(R.id.gender_chip)
        val statusChip: Chip = itemView.findViewById(R.id.status_chip)
        val attendanceText: TextView = itemView.findViewById(R.id.attendance_text)
        val cgpaText: TextView = itemView.findViewById(R.id.cgpa_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = studentsList[position]

        // Load profile image
        if (student.profileImageUrl.isNotEmpty()) {
            Picasso.get()
                .load(student.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(holder.profileImage)
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_person)
        }

        holder.studentName.text = student.fullName
        holder.studentId.text = student.studentId.ifEmpty { "Not Assigned" }
        holder.rollNumber.text = "Roll: ${student.rollNumber.ifEmpty { "N/A" }}"
        holder.email.text = student.email
        holder.department.text = student.departmentName.ifEmpty { "No Department" }
        holder.className.text = student.className.ifEmpty { "No Class" }

        // Gender chip
        holder.genderChip.text = student.gender.ifEmpty { "N/A" }
        val genderColor = when (student.gender) {
            "Male" -> android.R.color.holo_blue_light
            "Female" -> android.R.color.holo_red_light
            else -> R.color.splash_text_secondary
        }
        holder.genderChip.setChipBackgroundColorResource(genderColor)

        // Status chip
        holder.statusChip.text = if (student.isActive) "Active" else "Inactive"
        holder.statusChip.setChipBackgroundColorResource(
            if (student.isActive) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        )

        // Attendance
        holder.attendanceText.text = String.format("%.1f%%", student.totalAttendancePercentage)

        // CGPA
        holder.cgpaText.text = String.format("%.2f", student.cgpa)

        // Click listener
        holder.itemView.setOnClickListener { onViewClick(student) }
    }

    override fun getItemCount(): Int = studentsList.size

    fun updateList(newList: List<Student>) {
        studentsList = newList
        notifyDataSetChanged()
    }
}
