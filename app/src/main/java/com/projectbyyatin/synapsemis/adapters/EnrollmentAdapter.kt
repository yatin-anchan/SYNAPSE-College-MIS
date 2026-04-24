package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.EnrollmentApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnrollmentAdapter(
    private var enrollments: List<EnrollmentApplication>,
    private val departments: List<Pair<String, String>>,
    private val classes: List<Pair<String, String>>,
    private val onViewProfile: (EnrollmentApplication) -> Unit,
    private val onApprove: (EnrollmentApplication, String, String, String, Int) -> Unit,
    private val onReject: (EnrollmentApplication) -> Unit
) : RecyclerView.Adapter<EnrollmentAdapter.EnrollmentViewHolder>() {

    inner class EnrollmentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tv_student_name)
        val tvReferenceNumber: TextView = view.findViewById(R.id.tv_reference_number)
        val tvStatusBadge: TextView = view.findViewById(R.id.tv_status_badge)
        val tvCourse: TextView = view.findViewById(R.id.tv_course)
        val tvSubmittedDate: TextView = view.findViewById(R.id.tv_submitted_date)

        val assignmentSection: View = view.findViewById(R.id.assignment_section)
        val departmentDropdown: AutoCompleteTextView = view.findViewById(R.id.department_dropdown)
        val classDropdown: AutoCompleteTextView = view.findViewById(R.id.class_dropdown)
        val semesterDropdown: AutoCompleteTextView = view.findViewById(R.id.semester_dropdown)

        val btnViewProfile: MaterialButton = view.findViewById(R.id.btn_view_profile)
        val btnApprove: MaterialButton = view.findViewById(R.id.btn_approve)
        val btnReject: MaterialButton = view.findViewById(R.id.btn_reject)

        var selectedDeptId = ""
        var selectedClassId = ""
        var selectedSemester = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EnrollmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_enrollment, parent, false)
        return EnrollmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: EnrollmentViewHolder, position: Int) {
        val enrollment = enrollments[position]

        holder.tvStudentName.text = enrollment.fullName
        holder.tvReferenceNumber.text = enrollment.referenceNumber
        holder.tvCourse.text = enrollment.appliedFor

        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.tvSubmittedDate.text = dateFormat.format(Date(enrollment.submittedDate))

        // Set status badge
        when (enrollment.status) {
            "profile_submitted" -> {
                holder.tvStatusBadge.text = "PENDING"
                holder.tvStatusBadge.setBackgroundResource(R.drawable.badge_pending)
                holder.assignmentSection.visibility = View.VISIBLE
                holder.btnApprove.visibility = View.VISIBLE
                holder.btnReject.visibility = View.VISIBLE
            }
            "approved" -> {
                holder.tvStatusBadge.text = "APPROVED"
                holder.tvStatusBadge.setBackgroundResource(R.drawable.badge_accepted)
                holder.assignmentSection.visibility = View.GONE
                holder.btnApprove.visibility = View.GONE
                holder.btnReject.visibility = View.GONE
            }
            "rejected" -> {
                holder.tvStatusBadge.text = "REJECTED"
                holder.tvStatusBadge.setBackgroundResource(R.drawable.badge_rejected)
                holder.assignmentSection.visibility = View.GONE
                holder.btnApprove.visibility = View.GONE
                holder.btnReject.visibility = View.GONE
            }
        }

        // Setup department dropdown
        val deptNames = departments.map { it.second }
        val deptAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_dropdown_item_1line, deptNames)
        holder.departmentDropdown.setAdapter(deptAdapter)
        holder.departmentDropdown.setOnItemClickListener { _, _, pos, _ ->
            holder.selectedDeptId = departments[pos].first
        }

        // Setup class dropdown
        val classNames = classes.map { it.second }
        val classAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_dropdown_item_1line, classNames)
        holder.classDropdown.setAdapter(classAdapter)
        holder.classDropdown.setOnItemClickListener { _, _, pos, _ ->
            holder.selectedClassId = classes[pos].first
        }

        // Setup semester dropdown
        val semesters = listOf("1", "2", "3", "4", "5", "6", "7", "8")
        val semesterAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_dropdown_item_1line, semesters)
        holder.semesterDropdown.setAdapter(semesterAdapter)
        holder.semesterDropdown.setOnItemClickListener { _, _, pos, _ ->
            holder.selectedSemester = pos + 1
        }

        // Click listeners
        holder.btnViewProfile.setOnClickListener {
            onViewProfile(enrollment)
        }

        holder.btnApprove.setOnClickListener {
            if (holder.selectedDeptId.isEmpty() || holder.selectedClassId.isEmpty()) {
                // Show error
                return@setOnClickListener
            }
            onApprove(
                enrollment,
                holder.selectedDeptId,
                holder.selectedClassId,
                holder.departmentDropdown.text.toString(),
                holder.selectedSemester
            )
        }

        holder.btnReject.setOnClickListener {
            onReject(enrollment)
        }
    }

    override fun getItemCount() = enrollments.size

    fun updateData(newEnrollments: List<EnrollmentApplication>) {
        enrollments = newEnrollments
        notifyDataSetChanged()
    }
}
