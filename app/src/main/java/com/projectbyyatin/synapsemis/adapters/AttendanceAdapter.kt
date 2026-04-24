package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Attendance
import de.hdodenhof.circleimageview.CircleImageView

class AttendanceAdapter(
    private var attendanceList: List<Attendance>,
    private val onStatusChange: (Attendance, String) -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    inner class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: CircleImageView = itemView.findViewById(R.id.profile_image)
        val studentName: TextView = itemView.findViewById(R.id.student_name)
        val studentRollNo: TextView = itemView.findViewById(R.id.student_roll_no)
        val statusChipGroup: ChipGroup = itemView.findViewById(R.id.status_chip_group)
        val chipPresent: Chip = itemView.findViewById(R.id.chip_present)
        val chipAbsent: Chip = itemView.findViewById(R.id.chip_absent)
        val chipLate: Chip = itemView.findViewById(R.id.chip_late)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val attendance = attendanceList[position]

        holder.studentName.text = attendance.studentName
        holder.studentRollNo.text = "Roll No: ${attendance.studentRollNo}"

        // Set checked chip based on status
        when (attendance.status) {
            "present" -> holder.chipPresent.isChecked = true
            "absent" -> holder.chipAbsent.isChecked = true
            "late" -> holder.chipLate.isChecked = true
        }

        // Handle chip selection
        holder.statusChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val newStatus = when (checkedIds[0]) {
                    R.id.chip_present -> "present"
                    R.id.chip_absent -> "absent"
                    R.id.chip_late -> "late"
                    else -> "absent"
                }

                if (newStatus != attendance.status) {
                    onStatusChange(attendance, newStatus)
                    attendance.status = newStatus
                }
            }
        }
    }

    override fun getItemCount(): Int = attendanceList.size

    fun updateList(newList: List<Attendance>) {
        attendanceList = newList
        notifyDataSetChanged()
    }
}
