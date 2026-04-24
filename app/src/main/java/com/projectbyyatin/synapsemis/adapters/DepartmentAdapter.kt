package com.projectbyyatin.synapsemis.adapters

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Department
import com.squareup.picasso.Picasso

class DepartmentAdapter(
    private var departmentList: List<Department>,
    private val onManageClick: (Department) -> Unit,
    private val onDeleteClick: (Department) -> Unit
) : RecyclerView.Adapter<DepartmentAdapter.DepartmentViewHolder>() {

    private var filteredList = departmentList.toMutableList()
    private val firestore = FirebaseFirestore.getInstance()
    private var loadingDialog: AlertDialog? = null

    inner class DepartmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val departmentImage: ImageView = itemView.findViewById(R.id.department_image)
        val departmentName: TextView = itemView.findViewById(R.id.department_name)
        val departmentCode: TextView = itemView.findViewById(R.id.department_code)
        val collegeChip: Chip = itemView.findViewById(R.id.college_chip)
        val streamChip: Chip = itemView.findViewById(R.id.stream_chip)
        val hodName: TextView = itemView.findViewById(R.id.hod_name)
        val facultyCount: TextView = itemView.findViewById(R.id.faculty_count)
        val studentCount: TextView = itemView.findViewById(R.id.student_count)
        val btnManage: ImageView = itemView.findViewById(R.id.btn_manage)
        val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DepartmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_department, parent, false)
        return DepartmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DepartmentViewHolder, position: Int) {
        val department = filteredList[position]

        if (department.photoUrl.isNotEmpty()) {
            Picasso.get()
                .load(department.photoUrl)
                .placeholder(R.drawable.ic_departments)
                .error(R.drawable.ic_departments)
                .into(holder.departmentImage)
        } else {
            holder.departmentImage.setImageResource(R.drawable.ic_departments)
        }

        holder.departmentName.text = department.name
        holder.departmentCode.text = department.code
        holder.hodName.text = department.hod.ifEmpty { "Not Assigned" }

        // Load real-time counts
        loadRealTimeCounts(department.id, holder)

        // College chip - compact text
        holder.collegeChip.text = if (department.college == "JR") "JR" else "SR"
        holder.collegeChip.setChipBackgroundColorResource(
            if (department.college == "JR") R.color.splash_accent else android.R.color.holo_blue_dark
        )

        // Stream chip
        holder.streamChip.text = department.stream
        val streamColor = when (department.stream) {
            "Science" -> android.R.color.holo_green_dark
            "Commerce" -> android.R.color.holo_orange_dark
            "Arts" -> android.R.color.holo_purple
            else -> R.color.splash_text_secondary
        }
        holder.streamChip.setChipBackgroundColorResource(streamColor)

        // Click listeners
        holder.itemView.setOnClickListener { onManageClick(department) }
        holder.btnManage.setOnClickListener { onManageClick(department) }
        holder.btnDelete.setOnClickListener {
            showDeleteConfirmation(holder.itemView.context, department)
        }
    }

    private fun showDeleteConfirmation(context: Context, department: Department) {
        AlertDialog.Builder(context)
            .setTitle("Delete Department")
            .setMessage("Are you sure you want to delete '${department.name}'?\n\nAll faculty will be unassigned from this department.")
            .setPositiveButton("Delete") { _, _ ->
                deleteDepartmentWithCascade(context, department)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDepartmentWithCascade(context: Context, department: Department) {
        showLoadingDialog(context)

        // Step 1: Unassign all faculty from this department
        unassignFacultyFromDepartment(context, department.id) { success ->
            if (success) {
                // Step 2: Delete the department
                firestore.collection("departments")
                    .document(department.id)
                    .delete()
                    .addOnSuccessListener {
                        hideLoadingDialog()
                        Toast.makeText(context, "Department deleted successfully", Toast.LENGTH_SHORT).show()

                        // Remove from list and update UI
                        val position = filteredList.indexOf(department)
                        if (position != -1) {
                            filteredList.removeAt(position)
                            notifyItemRemoved(position)
                        }

                        // Also remove from main list
                        val mainPosition = departmentList.indexOf(department)
                        if (mainPosition != -1) {
                            departmentList = departmentList.toMutableList().apply {
                                removeAt(mainPosition)
                            }
                        }

                        // Trigger callback for parent activity
                        onDeleteClick(department)
                    }
                    .addOnFailureListener { e ->
                        hideLoadingDialog()
                        Toast.makeText(context, "Error deleting department: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("DepartmentAdapter", "Error deleting department", e)
                    }
            } else {
                hideLoadingDialog()
                Toast.makeText(context, "Failed to unassign faculty. Department not deleted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unassignFacultyFromDepartment(
        context: Context,
        departmentId: String,
        callback: (Boolean) -> Unit
    ) {
        Log.d("DepartmentAdapter", "Unassigning faculty from department: $departmentId")

        // Find all faculty assigned to this department
        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("DepartmentAdapter", "No faculty to unassign")
                    callback(true)
                    return@addOnSuccessListener
                }

                Log.d("DepartmentAdapter", "Found ${documents.size()} faculty to unassign")

                // Batch update all faculty
                val batch = firestore.batch()

                documents.forEach { doc ->
                    val facultyRef = firestore.collection("faculty").document(doc.id)
                    batch.update(
                        facultyRef, mapOf(
                            "departmentId" to "",
                            "department" to ""
                        )
                    )
                }

                // Commit the batch
                batch.commit()
                    .addOnSuccessListener {
                        Log.d("DepartmentAdapter", "Successfully unassigned ${documents.size()} faculty")
                        Toast.makeText(
                            context,
                            "${documents.size()} faculty members unassigned",
                            Toast.LENGTH_SHORT
                        ).show()
                        callback(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("DepartmentAdapter", "Error unassigning faculty", e)
                        callback(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("DepartmentAdapter", "Error finding faculty", e)
                callback(false)
            }
    }

    private fun showLoadingDialog(context: Context) {
        if (loadingDialog == null) {
            val progressBar = ProgressBar(context).apply {
                setPadding(50, 50, 50, 50)
            }

            loadingDialog = AlertDialog.Builder(context)
                .setView(progressBar)
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun loadRealTimeCounts(departmentId: String, holder: DepartmentViewHolder) {
        // Show loading state
        holder.facultyCount.text = "Loading..."
        holder.studentCount.text = "Loading..."

        // Count faculty members
        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                holder.facultyCount.text = "$count Faculty"
            }
            .addOnFailureListener {
                holder.facultyCount.text = "0 Faculty"
            }

        // Count students
        firestore.collection("students")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                holder.studentCount.text = "$count Students"
            }
            .addOnFailureListener {
                holder.studentCount.text = "0 Students"
            }
    }

    override fun getItemCount(): Int = filteredList.size

    fun updateList(newList: List<Department>) {
        departmentList = newList
        filteredList = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(college: String, stream: String, query: String) {
        filteredList = departmentList.filter { department ->
            val matchesCollege = department.college == college
            val matchesStream = stream == "All" || department.stream == stream
            val matchesQuery = query.isEmpty() ||
                    department.name.contains(query, ignoreCase = true) ||
                    department.code.contains(query, ignoreCase = true) ||
                    department.hod.contains(query, ignoreCase = true)

            matchesCollege && matchesStream && matchesQuery
        }.toMutableList()
        notifyDataSetChanged()
    }

    // Clean up dialog when adapter is destroyed
    fun cleanup() {
        hideLoadingDialog()
    }
}
