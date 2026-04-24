package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackApplicationActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore

    private lateinit var toolbar: Toolbar
    private lateinit var referenceInput: TextInputEditText
    private lateinit var referenceLayout: TextInputLayout
    private lateinit var btnTrack: MaterialButton
    private lateinit var progressBar: ProgressBar

    private lateinit var searchCard: MaterialCardView
    private lateinit var statusCard: MaterialCardView

    private lateinit var tvApplicantName: TextView
    private lateinit var tvApplicantEmail: TextView
    private lateinit var tvAppliedCourse: TextView
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvAppliedDate: TextView
    private lateinit var tvStatusMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_application)

        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupClickListeners()

        // Auto-fill if reference number is passed
        intent.getStringExtra("REFERENCE_NUMBER")?.let { refNum ->
            referenceInput.setText(refNum)
            trackApplication(refNum)
        }
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        referenceInput = findViewById(R.id.reference_input)
        referenceLayout = findViewById(R.id.reference_layout)
        btnTrack = findViewById(R.id.btn_track)
        progressBar = findViewById(R.id.progress_bar)

        searchCard = findViewById(R.id.search_card)
        statusCard = findViewById(R.id.status_card)

        tvApplicantName = findViewById(R.id.tv_applicant_name)
        tvApplicantEmail = findViewById(R.id.tv_applicant_email)
        tvAppliedCourse = findViewById(R.id.tv_applied_course)
        ivStatusIcon = findViewById(R.id.iv_status_icon)
        tvStatus = findViewById(R.id.tv_status)
        tvAppliedDate = findViewById(R.id.tv_applied_date)
        tvStatusMessage = findViewById(R.id.tv_status_message)
    }

    private fun setupClickListeners() {
        toolbar.setNavigationOnClickListener {
            finish()
        }

        btnTrack.setOnClickListener {
            val refNum = referenceInput.text.toString().trim().uppercase()
            if (refNum.isEmpty()) {
                referenceLayout.error = "Please enter reference number"
                return@setOnClickListener
            }
            referenceLayout.error = null
            trackApplication(refNum)
        }
    }

    private fun trackApplication(referenceNumber: String) {
        showLoading(true)

        firestore.collection("applications")
            .whereEqualTo("referenceNumber", referenceNumber)
            .get()
            .addOnSuccessListener { documents ->
                showLoading(false)

                if (documents.isEmpty) {
                    Toast.makeText(
                        this,
                        "Application not found. Please check your reference number.",
                        Toast.LENGTH_LONG
                    ).show()
                    statusCard.visibility = View.GONE
                } else {
                    val application = documents.documents[0]
                    displayApplicationStatus(application.data ?: emptyMap())
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("TrackApplication", "Error: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayApplicationStatus(data: Map<String, Any>) {
        statusCard.visibility = View.VISIBLE

        // Set applicant info
        tvApplicantName.text = data["fullName"] as? String ?: "N/A"
        tvApplicantEmail.text = data["email"] as? String ?: "N/A"
        tvAppliedCourse.text = "Applied for: ${data["appliedFor"] as? String ?: "N/A"}"

        // Set status
        val status = data["status"] as? String ?: "pending"
        updateStatusDisplay(status)

        // Set applied date
        val appliedDate = data["appliedDate"] as? Long ?: 0L
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        tvAppliedDate.text = dateFormat.format(Date(appliedDate))

        // Set status message
        tvStatusMessage.text = getStatusMessage(status, data)
    }

    private fun updateStatusDisplay(status: String) {
        when (status) {
            "pending" -> {
                tvStatus.text = "Pending Review"
                ivStatusIcon.setImageResource(R.drawable.ic_pending)
                tvStatus.setTextColor(getColor(R.color.status_pending))
            }
            "accepted" -> {
                tvStatus.text = "Accepted"
                ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                tvStatus.setTextColor(getColor(R.color.status_success))
            }
            "rejected" -> {
                tvStatus.text = "Rejected"
                ivStatusIcon.setImageResource(R.drawable.ic_cancel)
                tvStatus.setTextColor(getColor(R.color.status_error))
            }
            "enrolled" -> {
                tvStatus.text = "Enrolled"
                ivStatusIcon.setImageResource(R.drawable.ic_verified)
                tvStatus.setTextColor(getColor(R.color.status_success))
            }
            else -> {
                tvStatus.text = "Unknown"
                ivStatusIcon.setImageResource(R.drawable.ic_info)
            }
        }
    }

    private fun getStatusMessage(status: String, data: Map<String, Any>): String {
        return when (status) {
            "pending" -> "Your application is under review by the admissions committee. You will receive an email notification once it's reviewed."

            "accepted" -> {
                val accountCreated = data["accountCreated"] as? Boolean ?: false
                if (accountCreated) {
                    "Congratulations! Your application has been accepted. Please complete your enrollment profile to proceed."
                } else {
                    "Congratulations! Your application has been accepted. Check your email for instructions to set up your account."
                }
            }

            "rejected" -> {
                val reason = data["rejectionReason"] as? String
                if (reason.isNullOrEmpty()) {
                    "Unfortunately, your application was not accepted at this time."
                } else {
                    "Rejection Reason: $reason"
                }
            }

            "enrolled" -> "Your enrollment is complete! Welcome to Royal College. You can now login to your student dashboard."

            else -> "Status unknown. Please contact the admissions office."
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnTrack.isEnabled = !show
    }
}
