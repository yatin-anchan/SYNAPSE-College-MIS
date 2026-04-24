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

class ExamHistoryAdapter(
    private var examsList: List<Exam>,
    private val onViewClick: (Exam) -> Unit
) : RecyclerView.Adapter<ExamHistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val examName: TextView = itemView.findViewById(R.id.exam_name)
        val semesterChip: Chip = itemView.findViewById(R.id.semester_chip)
        val examPeriod: TextView = itemView.findViewById(R.id.exam_period)
        val subjectsCount: TextView = itemView.findViewById(R.id.subjects_count)
        val academicYear: TextView = itemView.findViewById(R.id.academic_year)
        val btnView: ImageView = itemView.findViewById(R.id.btn_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exam_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val exam = examsList[position]

        holder.examName.text = exam.examName
        holder.semesterChip.text = "Sem ${exam.semester}"
        holder.examPeriod.text = "${dateFormat.format(Date(exam.startDate))} - ${dateFormat.format(Date(exam.endDate))}"
        holder.subjectsCount.text = "${exam.subjects.size} Subjects"
        holder.academicYear.text = exam.academicYear

        holder.itemView.setOnClickListener { onViewClick(exam) }
        holder.btnView.setOnClickListener { onViewClick(exam) }
    }

    override fun getItemCount(): Int = examsList.size

    fun updateList(newList: List<Exam>) {
        examsList = newList
        notifyDataSetChanged()
    }
}
