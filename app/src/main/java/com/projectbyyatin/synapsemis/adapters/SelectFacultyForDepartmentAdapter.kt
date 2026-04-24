package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Faculty

class SelectFacultyForDepartmentAdapter(
    private var facultyList: List<Faculty>,
    private val onAddClick: (Faculty) -> Unit
) : RecyclerView.Adapter<SelectFacultyForDepartmentAdapter.FacultyViewHolder>() {

    inner class FacultyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val facultyName: TextView = itemView.findViewById(R.id.faculty_name)
        val facultyDesignation: TextView = itemView.findViewById(R.id.faculty_designation)
        val facultyEmail: TextView = itemView.findViewById(R.id.faculty_email)
        val employeeId: TextView = itemView.findViewById(R.id.employee_id)
        val btnAdd: MaterialButton = itemView.findViewById(R.id.btn_add)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacultyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_faculty_for_department, parent, false)
        return FacultyViewHolder(view)
    }

    override fun onBindViewHolder(holder: FacultyViewHolder, position: Int) {
        val faculty = facultyList[position]

        holder.facultyName.text = faculty.name
        holder.facultyDesignation.text = faculty.designation
        holder.facultyEmail.text = faculty.email
        holder.employeeId.text = "ID: ${faculty.employeeId}"

        holder.btnAdd.setOnClickListener { onAddClick(faculty) }
    }

    override fun getItemCount(): Int = facultyList.size

    fun updateList(newList: List<Faculty>) {
        facultyList = newList
        notifyDataSetChanged()
    }
}
