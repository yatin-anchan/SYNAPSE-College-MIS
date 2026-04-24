package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.TimetableEntry

class TimetableAdapter(
    private var timetableList: List<TimetableEntry>,
    private val onEditClick: (TimetableEntry) -> Unit
) : RecyclerView.Adapter<TimetableAdapter.TimetableViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimetableViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable_entry, parent, false)
        return TimetableViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimetableViewHolder, position: Int) {
        holder.bind(timetableList[position])
    }

    override fun getItemCount(): Int = timetableList.size

    fun updateList(newList: List<TimetableEntry>) {
        timetableList = newList
        notifyDataSetChanged()
    }

    inner class TimetableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val subjectCode: TextView = itemView.findViewById(R.id.subject_code)
        private val subjectName: TextView = itemView.findViewById(R.id.subject_name)
        private val facultyName: TextView = itemView.findViewById(R.id.faculty_name)
        private val timeRange: TextView = itemView.findViewById(R.id.time_range)
        private val room: TextView = itemView.findViewById(R.id.room)
        private val semester: TextView = itemView.findViewById(R.id.semester)
        private val card: MaterialCardView = itemView.findViewById(R.id.card)

        fun bind(entry: TimetableEntry) {
            subjectCode.text = entry.subjectCode
            subjectName.text = entry.subject
            facultyName.text = entry.facultyName
            timeRange.text = "${entry.startTime} - ${entry.endTime}"
            room.text = "Room: ${entry.room}"
            semester.text = entry.semester

            card.setOnClickListener { onEditClick(entry) }
        }
    }
}
