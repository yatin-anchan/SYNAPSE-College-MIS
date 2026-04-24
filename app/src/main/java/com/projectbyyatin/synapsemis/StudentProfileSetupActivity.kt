package com.projectbyyatin.synapsemis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Student
import com.projectbyyatin.synapsemis.utils.ImageUploadHelper
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class StudentProfileSetupActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var profileImage: CircleImageView
    private lateinit var uploadPhotoButton: Button
    private lateinit var skipButton: Button
    private lateinit var completeButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var welcomeText: TextView

    private var selectedImageUri: Uri? = null
    private var existingPhotoUrl: String = ""
    private var userId: String = ""

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedImageUri = result.data?.data
            profileImage.setImageURI(selectedImageUri)
            completeButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile_setup)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        userId = intent.getStringExtra("USER_ID") ?: firebaseAuth.currentUser?.uid ?: ""

        initializeViews()
        loadStudentData()
        setupClickListeners()
    }

    private fun initializeViews() {
        profileImage = findViewById(R.id.profile_image)
        uploadPhotoButton = findViewById(R.id.upload_photo_button)
        skipButton = findViewById(R.id.skip_button)
        completeButton = findViewById(R.id.complete_button)
        progressBar = findViewById(R.id.progress_bar)
        welcomeText = findViewById(R.id.welcome_text)
    }

    private fun loadStudentData() {
        progressBar.visibility = View.VISIBLE

        firestore.collection("students")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE

                if (document.exists()) {
                    val student = document.toObject(Student::class.java)
                    if (student != null) {
                        welcomeText.text = "Welcome, ${student.fullName}!"

                        existingPhotoUrl = student.profileImageUrl

                        if (existingPhotoUrl.isNotEmpty()) {
                            // Photo already exists from application
                            Picasso.get()
                                .load(existingPhotoUrl)
                                .placeholder(R.drawable.ic_person)
                                .into(profileImage)

                            uploadPhotoButton.text = "Change Photo"
                            skipButton.visibility = View.VISIBLE
                            completeButton.isEnabled = true
                        } else {
                            // No photo - must upload
                            uploadPhotoButton.text = "Upload Photo"
                            skipButton.visibility = View.GONE
                            completeButton.isEnabled = false
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading profile: ${e.message}",
                    Toast.LENGTH_SHORT).show()
                Log.e("ProfileSetup", "Error loading student data", e)
            }
    }

    private fun setupClickListeners() {
        uploadPhotoButton.setOnClickListener {
            openImagePicker()
        }

        skipButton.setOnClickListener {
            // Use existing photo from application
            completeProfileSetup(existingPhotoUrl)
        }

        completeButton.setOnClickListener {
            if (selectedImageUri != null) {
                uploadImageAndComplete()
            } else if (existingPhotoUrl.isNotEmpty()) {
                completeProfileSetup(existingPhotoUrl)
            } else {
                Toast.makeText(this, "Please upload a profile photo",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun uploadImageAndComplete() {
        if (selectedImageUri == null) return

        progressBar.visibility = View.VISIBLE
        uploadPhotoButton.isEnabled = false
        completeButton.isEnabled = false

        ImageUploadHelper.uploadImage(
            context = this,
            imageUri = selectedImageUri!!,
            onSuccess = { imageUrl ->
                completeProfileSetup(imageUrl)
            },
            onFailure = { error ->
                progressBar.visibility = View.GONE
                uploadPhotoButton.isEnabled = true
                completeButton.isEnabled = true
                Toast.makeText(this, "Upload failed: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun completeProfileSetup(photoUrl: String) {
        progressBar.visibility = View.VISIBLE

        val updates = hashMapOf<String, Any>(
            "profileImageUrl" to photoUrl,
            "updatedAt" to System.currentTimeMillis()
            // DO NOT set profileCompleted or isActive here yet
        )

        firestore.collection("students")
            .document(userId)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Photo uploaded successfully!", Toast.LENGTH_SHORT).show()

                // Navigate to additional details form
                val intent = Intent(this, StudentAdditionalDetailsActivity::class.java)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
