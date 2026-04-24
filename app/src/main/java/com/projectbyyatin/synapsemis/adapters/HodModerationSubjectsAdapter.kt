package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.projectbyyatin.synapsemis.HodMarksModerationListActivity.SubjectWithModerationStatus
import com.projectbyyatin.synapsemis.R

/**
 * Flat subject list adapter — used when you want a standalone RecyclerView
 * of subjects (e.g. inside HodMarksModerationActivity or a detail sheet)
 * rather than the nested exam→subjects layout in HodMarksModerationListAdapter.
 *
 * Layout: item_hod_moderation_subject.xml
 * Data:   SubjectWithModerationStatus  (same model used by the list screen)
 *
 * Three visual states:
 *   📝 Not Submitted      — grey stroke, 0.55 alpha, tap → activity handles toast
 *   ⏳ Pending Moderation  — amber stroke, full opacity, tap → opens moderator
 *   🔒 Moderated & Locked — green stroke, 0.72 alpha, card non-clickable
 */
class HodModerationSubjectsAdapter(
    private val onSubjectClick: (SubjectWithModerationStatus) -> Unit
) : RecyclerView.Adapter<HodModerationSubjectsAdapter.SubjectViewHolder>() {

    private var subjectsList = listOf<SubjectWithModerationStatus>()

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.subject_card)
        val subjectName: TextView  = itemView.findViewById(R.id.subject_name)
        val maxMarks: TextView     = itemView.findViewById(R.id.max_marks)
        val statusText: TextView   = itemView.findViewById(R.id.status_text)
    }

    // ─── Adapter overrides ────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hod_moderation_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val item    = subjectsList[position]
        val subject = item.examSubject
        val ctx     = holder.itemView.context

        // ── Static fields ─────────────────────────────────────────────────────
        holder.subjectName.text = subject.subjectName
        holder.maxMarks.text    = "Max Marks: ${subject.maxMarks}"

        // ── State-driven appearance ───────────────────────────────────────────
        when {

            // State 3 — HOD moderated & locked
            item.isModerated -> {
                holder.statusText.text = "🔒 Moderated"
                holder.statusText.setTextColor(ctx.getColor(android.R.color.holo_green_dark))
                holder.card.strokeColor = ctx.getColor(android.R.color.holo_green_dark)
                holder.card.alpha       = 0.72f
                holder.card.isClickable = false
                holder.card.isFocusable = false
            }

            // State 2 — Faculty submitted, HOD action needed
            item.isSubmitted -> {
                holder.statusText.text = "⏳ Pending Moderation"
                holder.statusText.setTextColor(ctx.getColor(android.R.color.holo_orange_dark))
                holder.card.strokeColor = ctx.getColor(android.R.color.holo_orange_dark)
                holder.card.alpha       = 1f
                holder.card.isClickable = true
                holder.card.isFocusable = true
                holder.card.setOnClickListener { onSubjectClick(item) }
            }

            // State 1 — Faculty hasn't submitted yet
            else -> {
                holder.statusText.text = "📝 Not Submitted"
                holder.statusText.setTextColor(
                    ContextCompat.getColor(ctx, android.R.color.darker_gray)
                )
                holder.card.strokeColor =
                    ContextCompat.getColor(ctx, android.R.color.darker_gray)
                holder.card.alpha       = 0.55f
                // Still tappable so the activity can show a "not ready yet" toast
                holder.card.isClickable = true
                holder.card.isFocusable = true
                holder.card.setOnClickListener { onSubjectClick(item) }
            }
        }
    }

    override fun getItemCount(): Int = subjectsList.size

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Replace the full list and refresh.
     * Call this from your Activity/Fragment after loading moderation status.
     */
    fun updateList(newList: List<SubjectWithModerationStatus>) {
        subjectsList = newList
        notifyDataSetChanged()
    }

    /**
     * Refresh a single subject's status in-place (e.g. after returning from
     * HodMarksModerationActivity) without rebuilding the whole list.
     */
    fun updateSubjectStatus(subjectId: String, isSubmitted: Boolean, isModerated: Boolean) {
        val index = subjectsList.indexOfFirst { it.examSubject.subjectId == subjectId }
        if (index == -1) return
        subjectsList[index].isSubmitted = isSubmitted
        subjectsList[index].isModerated = isModerated
        notifyItemChanged(index)
    }
}