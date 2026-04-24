package com.projectbyyatin.synapsemis

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Application
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class ApplicationDetailsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressBar: ProgressBar

    // Image views
    private lateinit var profileImageView: ImageView
    private lateinit var tenthMarksheetImageView: ImageView
    private lateinit var twelfthMarksheetImageView: ImageView

    // Image cards
    private lateinit var profileImageCard: MaterialCardView
    private lateinit var tenthImageCard: MaterialCardView
    private lateinit var twelfthImageCard: MaterialCardView

    // Review card
    private lateinit var reviewCard: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_application_details)

        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupToolbar()
        loadApplicationDetails()
    }

    private fun initializeViews() {
        progressBar = findViewById(R.id.progress_bar)

        // Image views
        profileImageView = findViewById(R.id.profile_image)
        tenthMarksheetImageView = findViewById(R.id.tenth_marksheet_image)
        twelfthMarksheetImageView = findViewById(R.id.twelfth_marksheet_image)

        // Image cards
        profileImageCard = findViewById(R.id.profile_image_card)
        tenthImageCard = findViewById(R.id.tenth_image_card)
        twelfthImageCard = findViewById(R.id.twelfth_image_card)

        // Review card
        reviewCard = findViewById(R.id.review_card)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Application Details"
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadApplicationDetails() {
        val applicationId = intent.getStringExtra("APPLICATION_ID")
        if (applicationId == null) {
            Toast.makeText(this, "Error: No application ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE

        firestore.collection("applications")
            .document(applicationId)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE

                if (document.exists()) {
                    val application = document.toObject(Application::class.java)
                    if (application != null) {
                        displayApplicationDetails(application)
                        loadImages(application)
                    }
                } else {
                    Toast.makeText(this, "Application not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ApplicationDetails", "Error loading application", e)
                finish()
            }
    }

    private fun formatDate(timestamp: Long?): String {
        return if (timestamp != null && timestamp > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        } else {
            "N/A"
        }
    }

    private fun displayApplicationDetails(application: Application) {
        // Status views
        findViewById<TextView>(R.id.tv_status).text = application.status.uppercase()
        findViewById<TextView>(R.id.tv_reference_badge).text = application.referenceNumber

        // Set status icon based on status
        val statusIcon = findViewById<ImageView>(R.id.iv_status_icon)
        when (application.status) {
            "pending" -> statusIcon.setImageResource(R.drawable.ic_pending)
            "accepted" -> statusIcon.setImageResource(R.drawable.ic_check_circle)
            "rejected" -> statusIcon.setImageResource(R.drawable.ic_cancel)
            else -> statusIcon.setImageResource(R.drawable.ic_pending)
        }

        // Personal info
        findViewById<TextView>(R.id.tv_full_name).text = application.fullName
        findViewById<TextView>(R.id.tv_email).text = application.email
        findViewById<TextView>(R.id.tv_phone).text = application.phone
        findViewById<TextView>(R.id.tv_dob).text = formatDate(application.dateOfBirth)

        // Application details
        findViewById<TextView>(R.id.tv_course).text = application.courseName
        findViewById<TextView>(R.id.tv_batch).text = application.preferredBatch
        findViewById<TextView>(R.id.tv_applied_date).text = formatDate(application.appliedDate)

        // Review information (if reviewed)
        if (application.reviewedBy.isNotEmpty()) {
            reviewCard.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_reviewed_by).text = application.reviewedBy
            findViewById<TextView>(R.id.tv_reviewed_date).text = formatDate(application.reviewedDate)

            // Show rejection reason if rejected
            if (application.status == "rejected" && application.rejectionReason.isNotEmpty()) {
                findViewById<View>(R.id.rejection_reason_container).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tv_rejection_reason).text = application.rejectionReason
            }
        } else {
            reviewCard.visibility = View.GONE
        }
    }



    private fun loadImages(application: Application) {
        // Load profile image
        if (application.photoUrl.isNotEmpty()) {
            profileImageCard.visibility = View.VISIBLE
            Picasso.get()
                .load(application.photoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(profileImageView, object : Callback {
                    override fun onSuccess() {
                        profileImageView.setOnClickListener {
                            showFullScreenImage(application.photoUrl, "Profile Photo")
                        }
                    }

                    override fun onError(e: Exception?) {
                        Log.e("ApplicationDetails", "Error loading profile image", e)
                    }
                })
        } else {
            profileImageCard.visibility = View.GONE
        }

        // Load 10th marksheet
        if (application.tenthMarksheetUrl.isNotEmpty()) {
            tenthImageCard.visibility = View.VISIBLE
            Picasso.get()
                .load(application.tenthMarksheetUrl)
                .placeholder(R.drawable.ic_document)
                .error(R.drawable.ic_document)
                .into(tenthMarksheetImageView, object : Callback {
                    override fun onSuccess() {
                        tenthMarksheetImageView.setOnClickListener {
                            showFullScreenImage(application.tenthMarksheetUrl, "10th Marksheet")
                        }
                    }

                    override fun onError(e: Exception?) {
                        Log.e("ApplicationDetails", "Error loading 10th marksheet", e)
                    }
                })
        } else {
            tenthImageCard.visibility = View.GONE
        }

        // Load 12th marksheet
        if (application.twelfthMarksheetUrl.isNotEmpty()) {
            twelfthImageCard.visibility = View.VISIBLE
            Picasso.get()
                .load(application.twelfthMarksheetUrl)
                .placeholder(R.drawable.ic_document)
                .error(R.drawable.ic_document)
                .into(twelfthMarksheetImageView, object : Callback {
                    override fun onSuccess() {
                        twelfthMarksheetImageView.setOnClickListener {
                            showFullScreenImage(application.twelfthMarksheetUrl, "12th Marksheet")
                        }
                    }

                    override fun onError(e: Exception?) {
                        Log.e("ApplicationDetails", "Error loading 12th marksheet", e)
                    }
                })
        } else {
            twelfthImageCard.visibility = View.GONE
        }
    }

    private fun showFullScreenImage(imageUrl: String, title: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_fullscreen_image)

        val imageView = dialog.findViewById<ImageView>(R.id.fullscreen_image)
        val titleText = dialog.findViewById<TextView>(R.id.image_title)
        val closeButton = dialog.findViewById<ImageView>(R.id.close_button)
        val loadingBar = dialog.findViewById<ProgressBar>(R.id.loading_progress)

        titleText.text = title
        loadingBar.visibility = View.VISIBLE

        Picasso.get()
            .load(imageUrl)
            .into(imageView, object : Callback {
                override fun onSuccess() {
                    loadingBar.visibility = View.GONE
                }

                override fun onError(e: Exception?) {
                    loadingBar.visibility = View.GONE
                    Toast.makeText(this@ApplicationDetailsActivity,
                        "Error loading image", Toast.LENGTH_SHORT).show()
                }
            })

        closeButton.setOnClickListener { dialog.dismiss() }
        imageView.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
