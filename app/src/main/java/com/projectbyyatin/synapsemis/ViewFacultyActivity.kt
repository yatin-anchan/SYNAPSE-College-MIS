package com.projectbyyatin.synapsemis

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Faculty
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class ViewFacultyActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var profileImageCard: MaterialCardView
    private lateinit var profileImage: CircleImageView
    private lateinit var profileStatusBadge: ImageView
    private lateinit var facultyName: TextView
    private lateinit var facultyDesignation: TextView
    private lateinit var facultyEmployeeId: TextView
    private lateinit var profileStatusIcon: ImageView
    private lateinit var profileStatusText: TextView
    private lateinit var accessStatusIcon: ImageView
    private lateinit var accessStatusText: TextView
    private lateinit var btnCall: MaterialButton
    private lateinit var btnEmail: MaterialButton
    private lateinit var facultyEmail: TextView
    private lateinit var facultyPhone: TextView
    private lateinit var facultyDepartment: TextView
    private lateinit var facultyQualifications: TextView
    private lateinit var facultyExperience: TextView
    private lateinit var subjectsChipGroup: ChipGroup
    private lateinit var noSubjectsText: TextView
    private lateinit var fabEdit: ExtendedFloatingActionButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private var facultyId: String = ""
    private var faculty: Faculty? = null
    private var currentPhotoUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_faculty)

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""

        if (facultyId.isEmpty()) {
            Toast.makeText(this, "Error: Faculty ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupBackButton()
        setupClickListeners()
        loadFacultyData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        profileImageCard = findViewById(R.id.profile_image_card)
        profileImage = findViewById(R.id.profile_image)
        profileStatusBadge = findViewById(R.id.profile_status_badge)
        facultyName = findViewById(R.id.faculty_name)
        facultyDesignation = findViewById(R.id.faculty_designation)
        facultyEmployeeId = findViewById(R.id.faculty_employee_id)
        profileStatusIcon = findViewById(R.id.profile_status_icon)
        profileStatusText = findViewById(R.id.profile_status_text)
        accessStatusIcon = findViewById(R.id.access_status_icon)
        accessStatusText = findViewById(R.id.access_status_text)
        btnCall = findViewById(R.id.btn_call)
        btnEmail = findViewById(R.id.btn_email)
        facultyEmail = findViewById(R.id.faculty_email)
        facultyPhone = findViewById(R.id.faculty_phone)
        facultyDepartment = findViewById(R.id.faculty_department)
        facultyQualifications = findViewById(R.id.faculty_qualifications)
        facultyExperience = findViewById(R.id.faculty_experience)
        subjectsChipGroup = findViewById(R.id.subjects_chip_group)
        noSubjectsText = findViewById(R.id.no_subjects_text)
        fabEdit = findViewById(R.id.fab_edit)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun setupClickListeners() {
        // Profile Image Click - Show Full Screen
        profileImageCard.setOnClickListener {
            if (currentPhotoUrl.isNotEmpty()) {
                showFullScreenImage(currentPhotoUrl)
            } else {
                Toast.makeText(this, "No profile image available", Toast.LENGTH_SHORT).show()
            }
        }

        fabEdit.setOnClickListener {
            val intent = Intent(this, EditFacultyActivity::class.java)
            intent.putExtra("FACULTY_ID", facultyId)
            startActivity(intent)
        }

        btnCall.setOnClickListener {
            faculty?.phone?.let { phone ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            }
        }

        btnEmail.setOnClickListener {
            faculty?.email?.let { email ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$email")
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFullScreenImage(imageUrl: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_full_image)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val fullImage = dialog.findViewById<ImageView>(R.id.full_image)
        val btnClose = dialog.findViewById<ImageView>(R.id.btn_close)
        val facultyNameImg = dialog.findViewById<TextView>(R.id.faculty_name_img)

        // Set faculty name
        facultyNameImg.text = faculty?.name ?: "Faculty Name"

        // Load full image
        Picasso.get()
            .load(imageUrl)
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
            .into(fullImage)

        // Close button click
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Tap anywhere to close
        dialog.findViewById<View>(android.R.id.content).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun loadFacultyData() {
        showLoading(true)

        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                faculty = document.toObject(Faculty::class.java)
                faculty?.let {
                    populateUI(it)
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun populateUI(faculty: Faculty) {
        // Header
        facultyName.text = faculty.name
        facultyDesignation.text = faculty.designation.ifEmpty { "Faculty Member" }
        facultyEmployeeId.text = faculty.employeeId

        // Load profile image
        currentPhotoUrl = faculty.photoUrl
        if (currentPhotoUrl.isNotEmpty()) {
            Picasso.get()
                .load(currentPhotoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(profileImage)
        }

        // Status
        if (faculty.profileCompleted) {
            profileStatusIcon.setImageResource(R.drawable.ic_check_circle)
            profileStatusText.text = "Completed"
            profileStatusBadge.setImageResource(R.drawable.ic_check_circle)
            profileStatusText.setTextColor(getColor(R.color.splash_accent))
        } else {
            profileStatusIcon.setImageResource(R.drawable.ic_pending)
            profileStatusText.text = "Incomplete"
            profileStatusBadge.setImageResource(R.drawable.ic_pending)
            profileStatusText.setTextColor(getColor(android.R.color.holo_orange_light))
        }

        if (faculty.appAccessEnabled) {
            accessStatusIcon.setImageResource(R.drawable.ic_lock_open)
            accessStatusText.text = "Enabled"
            accessStatusText.setTextColor(getColor(R.color.splash_accent))
        } else {
            accessStatusIcon.setImageResource(R.drawable.ic_lock)
            accessStatusText.text = "Disabled"
            accessStatusText.setTextColor(getColor(android.R.color.holo_red_light))
        }

        // Contact Info
        facultyEmail.text = faculty.email
        facultyPhone.text = faculty.phone

        // Professional Details
        facultyDepartment.text = faculty.department.ifEmpty { "Not specified" }
        facultyQualifications.text = faculty.qualifications.ifEmpty { "Not specified" }
        facultyExperience.text = faculty.experience.ifEmpty { "Not specified" }

        // Subjects
        if (faculty.subjects.isEmpty()) {
            noSubjectsText.visibility = View.VISIBLE
            subjectsChipGroup.visibility = View.GONE
        } else {
            noSubjectsText.visibility = View.GONE
            subjectsChipGroup.visibility = View.VISIBLE
            subjectsChipGroup.removeAllViews()

            faculty.subjects.forEach { subject ->
                val chip = Chip(this).apply {
                    text = subject
                    isClickable = false
                    setChipBackgroundColorResource(R.color.splash_accent)
                    setTextColor(getColor(android.R.color.white))
                }
                subjectsChipGroup.addView(chip)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (facultyId.isNotEmpty()) {
            loadFacultyData()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }
}
