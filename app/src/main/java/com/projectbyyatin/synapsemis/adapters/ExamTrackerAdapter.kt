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

class ExamTrackerAdapter(
    private val examsList: MutableList<Exam>,
    private val onExamClick: (Exam) -> Unit
) : RecyclerView.Adapter<ExamTrackerAdapter.ExamViewHolder>() {

    inner class ExamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val examName: TextView = itemView.findViewById(R.id.exam_name)
        val semesterChip: Chip = itemView.findViewById(R.id.semester_chip)
        val statusChip: Chip = itemView.findViewById(R.id.status_chip)
        val examPeriod: TextView = itemView.findViewById(R.id.exam_period)
        val progressText: TextView = itemView.findViewById(R.id.progress_text)
        val arrowIcon: ImageView = itemView.findViewById(R.id.arrow_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exam_tracker, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        val exam = examsList[position]
        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

        // Bind data
        holder.examName.text = exam.examName
        holder.semesterChip.text = "Sem ${exam.semester}"

        // Status chip
        holder.statusChip.text = exam.status.replaceFirstChar { it.uppercase() }
        when (exam.status.lowercase()) {
            "confirmed" -> holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
            "ongoing" -> holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
            "completed" -> holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_blue_dark)
            else -> holder.statusChip.setChipBackgroundColorResource(android.R.color.darker_gray)
        }

        holder.examPeriod.text = "${dateFormat.format(Date(exam.startDate))} - ${dateFormat.format(Date(exam.endDate))}"

        // Progress text
        val totalSubjects = exam.subjectsMarksStatus.size
        val readyCount = exam.subjectsMarksStatus.count { it.value.readyForPublish }
        holder.progressText.text = when {
            exam.marksPublished -> "✅ Published"
            readyCount == totalSubjects && totalSubjects > 0 -> "${readyCount}/${totalSubjects} Ready"
            else -> "📝 Track Progress"
        }

        // Click listener
        holder.itemView.setOnClickListener { onExamClick(exam) }
        holder.arrowIcon.setOnClickListener { onExamClick(exam) }
    }

    override fun getItemCount(): Int = examsList.size

    fun updateList(newList: List<Exam>) {
        examsList.clear()
        examsList.addAll(newList)
        notifyDataSetChanged()
    }
}
