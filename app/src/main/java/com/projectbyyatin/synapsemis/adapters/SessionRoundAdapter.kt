package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.RoomAllocation

class SessionRoundAdapter(
    private var allocations: MutableList<RoomAllocation>,
    private var isLocked: Boolean,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<SessionRoundAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val roomNoText: TextView = view.findViewById(R.id.room_no_text)
        val teacherNameText: TextView = view.findViewById(R.id.teacher_name_text)
        val classNameText: TextView = view.findViewById(R.id.class_name_text)
        val statusText: TextView = view.findViewById(R.id.status_text)
        val btnEdit: ImageButton = view.findViewById(R.id.btn_edit)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_allocation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val allocation = allocations[position]

        holder.roomNoText.text = "Room: ${allocation.roomNo}"
        holder.teacherNameText.text = allocation.teacherName
        holder.classNameText.text = allocation.className

        if (allocation.isPresent) {
            holder.statusText.text = "Present"
            holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
        } else {
            holder.statusText.text = "Absent"
            holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
        }

        // Show/hide edit and delete buttons based on lock status
        if (isLocked) {
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        } else {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE

            holder.btnEdit.setOnClickListener { onEdit(position) }
            holder.btnDelete.setOnClickListener { onDelete(position) }
        }
    }

    override fun getItemCount() = allocations.size

    fun updateList(newAllocations: List<RoomAllocation>, locked: Boolean) {
        allocations.clear()
        allocations.addAll(newAllocations)
        isLocked = locked
        notifyDataSetChanged()
    }
}