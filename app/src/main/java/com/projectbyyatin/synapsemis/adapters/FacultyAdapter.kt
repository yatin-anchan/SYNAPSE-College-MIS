package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Faculty
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class FacultyAdapter(
    private var facultyList: List<Faculty>,
    private val onViewClick: (Faculty) -> Unit,
    private val onEditClick: (Faculty) -> Unit,
    private val onDeleteClick: (Faculty) -> Unit,
    private val onAccessToggle: (Faculty, Boolean) -> Unit
) : RecyclerView.Adapter<FacultyAdapter.FacultyViewHolder>() {

    private var filteredList = facultyList.toMutableList()

    inner class FacultyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.faculty_profile_image)
        val facultyName: TextView = itemView.findViewById(R.id.faculty_name)
        val facultyDesignation: TextView = itemView.findViewById(R.id.faculty_designation)
        val facultyDepartment: TextView = itemView.findViewById(R.id.faculty_department)
        val facultyEmail: TextView = itemView.findViewById(R.id.faculty_email)
        val facultyPhone: TextView = itemView.findViewById(R.id.faculty_phone)
        val accessSwitch: SwitchMaterial = itemView.findViewById(R.id.access_switch)
        val btnView: ImageView = itemView.findViewById(R.id.btn_view)
        val btnEdit: ImageView = itemView.findViewById(R.id.btn_edit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)
        val profileStatusBadge: ImageView = itemView.findViewById(R.id.profile_status_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacultyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_faculty, parent, false)
        return FacultyViewHolder(view)
    }

    override fun onBindViewHolder(holder: FacultyViewHolder, position: Int) {
        val faculty = filteredList[position]

        // Load profile image
        if (faculty.photoUrl.isNotEmpty()) {
            Picasso.get()
                .load(faculty.photoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(holder.profileImage)
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_person)
        }

        holder.facultyName.text = faculty.name
        holder.facultyDesignation.text = faculty.designation.ifEmpty { "Faculty Member" }
        holder.facultyDepartment.text = faculty.department.ifEmpty { "No Department" }
        holder.facultyEmail.text = faculty.email
        holder.facultyPhone.text = faculty.phone

        // Profile status badge
        if (faculty.profileCompleted) {
            holder.profileStatusBadge.setImageResource(R.drawable.ic_check_circle)
            holder.profileStatusBadge.setColorFilter(
                holder.itemView.context.getColor(R.color.splash_accent)
            )
        } else {
            holder.profileStatusBadge.setImageResource(R.drawable.ic_pending)
            holder.profileStatusBadge.setColorFilter(
                holder.itemView.context.getColor(android.R.color.holo_orange_light)
            )
        }

        // Access switch
        holder.accessSwitch.isChecked = faculty.appAccessEnabled
        holder.accessSwitch.setOnCheckedChangeListener { _, isChecked ->
            onAccessToggle(faculty, isChecked)
        }

        // Click listeners
        holder.itemView.setOnClickListener { onViewClick(faculty) }
        holder.btnView.setOnClickListener { onViewClick(faculty) }
        holder.btnEdit.setOnClickListener { onEditClick(faculty) }
        holder.btnDelete.setOnClickListener { onDeleteClick(faculty) }
    }

    override fun getItemCount(): Int = filteredList.size

    fun updateList(newList: List<Faculty>) {
        facultyList = newList
        filteredList = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            facultyList.toMutableList()
        } else {
            facultyList.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.email.contains(query, ignoreCase = true) ||
                        it.employeeId.contains(query, ignoreCase = true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }

    fun filterByDepartment(department: String) {
        filteredList = if (department == "All") {
            facultyList.toMutableList()
        } else {
            facultyList.filter { it.department == department }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
