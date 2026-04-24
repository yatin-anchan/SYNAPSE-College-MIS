package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.projectbyyatin.synapsemis.FacultyMarksEntryListActivity
import com.projectbyyatin.synapsemis.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FacultyMarksEntryListAdapter(
    private val examsList: List<FacultyMarksEntryListActivity.ExamWithSubjects>,
    private val onItemClick: (FacultyMarksEntryListActivity.ExamWithSubjects, FacultyMarksEntryListActivity.SubjectWithStatus) -> Unit
) : RecyclerView.Adapter<FacultyMarksEntryListAdapter.ExamViewHolder>() {

    inner class ExamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val examNameText: TextView = itemView.findViewById(R.id.exam_name)
        val examDateText: TextView = itemView.findViewById(R.id.exam_date)
        val courseText: TextView = itemView.findViewById(R.id.course_text)
        val subjectsRecyclerView: RecyclerView = itemView.findViewById(R.id.subjects_recycler_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_faculty_marks_exam, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        val examWithSubjects = examsList[position]
        val exam = examWithSubjects.exam

        holder.examNameText.text = exam.examName
        holder.courseText.text = exam.courseName

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.examDateText.text = "Exam Date: ${dateFormat.format(Date(exam.startDate))}"

        // Setup nested RecyclerView for subjects
        val subjectsAdapter = SubjectsAdapter(examWithSubjects.relevantSubjects) { subject ->
            onItemClick(examWithSubjects, subject)
        }

        holder.subjectsRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.subjectsRecyclerView.adapter = subjectsAdapter
    }

    override fun getItemCount(): Int = examsList.size

    // Nested adapter for subjects
    private class SubjectsAdapter(
        private val subjects: List<FacultyMarksEntryListActivity.SubjectWithStatus>,
        private val onSubjectClick: (FacultyMarksEntryListActivity.SubjectWithStatus) -> Unit
    ) : RecyclerView.Adapter<SubjectsAdapter.SubjectViewHolder>() {

        inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.subject_card)
            val subjectNameText: TextView = itemView.findViewById(R.id.subject_name)
            val maxMarksText: TextView = itemView.findViewById(R.id.max_marks)
            val statusText: TextView = itemView.findViewById(R.id.status_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_marks_entry_subject, parent, false)
            return SubjectViewHolder(view)
        }

        override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
            val subjectWithStatus = subjects[position]
            val subject = subjectWithStatus.examSubject

            holder.subjectNameText.text = subject.subjectName
            holder.maxMarksText.text = "Max Marks: ${subject.maxMarks}"

            if (subjectWithStatus.isSubmitted) {
                holder.statusText.text = "✓ Submitted"
                holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
                holder.card.strokeColor = holder.itemView.context.getColor(android.R.color.holo_green_dark)
            } else {
                holder.statusText.text = "⏳ Pending"
                holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_orange_dark))
                holder.card.strokeColor = holder.itemView.context.getColor(android.R.color.holo_orange_dark)
            }

            holder.card.setOnClickListener { onSubjectClick(subjectWithStatus) }
        }

        override fun getItemCount(): Int = subjects.size
    }
}