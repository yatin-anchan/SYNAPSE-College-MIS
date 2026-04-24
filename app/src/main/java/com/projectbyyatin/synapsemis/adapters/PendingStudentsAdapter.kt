package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ClassStudent
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PendingStudentsAdapter(
    private var studentsList: List<ClassStudent>,
    private val onApproveClick: (ClassStudent) -> Unit,
    private val onRejectClick: (ClassStudent) -> Unit
) : RecyclerView.Adapter<PendingStudentsAdapter.StudentViewHolder>() {

    inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.profile_image)
        val studentName: TextView = itemView.findViewById(R.id.student_name)
        val studentEmail: TextView = itemView.findViewById(R.id.student_email)
        val studentRollNo: TextView = itemView.findViewById(R.id.student_roll_no)
        val requestedAt: TextView = itemView.findViewById(R.id.requested_at)
        val btnApprove: MaterialButton = itemView.findViewById(R.id.btn_approve)
        val btnReject: MaterialButton = itemView.findViewById(R.id.btn_reject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = studentsList[position]

        holder.studentName.text = student.studentName
        holder.studentEmail.text = student.studentEmail
        holder.studentRollNo.text = "Roll No: ${student.rollNumber}"

        // Format timestamp
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        holder.requestedAt.text = "Requested: ${dateFormat.format(Date(student.joinedAt))}"

        holder.btnApprove.setOnClickListener { onApproveClick(student) }
        holder.btnReject.setOnClickListener { onRejectClick(student) }
    }

    override fun getItemCount(): Int = studentsList.size

    fun updateList(newList: List<ClassStudent>) {
        studentsList = newList
        notifyDataSetChanged()
    }
}
