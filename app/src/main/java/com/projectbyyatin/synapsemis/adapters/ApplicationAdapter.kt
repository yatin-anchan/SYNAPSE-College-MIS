package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Application
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApplicationAdapter(
    private var applications: List<Application>,
    private val onViewDetails: (Application) -> Unit,
    private val onAccept: (Application) -> Unit,
    private val onReject: (Application) -> Unit
) : RecyclerView.Adapter<ApplicationAdapter.ApplicationViewHolder>() {

    inner class ApplicationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvApplicantName: TextView = view.findViewById(R.id.tv_applicant_name)
        val tvReferenceNumber: TextView = view.findViewById(R.id.tv_reference_number)
        val tvStatusBadge: TextView = view.findViewById(R.id.tv_status_badge)
        val tvEmail: TextView = view.findViewById(R.id.tv_email)
        val tvPhone: TextView = view.findViewById(R.id.tv_phone)
        val tvCourse: TextView = view.findViewById(R.id.tv_course)
        val tvAppliedDate: TextView = view.findViewById(R.id.tv_applied_date)
        val btnViewDetails: MaterialButton = view.findViewById(R.id.btn_view_details)
        val btnAccept: MaterialButton = view.findViewById(R.id.btn_accept)
        val btnReject: MaterialButton = view.findViewById(R.id.btn_reject)
        val actionButtons: View = view.findViewById(R.id.action_buttons)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_application, parent, false)
        return ApplicationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
        val application = applications[position]

        holder.tvApplicantName.text = application.fullName
        holder.tvReferenceNumber.text = application.referenceNumber
        holder.tvEmail.text = application.email
        holder.tvPhone.text = application.phone
        holder.tvCourse.text = application.appliedFor

        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.tvAppliedDate.text = dateFormat.format(Date(application.appliedDate))

        // Set status badge
        when (application.status) {
            "pending" -> {
                holder.tvStatusBadge.text = "PENDING"
                holder.tvStatusBadge.setBackgroundResource(R.drawable.badge_pending)
                holder.btnAccept.visibility = View.VISIBLE
                holder.btnReject.visibility = View.VISIBLE
            }
            "accepted" -> {
                holder.tvStatusBadge.text = "ACCEPTED"
                holder.tvStatusBadge.setBackgroundResource(R.drawable.badge_accepted)
                holder.btnAccept.visibility = View.GONE
                holder.btnReject.visibility = View.GONE
            }
            "rejected" -> {
                holder.tvStatusBadge.text = "REJECTED"
                holder.tvStatusBadge.setBackgroundResource(R.drawable.badge_rejected)
                holder.btnAccept.visibility = View.GONE
                holder.btnReject.visibility = View.GONE
            }
        }

        // Click listeners
        holder.btnViewDetails.setOnClickListener {
            onViewDetails(application)
        }

        holder.btnAccept.setOnClickListener {
            onAccept(application)
        }

        holder.btnReject.setOnClickListener {
            onReject(application)
        }
    }

    override fun getItemCount() = applications.size

    fun updateData(newApplications: List<Application>) {
        applications = newApplications
        notifyDataSetChanged()
    }
}
