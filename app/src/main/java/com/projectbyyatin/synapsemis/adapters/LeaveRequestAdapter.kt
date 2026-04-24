package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.LeaveRequest

class LeaveRequestAdapter(
    private var leaveList: List<LeaveRequest>,
    private val showActionButtons: Boolean,
    private val onActionListener: (LeaveRequest, String) -> Unit
) : RecyclerView.Adapter<LeaveRequestAdapter.LeaveRequestViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaveRequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leave_request, parent, false)
        return LeaveRequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaveRequestViewHolder, position: Int) {
        val leaveRequest = leaveList[position]
        holder.bind(leaveRequest, showActionButtons)
    }

    override fun getItemCount(): Int = leaveList.size

    fun updateList(newList: List<LeaveRequest>, showButtons: Boolean) {
        leaveList = newList
        notifyDataSetChanged()
    }

    inner class LeaveRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val applicantName: TextView = itemView.findViewById(R.id.applicant_name)
        private val applicantType: TextView = itemView.findViewById(R.id.applicant_type)
        private val dateRange: TextView = itemView.findViewById(R.id.date_range)
        private val numberOfDays: TextView = itemView.findViewById(R.id.number_of_days)
        private val reason: TextView = itemView.findViewById(R.id.reason)
        private val statusChip: Chip = itemView.findViewById(R.id.status_chip)
        private val approveBtn: Button = itemView.findViewById(R.id.btn_approve)
        private val rejectBtn: Button = itemView.findViewById(R.id.btn_reject)
        private val detailsBtn: ImageView = itemView.findViewById(R.id.btn_details)
        private val actionContainer: ViewGroup = itemView.findViewById(R.id.action_container)

        fun bind(leave: LeaveRequest, showActions: Boolean) {
            applicantName.text = leave.applicantName
            applicantType.text = leave.applicantType
            dateRange.text = "${leave.startDate} → ${leave.endDate}"
            numberOfDays.text = "${leave.numberOfDays} days"
            reason.text = leave.reason

            // Status chip styling
            statusChip.text = leave.status?.replaceFirstChar { it.uppercase() } ?: "Pending"
            when (leave.status) {
                "approved" -> {
                    statusChip.setChipBackgroundColor(ContextCompat.getColorStateList(itemView.context, R.color.chip_approved))
                    statusChip.chipStrokeColor = ContextCompat.getColorStateList(itemView.context, R.color.chip_approved_stroke)
                }
                "rejected" -> {
                    statusChip.setChipBackgroundColor(ContextCompat.getColorStateList(itemView.context, R.color.chip_rejected))
                    statusChip.chipStrokeColor = ContextCompat.getColorStateList(itemView.context, R.color.chip_rejected_stroke)
                }
                else -> {
                    statusChip.setChipBackgroundColor(ContextCompat.getColorStateList(itemView.context, R.color.chip_pending))
                    statusChip.chipStrokeColor = ContextCompat.getColorStateList(itemView.context, R.color.chip_pending_stroke)
                }
            }

            // Action buttons visibility
            actionContainer.visibility = if (showActions && leave.status == "pending") View.VISIBLE else View.GONE

            approveBtn.setOnClickListener { onActionListener(leave, "approve") }
            rejectBtn.setOnClickListener { onActionListener(leave, "reject") }
            detailsBtn.setOnClickListener { onActionListener(leave, "view") }
        }
    }
}