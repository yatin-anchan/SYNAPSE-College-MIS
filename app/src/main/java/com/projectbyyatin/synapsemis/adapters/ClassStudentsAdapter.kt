package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ClassStudent
import de.hdodenhof.circleimageview.CircleImageView

class ClassStudentsAdapter(
    private var studentsList: List<ClassStudent>,
    private val onViewClick: (ClassStudent) -> Unit,
    private val onRemoveClick: (ClassStudent) -> Unit
) : RecyclerView.Adapter<ClassStudentsAdapter.StudentViewHolder>() {

    inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.profile_image)
        val studentName: TextView = itemView.findViewById(R.id.student_name)
        val studentEmail: TextView = itemView.findViewById(R.id.student_email)
        val studentRollNo: TextView = itemView.findViewById(R.id.student_roll_no)
        val btnView: ImageView = itemView.findViewById(R.id.btn_view)
        val btnRemove: ImageView = itemView.findViewById(R.id.btn_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_class_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = studentsList[position]

        holder.studentName.text = student.studentName
        holder.studentEmail.text = student.studentEmail
        holder.studentRollNo.text = "Roll No: ${student.rollNumber}"

        holder.itemView.setOnClickListener { onViewClick(student) }
        holder.btnView.setOnClickListener { onViewClick(student) }
        holder.btnRemove.setOnClickListener { onRemoveClick(student) }
    }

    override fun getItemCount(): Int = studentsList.size

    fun updateList(newList: List<ClassStudent>) {
        studentsList = newList
        notifyDataSetChanged()
    }
}
