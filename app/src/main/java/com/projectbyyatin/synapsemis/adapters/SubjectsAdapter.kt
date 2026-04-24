package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Subject

class SubjectsAdapter(
    private var subjectsList: List<Subject>,
    private val onAssignFacultyClick: (Subject) -> Unit,
    private val onEditClick: (Subject) -> Unit,
    private val onDeleteClick: (Subject) -> Unit
) : RecyclerView.Adapter<SubjectsAdapter.SubjectViewHolder>() {

    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subjectIcon: ImageView = itemView.findViewById(R.id.subject_icon)
        val subjectName: TextView = itemView.findViewById(R.id.subject_name)
        val subjectCode: TextView = itemView.findViewById(R.id.subject_code)
        val creditsChip: Chip = itemView.findViewById(R.id.credits_chip)
        val typeChip: Chip = itemView.findViewById(R.id.type_chip)
        val facultyName: TextView = itemView.findViewById(R.id.faculty_name)
        val btnAssignFaculty: ImageView = itemView.findViewById(R.id.btn_assign_faculty)
        val btnEdit: ImageView = itemView.findViewById(R.id.btn_edit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = subjectsList[position]

        holder.subjectName.text = subject.name
        holder.subjectCode.text = subject.code
        holder.creditsChip.text = "${subject.credits} Credits"

        // Type chip with different colors
        holder.typeChip.text = subject.type
        when (subject.type) {
            "Theory" -> holder.typeChip.setChipBackgroundColorResource(android.R.color.holo_blue_dark)
            "Practical" -> holder.typeChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
            "Elective" -> holder.typeChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
        }

        // Faculty assignment
        if (subject.assignedFacultyName.isNotEmpty()) {
            holder.facultyName.text = "Faculty: ${subject.assignedFacultyName}"
            holder.facultyName.visibility = View.VISIBLE
            holder.facultyName.setTextColor(holder.itemView.context.getColor(R.color.splash_accent))
            holder.btnAssignFaculty.setImageResource(R.drawable.ic_person_check)
        } else {
            holder.facultyName.text = "No faculty assigned"
            holder.facultyName.visibility = View.VISIBLE
            holder.facultyName.setTextColor(holder.itemView.context.getColor(R.color.splash_text_secondary))
            holder.btnAssignFaculty.setImageResource(R.drawable.ic_person_add)
        }

        // Button clicks
        holder.btnAssignFaculty.setOnClickListener { onAssignFacultyClick(subject) }
        holder.btnEdit.setOnClickListener { onEditClick(subject) }
        holder.btnDelete.setOnClickListener { onDeleteClick(subject) }
    }

    override fun getItemCount(): Int = subjectsList.size

    fun updateList(newList: List<Subject>) {
        subjectsList = newList
        notifyDataSetChanged()
    }
}
