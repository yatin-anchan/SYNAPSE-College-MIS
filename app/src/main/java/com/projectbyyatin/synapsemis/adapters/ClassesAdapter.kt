package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Class

class ClassesAdapter(
    private var classesList: List<Class>,
    private val onViewClick: (Class) -> Unit,
    private val onEditClick: (Class) -> Unit
) : RecyclerView.Adapter<ClassesAdapter.ClassViewHolder>() {

    inner class ClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val classIcon: ImageView = itemView.findViewById(R.id.class_icon)
        val className: TextView = itemView.findViewById(R.id.class_name)
        val courseName: TextView = itemView.findViewById(R.id.course_name)
        val currentSemesterChip: Chip = itemView.findViewById(R.id.current_semester_chip)
        val classSizeChip: Chip = itemView.findViewById(R.id.class_size_chip)
        val statusChip: Chip = itemView.findViewById(R.id.status_chip)
        val classTeacherName: TextView = itemView.findViewById(R.id.class_teacher_name)
        val batchText: TextView = itemView.findViewById(R.id.batch_text)
        val academicYear: TextView = itemView.findViewById(R.id.academic_year)
        val btnView: ImageView = itemView.findViewById(R.id.btn_view)
        val btnEdit: ImageView = itemView.findViewById(R.id.btn_edit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_class, parent, false)
        return ClassViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val classItem = classesList[position]

        holder.className.text = classItem.className
        holder.courseName.text = classItem.courseName
        holder.currentSemesterChip.text = "Sem ${classItem.currentSemester}"
        holder.classSizeChip.text = "${classItem.currentSize}/${classItem.maxSize}"
        holder.classTeacherName.text = "Teacher: ${classItem.classTeacherName}"
        holder.batchText.text = "Batch: ${classItem.batch}"
        holder.academicYear.text = classItem.academicYear

        // Status chip
        if (classItem.isActive) {
            holder.statusChip.text = "Active"
            holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
        } else {
            holder.statusChip.text = "Inactive"
            holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_red_dark)
        }

        holder.itemView.setOnClickListener { onViewClick(classItem) }
        holder.btnView.setOnClickListener { onViewClick(classItem) }
        holder.btnEdit.setOnClickListener { onEditClick(classItem) }
    }

    override fun getItemCount(): Int = classesList.size

    fun updateList(newList: List<Class>) {
        classesList = newList
        notifyDataSetChanged()
    }
}
