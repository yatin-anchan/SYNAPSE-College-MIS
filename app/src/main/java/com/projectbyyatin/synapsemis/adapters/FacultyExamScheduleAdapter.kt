package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import java.text.SimpleDateFormat
import java.util.*

class FacultyExamScheduleAdapter(
    private var exams: MutableList<Exam>,
    private val facultyId: String,                       // ✅ REQUIRED
    private val onExamClick: (Exam) -> Unit,
    private val onEnterMarks: (Exam, ExamSubject) -> Unit
) : RecyclerView.Adapter<FacultyExamScheduleAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.card_view)
        val examNameText: TextView = view.findViewById(R.id.exam_name_text)
        val examTypeText: TextView = view.findViewById(R.id.exam_type_text)
        val dateRangeText: TextView = view.findViewById(R.id.date_range_text)
        val statusChip: Chip = view.findViewById(R.id.status_chip)
        val subjectsContainer: ViewGroup = view.findViewById(R.id.subjects_container)
        val btnViewDetails: MaterialButton = view.findViewById(R.id.btn_view_details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_faculty_exam_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val exam = exams[position]
        val currentTime = System.currentTimeMillis()

        holder.examNameText.text = exam.examName
        holder.examTypeText.text = "Semester ${exam.semester} • ${exam.academicYear}"
        holder.dateRangeText.text =
            "${dateFormat.format(exam.startDate)} - ${dateFormat.format(exam.endDate)}"

        // Status chip
        when {
            currentTime < exam.startDate -> {
                holder.statusChip.text = "Upcoming"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_blue_light)
            }
            currentTime in exam.startDate..exam.endDate -> {
                holder.statusChip.text = "Ongoing"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_light)
            }
            else -> {
                holder.statusChip.text = "Completed"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.darker_gray)
            }
        }

        if (exam.marksPublished) {
            holder.statusChip.text = "${holder.statusChip.text} • Published"
        }

        // Subjects assigned to THIS faculty
        holder.subjectsContainer.removeAllViews()

        exam.subjects
            .filter { it.assignedFacultyId == facultyId }
            .forEach { subject ->

                val subjectView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_exam_subject_mini, holder.subjectsContainer, false)

                subjectView.findViewById<TextView>(R.id.subject_name).text = subject.subjectName
                subjectView.findViewById<TextView>(R.id.subject_date).text =
                    dateFormat.format(subject.examDate)
                subjectView.findViewById<TextView>(R.id.subject_time).text =
                    "${subject.startTime} - ${subject.endTime}"

                val btnMarks =
                    subjectView.findViewById<MaterialButton>(R.id.btn_enter_marks)

                if (currentTime >= exam.startDate &&
                    exam.marksEntryEnabled &&
                    !exam.marksPublished
                ) {
                    btnMarks.visibility = View.VISIBLE
                    btnMarks.setOnClickListener {
                        onEnterMarks(exam, subject)
                    }

                    val marksStatus = exam.subjectsMarksStatus[subject.subjectId]
                    if (marksStatus?.marksSubmitted == true) {
                        btnMarks.text = "View Marks"
                        btnMarks.setIconResource(R.drawable.ic_check)
                    }
                } else {
                    btnMarks.visibility = View.GONE
                }

                holder.subjectsContainer.addView(subjectView)
            }

        holder.btnViewDetails.setOnClickListener { onExamClick(exam) }
        holder.cardView.setOnClickListener { onExamClick(exam) }
    }

    override fun getItemCount(): Int = exams.size

    fun updateList(newExams: List<Exam>) {
        exams.clear()
        exams.addAll(newExams)
        notifyDataSetChanged()
    }
}
