package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ExamSubject
import java.text.SimpleDateFormat
import java.util.*

class ExamSubjectDetailsAdapter(
    private val subjects: List<ExamSubject>,
    private val onAssignInvigilatorClick: (ExamSubject) -> Unit
) : RecyclerView.Adapter<ExamSubjectDetailsAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subjectName: TextView = view.findViewById(R.id.subject_name)
        val subjectCode: TextView = view.findViewById(R.id.subject_code)
        val courseName: TextView = view.findViewById(R.id.course_name)
        val examDate: TextView = view.findViewById(R.id.exam_date)
        val examDay: TextView = view.findViewById(R.id.exam_day)
        val examTime: TextView = view.findViewById(R.id.exam_time)
        val duration: TextView = view.findViewById(R.id.duration)
        val maxMarks: TextView = view.findViewById(R.id.max_marks)
        val btnAssignInvigilator: ImageButton = view.findViewById(R.id.btn_assign_invigilator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exam_subject_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subject = subjects[position]
        val date = Date(subject.examDate)

        holder.subjectName.text = subject.subjectName
        holder.subjectCode.text = subject.subjectCode
        holder.courseName.text = subject.courseName
        holder.examDate.text = dateFormat.format(date)
        holder.examDay.text = dayFormat.format(date)
        holder.examTime.text = "${subject.startTime} - ${subject.endTime}"
        holder.duration.text = "${subject.duration} hrs"
        holder.maxMarks.text = "${subject.maxMarks} marks"

        holder.btnAssignInvigilator.setOnClickListener {
            onAssignInvigilatorClick(subject)
        }
    }

    override fun getItemCount() = subjects.size
}