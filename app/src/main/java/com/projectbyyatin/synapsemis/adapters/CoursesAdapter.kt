package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.switchmaterial.SwitchMaterial
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Course

class CoursesAdapter(
    private var coursesList: List<Course>,
    private val onViewClick: (Course) -> Unit,
    private val onEditClick: (Course) -> Unit,
    private val onDeleteClick: (Course) -> Unit,
    private val onToggleActive: (Course, Boolean) -> Unit
) : RecyclerView.Adapter<CoursesAdapter.CourseViewHolder>() {

    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val courseIcon: ImageView = itemView.findViewById(R.id.course_icon)
        val courseName: TextView = itemView.findViewById(R.id.course_name)
        val courseCode: TextView = itemView.findViewById(R.id.course_code)
        val courseDuration: TextView = itemView.findViewById(R.id.course_duration)
        val semestersChip: Chip = itemView.findViewById(R.id.semesters_chip)
        val statusChip: Chip = itemView.findViewById(R.id.status_chip)
        val activeSwitch: SwitchMaterial = itemView.findViewById(R.id.active_switch)
        val btnView: ImageView = itemView.findViewById(R.id.btn_view)
        val btnEdit: ImageView = itemView.findViewById(R.id.btn_edit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = coursesList[position]

        holder.courseName.text = course.name
        holder.courseCode.text = course.code
        holder.courseDuration.text = course.duration
        holder.semestersChip.text = "${course.totalSemesters} Semesters"

        // Status chip
        if (course.isActive) {
            holder.statusChip.text = "Active"
            holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
        } else {
            holder.statusChip.text = "Inactive"
            holder.statusChip.setChipBackgroundColorResource(android.R.color.holo_red_dark)
        }

        // Active switch
        holder.activeSwitch.isChecked = course.isActive
        holder.activeSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggleActive(course, isChecked)
        }

        // Button clicks
        holder.btnView.setOnClickListener { onViewClick(course) }
        holder.btnEdit.setOnClickListener { onEditClick(course) }
        holder.btnDelete.setOnClickListener { onDeleteClick(course) }
    }

    override fun getItemCount(): Int = coursesList.size

    fun updateList(newList: List<Course>) {
        coursesList = newList
        notifyDataSetChanged()
    }
}
