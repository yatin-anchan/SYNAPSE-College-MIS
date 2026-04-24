package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ExamSubject
import java.text.SimpleDateFormat
import java.util.*

class ExamSubjectAdapter(
    private var subjectsList: List<ExamSubject>,
    private val onEditClick: (ExamSubject) -> Unit,
    private val onDeleteClick: (ExamSubject) -> Unit
) : RecyclerView.Adapter<ExamSubjectAdapter.SubjectViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
        val subjectName: TextView = itemView.findViewById(R.id.subject_name)
        val subjectCode: TextView = itemView.findViewById(R.id.subject_code)
        val courseName: TextView = itemView.findViewById(R.id.course_name)
        val examDate: TextView = itemView.findViewById(R.id.exam_date)
        val examTime: TextView = itemView.findViewById(R.id.exam_time)
        val duration: TextView = itemView.findViewById(R.id.duration)
        val maxMarks: TextView = itemView.findViewById(R.id.max_marks)
        val btnEdit: ImageView = itemView.findViewById(R.id.btn_edit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exam_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = subjectsList[position]

        holder.subjectName.text = subject.subjectName
        holder.subjectCode.text = subject.subjectCode
        holder.courseName.text = subject.courseName
        holder.examDate.text = dateFormat.format(Date(subject.examDate))
        holder.examTime.text = "${subject.startTime} - ${subject.endTime}"
        holder.duration.text = "${subject.duration} hrs"
        holder.maxMarks.text = "${subject.maxMarks} marks"

        holder.btnEdit.setOnClickListener { onEditClick(subject) }
        holder.btnDelete.setOnClickListener { onDeleteClick(subject) }
    }

    override fun getItemCount(): Int = subjectsList.size

    fun updateList(newList: List<ExamSubject>) {
        subjectsList = newList
        notifyDataSetChanged()
    }
}
