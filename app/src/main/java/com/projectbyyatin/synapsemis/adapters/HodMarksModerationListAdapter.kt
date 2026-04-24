package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.projectbyyatin.synapsemis.HodMarksModerationListActivity
import com.projectbyyatin.synapsemis.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HodMarksModerationListAdapter(
    private val examsList: List<HodMarksModerationListActivity.ExamWithSubjects>,
    private val onSubjectClick: (
        HodMarksModerationListActivity.ExamWithSubjects,
        HodMarksModerationListActivity.SubjectWithModerationStatus
    ) -> Unit
) : RecyclerView.Adapter<HodMarksModerationListAdapter.ExamViewHolder>() {

    // ─── Outer ViewHolder (one card per Exam) ─────────────────────────────────

    inner class ExamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val examNameText: TextView     = itemView.findViewById(R.id.exam_name)
        val examDateText: TextView     = itemView.findViewById(R.id.exam_date)
        val courseText: TextView       = itemView.findViewById(R.id.course_text)
        val subjectsRecyclerView: RecyclerView = itemView.findViewById(R.id.subjects_recycler_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hod_marks_moderation_exam, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        val examWithSubjects = examsList[position]
        val exam = examWithSubjects.exam

        holder.examNameText.text = exam.examName
        holder.courseText.text   = exam.courseName

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.examDateText.text = "Exam Date: ${dateFormat.format(Date(exam.startDate))}"

        // Nested adapter — one row per subject
        val subjectsAdapter = SubjectsAdapter(
            subjects = examWithSubjects.relevantSubjects
        ) { subject ->
            onSubjectClick(examWithSubjects, subject)
        }

        holder.subjectsRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.subjectsRecyclerView.adapter       = subjectsAdapter
        // Prevent nested scroll fighting with the outer NestedScrollView
        holder.subjectsRecyclerView.isNestedScrollingEnabled = false
    }

    override fun getItemCount(): Int = examsList.size

    // ─── Inner Adapter (one row per Subject) ─────────────────────────────────

    private class SubjectsAdapter(
        private val subjects: List<HodMarksModerationListActivity.SubjectWithModerationStatus>,
        private val onSubjectClick: (HodMarksModerationListActivity.SubjectWithModerationStatus) -> Unit
    ) : RecyclerView.Adapter<SubjectsAdapter.SubjectViewHolder>() {

        inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView   = itemView.findViewById(R.id.subject_card)
            val subjectNameText: TextView = itemView.findViewById(R.id.subject_name)
            val maxMarksText: TextView    = itemView.findViewById(R.id.max_marks)
            val statusText: TextView      = itemView.findViewById(R.id.status_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hod_moderation_subject, parent, false)
            return SubjectViewHolder(view)
        }

        override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
            val subjectWithStatus = subjects[position]
            val subject           = subjectWithStatus.examSubject
            val ctx               = holder.itemView.context

            holder.subjectNameText.text = subject.subjectName
            holder.maxMarksText.text    = "Max Marks: ${subject.maxMarks}"

            when {
                // ── State 3: HOD has moderated and locked ─────────────────────
                subjectWithStatus.isModerated -> {
                    holder.statusText.text = "🔒 Moderated"
                    holder.statusText.setTextColor(ctx.getColor(android.R.color.holo_green_dark))
                    holder.card.strokeColor = ctx.getColor(android.R.color.holo_green_dark)
                    // Visually dim the card — it's locked, tapping does nothing useful
                    holder.card.alpha = 0.72f
                    holder.card.isClickable = false
                    holder.card.isFocusable = false
                }

                // ── State 2: Faculty submitted, HOD action needed ─────────────
                subjectWithStatus.isSubmitted -> {
                    holder.statusText.text = "⏳ Pending Moderation"
                    holder.statusText.setTextColor(ctx.getColor(android.R.color.holo_orange_dark))
                    holder.card.strokeColor = ctx.getColor(android.R.color.holo_orange_dark)
                    holder.card.alpha = 1f
                    holder.card.isClickable = true
                    holder.card.isFocusable = true
                    holder.card.setOnClickListener { onSubjectClick(subjectWithStatus) }
                }

                // ── State 1: Faculty hasn't submitted yet ─────────────────────
                else -> {
                    holder.statusText.text = "📝 Not Submitted"
                    holder.statusText.setTextColor(
                        ContextCompat.getColor(ctx, android.R.color.darker_gray)
                    )
                    holder.card.strokeColor =
                        ContextCompat.getColor(ctx, android.R.color.darker_gray)
                    holder.card.alpha = 0.55f
                    // Tapping shows a toast (handled in Activity via the locked-guard path)
                    holder.card.isClickable = true
                    holder.card.isFocusable = true
                    holder.card.setOnClickListener { onSubjectClick(subjectWithStatus) }
                }
            }
        }

        override fun getItemCount(): Int = subjects.size
    }
}