package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.faculty.ViewAttendanceItem

class ViewAttendanceAdapter(
    private val items: List<ViewAttendanceItem>
) : RecyclerView.Adapter<ViewAttendanceAdapter.AttendanceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_faculty, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val subjectText: TextView = itemView.findViewById(R.id.txt_subject)
        private val classText: TextView = itemView.findViewById(R.id.txt_class)
        private val dateText: TextView = itemView.findViewById(R.id.txt_date)
        private val countText: TextView = itemView.findViewById(R.id.txt_count)

        fun bind(item: ViewAttendanceItem) {
            subjectText.text = item.subjectName
            classText.text = "${item.className} | Period ${item.period}"
            dateText.text = item.date
            countText.text = "Present: ${item.present}  |  Absent: ${item.absent}"
        }
    }
}