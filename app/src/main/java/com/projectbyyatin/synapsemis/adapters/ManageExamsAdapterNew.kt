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

class ManageExamsAdapterNew(
    private var examsList: List<Exam>,
    private val onViewClick: (Exam) -> Unit,
    private val onEnterMarksClick: (Exam) -> Unit,
    private val onEnableMarksEntryClick: (Exam) -> Unit,
    private val onPublishMarksClick: (Exam) -> Unit,
    private var currentUserRole: String
) : RecyclerView.Adapter<ManageExamsAdapterNew.ExamViewHolder>() {

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

        // New controls
        val marksStatusChip: Chip = itemView.findViewById(R.id.marks_status_chip)
        val btnEnableMarksEntry: ImageView = itemView.findViewById(R.id.btn_enable_marks_entry)
        val btnPublishMarks: ImageView = itemView.findViewById(R.id.btn_publish_marks)
        val marksProgressText: TextView = itemView.findViewById(R.id.marks_progress_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manage_exam_new, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        val exam = examsList[position]

        holder.examName.text = exam.examName
        holder.semesterChip.text = "Sem ${exam.semester}"
        holder.examPeriod.text = "${dateFormat.format(Date(exam.startDate))} - ${dateFormat.format(Date(exam.endDate))}"
        holder.subjectsCount.text = "${exam.subjects.size} Subjects"
        holder.academicYear.text = exam.academicYear

        // Exam status chip
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

        // Marks status and progress
        updateMarksStatus(holder, exam)

        // Role-based button visibility
        updateButtonVisibility(holder, exam)

        // Click listeners
        holder.itemView.setOnClickListener { onViewClick(exam) }
        holder.btnView.setOnClickListener { onViewClick(exam) }
        holder.btnEnterMarks.setOnClickListener { onEnterMarksClick(exam) }
        holder.btnEnableMarksEntry.setOnClickListener { onEnableMarksEntryClick(exam) }
        holder.btnPublishMarks.setOnClickListener { onPublishMarksClick(exam) }
    }

    override fun getItemCount(): Int = examsList.size

    private fun updateMarksStatus(holder: ExamViewHolder, exam: Exam) {
        val totalSubjects = exam.subjects.size
        val marksStatusMap = exam.subjectsMarksStatus

        val submitted = marksStatusMap.count { it.value.marksSubmitted }
        val moderated = marksStatusMap.count { it.value.moderated }
        val readyForPublish = marksStatusMap.count { it.value.readyForPublish }

        // Progress text
        holder.marksProgressText.text = when {
            exam.marksPublished -> "Published ✓"
            readyForPublish == totalSubjects && totalSubjects > 0 -> "All subjects ready"
            moderated > 0 -> "Moderated: $moderated/$totalSubjects"
            submitted > 0 -> "Submitted: $submitted/$totalSubjects"
            else -> "Not started"
        }

        // Marks status chip
        when {
            exam.marksPublished -> {
                holder.marksStatusChip.text = "Published"
                holder.marksStatusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
                holder.marksStatusChip.visibility = View.VISIBLE
            }
            readyForPublish == totalSubjects && totalSubjects > 0 -> {
                holder.marksStatusChip.text = "Ready to Publish"
                holder.marksStatusChip.setChipBackgroundColorResource(android.R.color.holo_blue_dark)
                holder.marksStatusChip.visibility = View.VISIBLE
            }
            exam.marksEntryEnabled -> {
                holder.marksStatusChip.text = "Entry Enabled"
                holder.marksStatusChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
                holder.marksStatusChip.visibility = View.VISIBLE
            }
            else -> {
                holder.marksStatusChip.visibility = View.GONE
            }
        }
    }

    private fun updateButtonVisibility(holder: ExamViewHolder, exam: Exam) {
        val examStarted = System.currentTimeMillis() >= exam.startDate
        val totalSubjects = exam.subjects.size
        val readyCount = exam.subjectsMarksStatus.count { it.value.readyForPublish }

        when (currentUserRole) {
            "coe" -> {
                // COE sees all controls
                holder.btnEnableMarksEntry.visibility = if (!exam.marksEntryEnabled && examStarted) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                holder.btnPublishMarks.visibility = if (readyCount == totalSubjects && totalSubjects > 0 && !exam.marksPublished) {
                    View.VISIBLE
                } else if (exam.marksPublished) {
                    View.VISIBLE // Allow re-publish
                } else {
                    View.GONE
                }

                holder.btnEnterMarks.visibility = if (exam.marksEntryEnabled) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            "hod" -> {
                // HOD only sees enter marks (for moderation)
                holder.btnEnableMarksEntry.visibility = View.GONE
                holder.btnPublishMarks.visibility = View.GONE
                holder.btnEnterMarks.visibility = if (exam.marksEntryEnabled) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            "faculty" -> {
                // Faculty only sees enter marks
                holder.btnEnableMarksEntry.visibility = View.GONE
                holder.btnPublishMarks.visibility = View.GONE
                holder.btnEnterMarks.visibility = if (exam.marksEntryEnabled) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            else -> {
                // Hide all action buttons for other roles
                holder.btnEnableMarksEntry.visibility = View.GONE
                holder.btnPublishMarks.visibility = View.GONE
                holder.btnEnterMarks.visibility = View.GONE
            }
        }
    }

    fun updateList(newList: List<Exam>) {
        examsList = newList
        notifyDataSetChanged()
    }

    fun updateRole(role: String) {
        currentUserRole = role
        notifyDataSetChanged()
    }
}