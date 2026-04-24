package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ExamMarks

class ExamResultsAdapter(
    private var resultsList: List<ExamMarks>
) : RecyclerView.Adapter<ExamResultsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val studentName: TextView = itemView.findViewById(R.id.studentname)
        val rollNo: TextView = itemView.findViewById(R.id.rollno)
        val writtenMarksText: TextView = itemView.findViewById(R.id.written_marks_text)
        val internalMarksText: TextView = itemView.findViewById(R.id.internal_marks_text)
        val totalMarksText: TextView = itemView.findViewById(R.id.total_marks_text)
        val percentageText: TextView = itemView.findViewById(R.id.percentagetext)
        val gradeText: TextView = itemView.findViewById(R.id.gradetext)
        val statusText: TextView = itemView.findViewById(R.id.status_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exam_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = resultsList[position]

        holder.studentName.text = result.studentName
        holder.rollNo.text = result.studentRollNo

        if (result.isAbsent) {
            holder.writtenMarksText.text = "AB"
            holder.internalMarksText.text = "AB"
            holder.totalMarksText.text = "AB"
            holder.percentageText.text = "AB"
            holder.gradeText.text = "AB"
            holder.statusText.text = "ABSENT"
            holder.statusText.setBackgroundResource(android.R.drawable.btn_default)
        } else {
            // Written component
            holder.writtenMarksText.text = if (result.hasWrittenComponent)
                "${result.writtenMarksObtained.toInt()} / ${result.writtenMaxMarks}"
            else "--"

            // Internal component
            holder.internalMarksText.text = if (result.hasInternalComponent)
                "${result.internalMarksObtained.toInt()} / ${result.internalMaxMarks}"
            else "--"

            // Total
            holder.totalMarksText.text =
                "${result.totalMarksObtained.toInt()} / ${result.totalMaxMarks}"

            // Percentage
            holder.percentageText.text = String.format("%.1f%%", result.percentage)

            // Grade
            holder.gradeText.text = result.grade

            // PASS / FAIL badge
            val passed = result.percentage >= 40f
            holder.statusText.text = if (passed) "PASS" else "FAIL"
        }

        // Grade badge background color
        val gradeColor = when (result.grade) {
            "O", "A+", "A" -> android.R.color.holo_green_dark
            "B+", "B"      -> android.R.color.holo_blue_dark
            "C", "D"       -> android.R.color.holo_orange_dark
            else           -> android.R.color.holo_red_dark
        }
        holder.gradeText.setBackgroundColor(
            holder.itemView.context.getColor(gradeColor)
        )
    }

    override fun getItemCount(): Int = resultsList.size

    fun updateList(newList: List<ExamMarks>) {
        resultsList = newList
        notifyDataSetChanged()
    }
}