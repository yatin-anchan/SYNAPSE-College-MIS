package com.projectbyyatin.synapsemis.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus

class SubjectMarksTrackerAdapter(
    private val subjects: List<SubjectMarksStatus>
) : RecyclerView.Adapter<SubjectMarksTrackerAdapter.SubjectViewHolder>() {

    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subjectCard: MaterialCardView            = itemView.findViewById(R.id.subject_card)
        val subjectCode: TextView                    = itemView.findViewById(R.id.subject_code)
        val progressBar: CircularProgressIndicator   = itemView.findViewById(R.id.progress_bar)
        val marksText: TextView                      = itemView.findViewById(R.id.marks_text)
        val statusChip: Chip                         = itemView.findViewById(R.id.status_chip)
        // Mini stepper dots
        val miniDot1: View     = itemView.findViewById(R.id.mini_dot_1)
        val miniDot2: View     = itemView.findViewById(R.id.mini_dot_2)
        val miniDot3: View     = itemView.findViewById(R.id.mini_dot_3)
        val miniDot1Fill: View = itemView.findViewById(R.id.mini_dot_1_fill)
        val miniDot2Fill: View = itemView.findViewById(R.id.mini_dot_2_fill)
        val miniDot3Fill: View = itemView.findViewById(R.id.mini_dot_3_fill)
        val miniLine1: View    = itemView.findViewById(R.id.mini_line_1)
        val miniLine2: View    = itemView.findViewById(R.id.mini_line_2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject_marks_tracker, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = subjects[position]
        val ctx     = holder.itemView.context

        // Subject display
        holder.subjectCode.text = subject.subjectId.takeLast(8)

        // Circular progress
        val progress = if (subject.totalStudents > 0)
            (subject.marksEntered * 100 / subject.totalStudents).coerceIn(0, 100) else 0
        holder.progressBar.progress = progress
        holder.marksText.text       = "${subject.marksEntered}/${subject.totalStudents}"

        // Step flags
        val step1 = subject.marksEntered > 0      // Entered
        val step2 = subject.marksSubmitted         // Submitted
        val step3 = subject.moderated              // Moderated

        // Mini stepper dots
        activateMiniDot(holder.miniDot1, holder.miniDot1Fill, holder.miniLine1, step1, ctx)
        activateMiniDot(holder.miniDot2, holder.miniDot2Fill, holder.miniLine2, step2, ctx)
        activateMiniDot(holder.miniDot3, holder.miniDot3Fill, null,             step3, ctx)

        // Status chip
        val (colorRes, chipLabel) = when {
            step3 -> Pair(R.color.green_500,  "✅ Moderated")
            step2 -> Pair(R.color.neural_blue,   "✅ Submitted")
            step1 -> Pair(R.color.orange_500, "⏳ Partial")
            else  -> Pair(R.color.grey_dark,   "Pending")
        }

        holder.statusChip.text = chipLabel
        holder.statusChip.chipBackgroundColor =
            ContextCompat.getColorStateList(ctx, colorRes)
        holder.statusChip.setTextColor(Color.WHITE)

        // Card stroke reflects status
        holder.subjectCard.strokeColor  = ContextCompat.getColor(ctx, colorRes)
        holder.subjectCard.strokeWidth  = 3
    }

    private fun activateMiniDot(
        dot: View, fill: View, line: View?,
        active: Boolean, ctx: android.content.Context
    ) {
        if (active) {
            dot.background  = ContextCompat.getDrawable(ctx, R.drawable.stepper_dot_active)
            fill.visibility = View.GONE
            line?.background = ContextCompat.getDrawable(ctx, R.drawable.stepper_line_active)
        } else {
            dot.background  = ContextCompat.getDrawable(ctx, R.drawable.stepper_dot_inactive)
            fill.visibility = View.VISIBLE
            line?.background = ContextCompat.getDrawable(ctx, R.drawable.stepper_line_inactive)
        }
    }

    override fun getItemCount() = subjects.size
}