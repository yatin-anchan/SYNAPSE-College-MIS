package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R

data class RollNumberAssignment(
    val studentId: String,
    val studentName: String,
    val newRollNumber: String,
    val currentRollNumber: String
)

class RollNumberPreviewAdapter(
    private val assignments: List<RollNumberAssignment>
) : RecyclerView.Adapter<RollNumberPreviewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rollNumber: TextView = view.findViewById(R.id.roll_number)
        val studentName: TextView = view.findViewById(R.id.student_name)
        val currentRollNumber: TextView = view.findViewById(R.id.current_roll_number)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_roll_number_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val assignment = assignments[position]

        holder.rollNumber.text = assignment.newRollNumber
        holder.studentName.text = assignment.studentName

        if (assignment.currentRollNumber.isEmpty() || assignment.currentRollNumber == "0") {
            holder.currentRollNumber.text = "Current: Not Assigned"
            holder.currentRollNumber.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        } else {
            holder.currentRollNumber.text = "Current: ${assignment.currentRollNumber}"
            holder.currentRollNumber.setTextColor(holder.itemView.context.getColor(R.color.chip_attendance_absent))
        }
    }

    override fun getItemCount() = assignments.size
}