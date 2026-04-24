package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Invigilator

class InvigilatorsAdapter(
    private var invigilatorsList: List<Invigilator>,
    private val onRemoveClick: (Invigilator) -> Unit
) : RecyclerView.Adapter<InvigilatorsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val teacherName: TextView = itemView.findViewById(R.id.teacher_name)
        val roleChip: Chip = itemView.findViewById(R.id.role_chip)
        val btnRemove: ImageView = itemView.findViewById(R.id.btn_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invigilator, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val invigilator = invigilatorsList[position]

        holder.teacherName.text = invigilator.teacherName

        if (invigilator.role == "supervisor") {
            holder.roleChip.text = "Supervisor"
            holder.roleChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
        } else {
            holder.roleChip.text = "Invigilator"
            holder.roleChip.setChipBackgroundColorResource(android.R.color.holo_blue_dark)
        }

        holder.btnRemove.setOnClickListener { onRemoveClick(invigilator) }
    }

    override fun getItemCount(): Int = invigilatorsList.size

    fun updateList(newList: List<Invigilator>) {
        invigilatorsList = newList
        notifyDataSetChanged()
    }
}
