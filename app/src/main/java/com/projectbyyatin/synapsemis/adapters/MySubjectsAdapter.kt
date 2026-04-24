package com.projectbyyatin.synapsemis.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Subject

class MySubjectsAdapter(
    private val subjectsList: MutableList<Subject>,  // Changed from 'var' to 'val'
    private val onSubjectClick: (Subject) -> Unit
) : RecyclerView.Adapter<MySubjectsAdapter.SubjectViewHolder>() {

    companion object {
        private const val TAG = "MySubjectsAdapter"
    }

    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subjectIcon: ImageView = itemView.findViewById(R.id.subject_icon)
        val subjectName: TextView = itemView.findViewById(R.id.subject_name)
        val subjectCode: TextView = itemView.findViewById(R.id.subject_code)
        val semesterText: TextView = itemView.findViewById(R.id.semester_text)
        val courseText: TextView = itemView.findViewById(R.id.course_text)
        val creditsChip: Chip = itemView.findViewById(R.id.credits_chip)
        val typeChip: Chip = itemView.findViewById(R.id.type_chip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        if (position >= subjectsList.size) {
            Log.e(TAG, "Invalid position: $position, list size: ${subjectsList.size}")
            return
        }

        val subject = subjectsList[position]

        holder.subjectName.text = subject.name
        holder.subjectCode.text = subject.code
        holder.semesterText.text = "Semester ${subject.semesterNumber}"
        holder.courseText.text = subject.courseName
        holder.creditsChip.text = "${subject.credits} Credits"

        // Type chip with different colors
        holder.typeChip.text = subject.type
        when {
            subject.type.contains("Theory", ignoreCase = true) -> {
                holder.typeChip.setChipBackgroundColorResource(R.color.chip_theory)
                holder.subjectIcon.setImageResource(R.drawable.ic_book)
            }
            subject.type.contains("Practical", ignoreCase = true) -> {
                holder.typeChip.setChipBackgroundColorResource(R.color.chip_practical)
                holder.subjectIcon.setImageResource(R.drawable.ic_lab)
            }
            subject.type.contains("Elective", ignoreCase = true) -> {
                holder.typeChip.setChipBackgroundColorResource(R.color.chip_elective)
                holder.subjectIcon.setImageResource(R.drawable.ic_star)
            }
            else -> {
                holder.typeChip.setChipBackgroundColorResource(R.color.splash_accent)
                holder.subjectIcon.setImageResource(R.drawable.ic_book)
            }
        }

        // Click to view details
        holder.itemView.setOnClickListener { onSubjectClick(subject) }
    }

    override fun getItemCount(): Int {
        Log.d(TAG, "getItemCount: ${subjectsList.size}")
        return subjectsList.size
    }

    fun updateList(newList: List<Subject>) {
        Log.d(TAG, "updateList called with ${newList.size} items")
        Log.d(TAG, "  Current list size before update: ${subjectsList.size}")

        // DON'T clear if it's the same reference!
        if (subjectsList !== newList) {
            subjectsList.clear()
            subjectsList.addAll(newList)
        }

        Log.d(TAG, "updateList completed, new size: ${subjectsList.size}")
        notifyDataSetChanged()
    }
}