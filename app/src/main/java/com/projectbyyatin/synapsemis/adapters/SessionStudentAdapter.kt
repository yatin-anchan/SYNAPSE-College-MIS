package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Attendance

class SessionStudentAdapter(
    private val list: List<Attendance>
) : RecyclerView.Adapter<SessionStudentAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRollNo: TextView     = itemView.findViewById(R.id.tv_roll_no)
        val tvName: TextView       = itemView.findViewById(R.id.tv_student_name)
        val tvStatus: TextView     = itemView.findViewById(R.id.tv_status)
        val tvRemarks: TextView    = itemView.findViewById(R.id.tv_remarks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session_student, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val att = list[position]

        holder.tvRollNo.text = att.studentRollNo
        holder.tvName.text   = att.studentName

        // Status chip
        holder.tvStatus.text = att.status.replaceFirstChar { it.uppercase() }
        val (bgColor, textColor) = when (att.status.lowercase()) {
            "present" -> Pair(R.color.splash_success, R.color.splash_success)
            "absent"  -> Pair(R.color.splash_error,  R.color.splash_error)
            "late"    -> Pair(R.color.splash_warning,    R.color.splash_warning)
            else      -> Pair(R.color.splash_card_background, R.color.splash_text_secondary)
        }
        holder.tvStatus.backgroundTintList =
            holder.itemView.context.getColorStateList(bgColor)
        holder.tvStatus.setTextColor(holder.itemView.context.getColor(textColor))

        if (att.remarks.isNotEmpty()) {
            holder.tvRemarks.visibility = View.VISIBLE
            holder.tvRemarks.text = att.remarks
        } else {
            holder.tvRemarks.visibility = View.GONE
        }
    }

    override fun getItemCount() = list.size
}