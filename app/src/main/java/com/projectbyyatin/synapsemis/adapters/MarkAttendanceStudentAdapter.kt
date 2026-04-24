package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Student

class MarkAttendanceStudentAdapter(
    private var studentsList: MutableList<Student>,
    private val onStatusChanged: () -> Unit
) : RecyclerView.Adapter<MarkAttendanceStudentAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.card_view)
        val tvName: TextView = view.findViewById(R.id.tv_student_name)
        val tvRollNumber: TextView = view.findViewById(R.id.tv_roll_number)
        val chipPresent: Chip = view.findViewById(R.id.chip_present)
        val chipAbsent: Chip = view.findViewById(R.id.chip_absent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mark_attendance_student, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = studentsList[position]

        holder.tvName.text = student.fullName
        holder.tvRollNumber.text = "Roll: ${student.rollNumber}"

        // Update chip states
        updateChipStates(holder, student.attendanceStatus ?: "present")

        // Present chip click
        holder.chipPresent.setOnClickListener {
            student.attendanceStatus = "present"
            updateChipStates(holder, "present")
            onStatusChanged()
        }

        // Absent chip click
        holder.chipAbsent.setOnClickListener {
            student.attendanceStatus = "absent"
            updateChipStates(holder, "absent")
            onStatusChanged()
        }

        // Card background based on status
        val bgColor = when (student.attendanceStatus) {
            "present" -> ContextCompat.getColor(holder.itemView.context, R.color.chip_present)
            "absent" -> ContextCompat.getColor(holder.itemView.context, R.color.chip_absent)
            else -> ContextCompat.getColor(holder.itemView.context, R.color.card_background)
        }
        holder.cardView.setCardBackgroundColor(bgColor and 0x20FFFFFF) // 20% opacity
    }

    private fun updateChipStates(holder: ViewHolder, status: String) {
        val context = holder.itemView.context

        when (status) {
            "present" -> {
                holder.chipPresent.isChecked = true
                holder.chipPresent.setChipBackgroundColorResource(R.color.chip_present)
                holder.chipPresent.setTextColor(ContextCompat.getColor(context, R.color.white))

                holder.chipAbsent.isChecked = false
                holder.chipAbsent.setChipBackgroundColorResource(R.color.chip_unselected)
                holder.chipAbsent.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            "absent" -> {
                holder.chipAbsent.isChecked = true
                holder.chipAbsent.setChipBackgroundColorResource(R.color.chip_absent)
                holder.chipAbsent.setTextColor(ContextCompat.getColor(context, R.color.white))

                holder.chipPresent.isChecked = false
                holder.chipPresent.setChipBackgroundColorResource(R.color.chip_unselected)
                holder.chipPresent.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
        }
    }

    override fun getItemCount() = studentsList.size

    fun updateList(newList: List<Student>) {
        studentsList.clear()
        studentsList.addAll(newList)
        notifyDataSetChanged()
    }
}