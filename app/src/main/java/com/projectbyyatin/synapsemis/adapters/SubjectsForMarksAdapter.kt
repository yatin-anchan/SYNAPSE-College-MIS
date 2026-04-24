package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ExamSubject

class SubjectsForMarksAdapter(
    private val onSubjectClick: (ExamSubject) -> Unit
) : RecyclerView.Adapter<SubjectsForMarksAdapter.ViewHolder>() {

    private var subjectsList: List<ExamSubject> = emptyList()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subjectName: TextView = itemView.findViewById(R.id.subject_name)
        val courseName: TextView = itemView.findViewById(R.id.course_name)
        val maxMarks: TextView = itemView.findViewById(R.id.max_marks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject_for_marks, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subject = subjectsList[position]

        holder.subjectName.text = subject.subjectName
        holder.courseName.text = subject.courseName
        holder.maxMarks.text = "Max: ${subject.maxMarks}"

        holder.itemView.setOnClickListener { onSubjectClick(subject) }
    }

    override fun getItemCount(): Int = subjectsList.size

    fun updateList(newList: List<ExamSubject>) {
        subjectsList = newList
        notifyDataSetChanged()
    }
}
