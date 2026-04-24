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

class EnhancedExamSubjectAdapter(
    private val subjects: MutableList<ExamSubject>,
    private val onRescheduleClick: (ExamSubject) -> Unit,
    private val onDeleteClick: (ExamSubject) -> Unit,
    private val onAssignInvigilatorClick: (ExamSubject) -> Unit
) : RecyclerView.Adapter<EnhancedExamSubjectAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subjectName: TextView = view.findViewById(R.id.subject_name)
        val courseName: TextView = view.findViewById(R.id.course_name)
        val examDate: TextView = view.findViewById(R.id.exam_date)
        val examTime: TextView = view.findViewById(R.id.exam_time)
        val venue: TextView = view.findViewById(R.id.venue)
        val maxMarks: TextView = view.findViewById(R.id.max_marks)

        val btnEdit: ImageView = view.findViewById(R.id.btn_edit)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_enhanced_exam_subject, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subject = subjects[position]
        val date = Date(subject.examDate)

        holder.subjectName.text = subject.subjectName
        holder.courseName.text = subject.courseName
        holder.examDate.text = dateFormat.format(date)
        holder.examTime.text = "${subject.startTime} - ${subject.endTime}"
        holder.venue.text = subject.venue ?: "Not Assigned"
        holder.maxMarks.text = "Max Marks: ${subject.maxMarks}"

        holder.btnEdit.setOnClickListener {
            onRescheduleClick(subject)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(subject)
        }
    }

    override fun getItemCount() = subjects.size

    fun updateSubject(updatedSubject: ExamSubject) {
        val index = subjects.indexOfFirst { it.subjectId == updatedSubject.subjectId }
        if (index != -1) {
            subjects[index] = updatedSubject
            notifyItemChanged(index)
        }
    }

    fun removeSubject(subject: ExamSubject) {
        val index = subjects.indexOfFirst { it.subjectId == subject.subjectId }
        if (index != -1) {
            subjects.removeAt(index)
            notifyItemRemoved(index)
            notifyItemRangeChanged(index, subjects.size - index)
        }
    }
}
