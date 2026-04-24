package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Exam

class ExamMarksTrackerAdapter(
    private var exams: List<Exam>
) : RecyclerView.Adapter<ExamMarksTrackerAdapter.ExamViewHolder>() {

    inner class ExamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val examCard: MaterialCardView            = itemView.findViewById(R.id.exam_card)
        val examName: TextView                    = itemView.findViewById(R.id.exam_name)
        val courseSemester: TextView              = itemView.findViewById(R.id.course_semester)
        val progressPercent: TextView             = itemView.findViewById(R.id.progress_percent)
        val progressBar: LinearProgressIndicator  = itemView.findViewById(R.id.progress_bar)
        val completedSubjects: TextView           = itemView.findViewById(R.id.completed_subjects)
        // ✅ TextView instead of MaterialButton (avoids Material3 theme attr crash)
        val expandButton: TextView                = itemView.findViewById(R.id.expand_button)
        val subjectsRecycler: RecyclerView        = itemView.findViewById(R.id.subjects_recycler)
        // Stepper dots (outer FrameLayout backgrounds are static; we toggle fill + check)
        val dot1Fill: View  = itemView.findViewById(R.id.dot_1_fill)
        val dot2Fill: View  = itemView.findViewById(R.id.dot_2_fill)
        val dot3Fill: View  = itemView.findViewById(R.id.dot_3_fill)
        val dot4Fill: View  = itemView.findViewById(R.id.dot_4_fill)
        val dot1Check: View = itemView.findViewById(R.id.dot_1_check)
        val dot2Check: View = itemView.findViewById(R.id.dot_2_check)
        val dot3Check: View = itemView.findViewById(R.id.dot_3_check)
        val dot4Check: View = itemView.findViewById(R.id.dot_4_check)
        // Connector lines
        val line12: View = itemView.findViewById(R.id.line_1_2)
        val line23: View = itemView.findViewById(R.id.line_2_3)
        val line34: View = itemView.findViewById(R.id.line_3_4)
        // Step labels + sub-texts
        val step1Label: TextView = itemView.findViewById(R.id.step_1_label)
        val step2Label: TextView = itemView.findViewById(R.id.step_2_label)
        val step3Label: TextView = itemView.findViewById(R.id.step_3_label)
        val step4Label: TextView = itemView.findViewById(R.id.step_4_label)
        val step1Sub: TextView   = itemView.findViewById(R.id.step_1_sub)
        val step2Sub: TextView   = itemView.findViewById(R.id.step_2_sub)
        val step3Sub: TextView   = itemView.findViewById(R.id.step_3_sub)
        val step4Sub: TextView   = itemView.findViewById(R.id.step_4_sub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exam_marks_tracker, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        val exam = exams[position]
        val ctx  = holder.itemView.context

        holder.examName.text       = exam.examName
        holder.courseSemester.text = "${exam.courseName} • Sem ${exam.semester}"

        val subjects      = exam.subjectsMarksStatus.values.toList()
        val enteredCount  = subjects.count { it.marksEntered > 0 }
        val totalSubjects = subjects.size
        val progress      = if (totalSubjects > 0) (enteredCount * 100 / totalSubjects) else 0

        holder.progressPercent.text   = "$progress%"
        holder.progressBar.progress   = progress
        holder.completedSubjects.text = "$enteredCount/$totalSubjects"

        // Stepper flags
        val step1 = subjects.any { it.marksEntered > 0 }
        val step2 = subjects.any { it.marksSubmitted }
        val step3 = subjects.any { it.moderated }
        val step4 = subjects.isNotEmpty() && subjects.all { it.readyForPublish }

        activateStep(holder.dot1Fill, holder.dot1Check, holder.line12, holder.step1Label, step1, ctx)
        activateStep(holder.dot2Fill, holder.dot2Check, holder.line23, holder.step2Label, step2, ctx)
        activateStep(holder.dot3Fill, holder.dot3Check, holder.line34, holder.step3Label, step3, ctx)
        activateStep(holder.dot4Fill, holder.dot4Check, null,          holder.step4Label, step4, ctx)

        holder.step1Sub.text = if (step1) "$enteredCount/$totalSubjects students" else "0/$totalSubjects"
        holder.step2Sub.text = if (step2) "Done ✓" else "Pending"
        holder.step3Sub.text = if (step3) "Done ✓" else "Pending"
        holder.step4Sub.text = if (step4) "Done ✓" else "Pending"

        // Nested subject list
        holder.subjectsRecycler.layoutManager =
            LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        holder.subjectsRecycler.adapter = SubjectMarksTrackerAdapter(subjects)

        // Expand / collapse
        holder.expandButton.setOnClickListener {
            val expanded = holder.subjectsRecycler.visibility == View.VISIBLE
            holder.subjectsRecycler.visibility = if (expanded) View.GONE else View.VISIBLE
            holder.expandButton.text           = if (expanded) "▼  View Subjects" else "▲  Hide Subjects"
        }
    }

    private fun activateStep(
        fill: View, check: View, line: View?,
        label: TextView, active: Boolean,
        ctx: android.content.Context
    ) {
        fill.visibility  = if (active) View.GONE else View.VISIBLE
        check.visibility = if (active) View.VISIBLE else View.GONE
        line?.background = ContextCompat.getDrawable(
            ctx, if (active) R.drawable.stepper_line_active else R.drawable.stepper_line_inactive
        )
        label.setTextColor(
            if (active) ContextCompat.getColor(ctx, R.color.splash_accent) else 0x88FFFFFF.toInt()
        )
    }

    override fun getItemCount() = exams.size

    fun updateList(newExams: List<Exam>) {
        exams = newExams
        notifyDataSetChanged()
    }
}