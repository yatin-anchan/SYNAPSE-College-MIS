package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Faculty
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class SelectFacultyAdapter(
    private var facultyList: List<Faculty>,
    private val onFacultyClick: (Faculty) -> Unit
) : RecyclerView.Adapter<SelectFacultyAdapter.ViewHolder>() {

    private var filteredList = facultyList.toMutableList()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.profile_image)
        val facultyName: TextView = itemView.findViewById(R.id.faculty_name)
        val facultyDesignation: TextView = itemView.findViewById(R.id.faculty_designation)
        val facultyEmail: TextView = itemView.findViewById(R.id.faculty_email)
        val roleChip: Chip = itemView.findViewById(R.id.role_chip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_faculty, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
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
        holder.facultyEmail.text = faculty.email

        // Show role chip if HOD
        if (faculty.role == "HOD") {
            holder.roleChip.visibility = View.VISIBLE
            holder.roleChip.text = "Current HOD"
            holder.roleChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
        } else {
            holder.roleChip.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onFacultyClick(faculty) }
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
                        it.designation.contains(query, ignoreCase = true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
