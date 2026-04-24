package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Semester

class SemestersAdapter(
    private var semestersList: List<Semester>,
    private val onViewClick: (Semester) -> Unit
) : RecyclerView.Adapter<SemestersAdapter.SemesterViewHolder>() {

    inner class SemesterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val semesterIcon: ImageView = itemView.findViewById(R.id.semester_icon)
        val semesterName: TextView = itemView.findViewById(R.id.semester_name)
        val semesterNumber: TextView = itemView.findViewById(R.id.semester_number)
        val subjectsCount: TextView = itemView.findViewById(R.id.subjects_count)
        val statusChip: Chip = itemView.findViewById(R.id.status_chip)
        val btnView: ImageView = itemView.findViewById(R.id.btn_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SemesterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_semester, parent, false)
        return SemesterViewHolder(view)
    }

    override fun onBindViewHolder(holder: SemesterViewHolder, position: Int) {
        val semester = semestersList[position]

        holder.semesterName.text = semester.semesterName
        holder.semesterNumber.text = "Sem ${semester.semesterNumber}"
        holder.subjectsCount.text = "${semester.totalSubjects} Subjects"

        // Status chip
        if (semester.isActive) {
            holder.statusChip.text = "Active"
            holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
        } else {
            holder.statusChip.text = "Inactive"
            holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_red_dark)
        }

        holder.itemView.setOnClickListener { onViewClick(semester) }
        holder.btnView.setOnClickListener { onViewClick(semester) }
    }

    override fun getItemCount(): Int = semestersList.size

    fun updateList(newList: List<Semester>) {
        semestersList = newList
        notifyDataSetChanged()
    }
}
