package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Exam
import java.text.SimpleDateFormat
import java.util.*

class ManageExamsAdapter(
    private var examsList: List<Exam>,
    private val onViewClick: (Exam) -> Unit,
    private val onEnterMarksClick: (Exam) -> Unit
) : RecyclerView.Adapter<ManageExamsAdapter.ExamViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    inner class ExamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val examName: TextView = itemView.findViewById(R.id.exam_name)
        val semesterChip: Chip = itemView.findViewById(R.id.semester_chip)
        val statusChip: Chip = itemView.findViewById(R.id.status_chip)
        val examPeriod: TextView = itemView.findViewById(R.id.exam_period)
        val subjectsCount: TextView = itemView.findViewById(R.id.subjects_count)
        val academicYear: TextView = itemView.findViewById(R.id.academic_year)
        val btnView: ImageView = itemView.findViewById(R.id.btn_view)
        val btnEnterMarks: ImageView = itemView.findViewById(R.id.btn_enter_marks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manage_exam, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        val exam = examsList[position]

        holder.examName.text = exam.examName
        holder.semesterChip.text = "Sem ${exam.semester}"
        holder.examPeriod.text = "${dateFormat.format(Date(exam.startDate))} - ${dateFormat.format(Date(exam.endDate))}"
        holder.subjectsCount.text = "${exam.subjects.size} Subjects"
        holder.academicYear.text = exam.academicYear

        // Status chip
        when (exam.status) {
            "confirmed" -> {
                holder.statusChip.text = "Confirmed"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
            }
            "ongoing" -> {
                holder.statusChip.text = "Ongoing"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
            }
            "completed" -> {
                holder.statusChip.text = "Completed"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_blue_dark)
            }
            else -> {
                holder.statusChip.text = "Draft"
                holder.statusChip.setChipBackgroundColorResource(android.R.color.darker_gray)
            }
        }

        holder.itemView.setOnClickListener { onViewClick(exam) }
        holder.btnView.setOnClickListener { onViewClick(exam) }
        holder.btnEnterMarks.setOnClickListener { onEnterMarksClick(exam) }
    }

    override fun getItemCount(): Int = examsList.size

    fun updateList(newList: List<Exam>) {
        examsList = newList
        notifyDataSetChanged()
    }
}
