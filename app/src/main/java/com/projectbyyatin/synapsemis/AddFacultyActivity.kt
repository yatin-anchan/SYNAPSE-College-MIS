package com.projectbyyatin.synapsemis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.projectbyyatin.synapsemis.models.Faculty
import java.util.UUID

class AddFacultyActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var employeeIdInput: TextInputEditText
    private lateinit var employeeIdLayout: TextInputLayout
    private lateinit var btnGenerateId: MaterialButton
    private lateinit var nameInput: TextInputEditText
    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var mobileInput: TextInputEditText
    private lateinit var mobileLayout: TextInputLayout
    private lateinit var departmentDropdown: AutoCompleteTextView
    private lateinit var departmentLayout: TextInputLayout
    private lateinit var roleDropdown: AutoCompleteTextView
    private lateinit var roleLayout: TextInputLayout
    private lateinit var btnCreateFaculty: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var departmentsList = mutableListOf<String>()
    private var departmentsMap = mutableMapOf<String, String>() // name -> id
    private var isEditMode = false
    private var facultyId: String? = null

    companion object {
        const val ROLE_FACULTY = "faculty"
        const val ROLE_HOD = "hod"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_faculty)

        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        facultyId = intent.getStringExtra("FACULTY_ID")

        initializeViews()
        setupToolbar()
        loadDepartments()
        setupRoleDropdown()
        setupGenerateId()
        setupCreateButton()

        if (isEditMode && facultyId != null) {
            loadFacultyData(facultyId!!)
        }
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        employeeIdInput = findViewById(R.id.employee_id_input)
        employeeIdLayout = findViewById(R.id.employee_id_layout)
        btnGenerateId = findViewById(R.id.btn_generate_id)
        nameInput = findViewById(R.id.name_input)
        nameLayout = findViewById(R.id.name_layout)
        emailInput = findViewById(R.id.email_input)
        emailLayout = findViewById(R.id.email_layout)
        mobileInput = findViewById(R.id.mobile_input)
        mobileLayout = findViewById(R.id.mobile_layout)
        departmentDropdown = findViewById(R.id.department_dropdown)
        departmentLayout = findViewById(R.id.department_layout)
        roleDropdown = findViewById(R.id.role_dropdown)
        roleLayout = findViewById(R.id.role_layout)
        btnCreateFaculty = findViewById(R.id.btn_create_faculty)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isEditMode) "Edit Faculty" else "Add Faculty"

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadDepartments() {
        departmentsList.add("None")
        departmentsMap["None"] = ""

        firestore.collection("departments")
            .get()
            .addOnSuccessListener { documents ->
                documents.forEach { document ->
                    val deptName = document.getString("name")
                    val deptId = document.id
                    if (deptName != null) {
                        departmentsList.add(deptName)
                        departmentsMap[deptName] = deptId
                    }
                }

                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    departmentsList
                )
                departmentDropdown.setAdapter(adapter)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading departments: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRoleDropdown() {
        val roles = listOf(ROLE_FACULTY, ROLE_HOD)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            roles
        )
        roleDropdown.setAdapter(adapter)
        roleDropdown.setText(ROLE_FACULTY, false) // Default to faculty
    }

    private fun setupGenerateId() {
        btnGenerateId.setOnClickListener {
            generateEmployeeId()
        }
    }

    private fun generateEmployeeId() {
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val randomPart = UUID.randomUUID().toString().take(4).uppercase()
        val employeeId = "FAC$timestamp$randomPart"

        firestore.collection("faculty")
            .whereEqualTo("employeeId", employeeId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    employeeIdInput.setText(employeeId)
                    Toast.makeText(this, "✅ Employee ID generated", Toast.LENGTH_SHORT).show()
                } else {
                    // Extremely rare collision, regenerate
                    generateEmployeeId()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error generating ID: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupCreateButton() {
        btnCreateFaculty.setOnClickListener {
            if (validateInputs()) {
                if (isEditMode && facultyId != null) {
                    updateFaculty()
                } else {
                    createFaculty()
                }
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Clear previous errors
        emailLayout.error = null
        mobileLayout.error = null
        nameLayout.error = null
        employeeIdLayout.error = null
        departmentLayout.error = null

        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val mobile = mobileInput.text.toString().trim()
        val employeeId = employeeIdInput.text.toString().trim()
        val department = departmentDropdown.text.toString()

        if (name.isEmpty()) {
            nameLayout.error = "Name is required"
            isValid = false
        }

        if (employeeId.isEmpty()) {
            employeeIdLayout.error = "Employee ID is required"
            isValid = false
        }

        if (email.isEmpty()) {
            emailLayout.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Invalid email format"
            isValid = false
        }

        if (mobile.isEmpty()) {
            mobileLayout.error = "Mobile number is required"
            isValid = false
        } else if (mobile.length != 10) {
            mobileLayout.error = "Mobile number must be 10 digits"
            isValid = false
        }

        if (department.isEmpty() || department == "None") {
            departmentLayout.error = "Department is required"
            isValid = false
        }

        return isValid
    }

    private fun createFaculty() {
        showLoading(true)

        val employeeId = employeeIdInput.text.toString().trim()
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val mobile = mobileInput.text.toString().trim()
        val departmentName = departmentDropdown.text.toString()
        val departmentId = departmentsMap[departmentName] ?: ""
        val role = roleDropdown.text.toString().lowercase().ifEmpty { ROLE_FACULTY }

        val faculty = Faculty(
            id = "",
            employeeId = employeeId,
            name = name,
            email = email,
            phone = mobile,
            department = departmentName,
            departmentId = departmentId,
            role = role,  // Only "faculty" or "hod"
            designation = "",  // Not used anymore
            appAccessEnabled = false,
            profileCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        // Check if department already has HOD if trying to assign HOD role
        if (role == ROLE_HOD) {
            checkAndAssignHOD(faculty, departmentId)
        } else {
            saveFacultyToFirestore(faculty)
        }
    }

    private fun checkAndAssignHOD(faculty: Faculty, departmentId: String) {
        firestore.collection("departments").document(departmentId)
            .get()
            .addOnSuccessListener { deptDoc ->
                val currentHODId = deptDoc.getString("hodId")

                if (!currentHODId.isNullOrEmpty()) {
                    // Department already has HOD
                    AlertDialog.Builder(this, R.style.CustomAlertDialog)
                        .setTitle("⚠️ HOD Already Exists")
                        .setMessage("This department already has an HOD. Do you want to replace them?\n\nCurrent HOD will be changed to regular faculty.")
                        .setPositiveButton("Replace HOD") { _, _ ->
                            saveFacultyAsHOD(faculty, currentHODId)
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            showLoading(false)
                        }
                        .show()
                } else {
                    // No HOD exists, proceed
                    saveFacultyAsHOD(faculty, null)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error checking department: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFacultyAsHOD(faculty: Faculty, oldHODId: String?) {
        val batch: WriteBatch = firestore.batch()

        // 1. Add new faculty document
        val newFacultyRef = firestore.collection("faculty").document()
        batch.set(newFacultyRef, faculty.copy(id = newFacultyRef.id))

        // 2. Update department with new HOD
        val deptRef = firestore.collection("departments").document(faculty.departmentId)
        batch.update(deptRef, mapOf(
            "hod" to faculty.name,
            "hodId" to newFacultyRef.id,
            "hodEmail" to faculty.email,
            "hodPhone" to faculty.phone
        ))

        // 3. If replacing old HOD, demote them
        if (oldHODId != null) {
            val oldHODRef = firestore.collection("faculty").document(oldHODId)
            batch.update(oldHODRef, "role", ROLE_FACULTY)

            // Also update in users collection if exists
            val oldHODUserRef = firestore.collection("users").document(oldHODId)
            batch.set(oldHODUserRef, mapOf("role" to ROLE_FACULTY), com.google.firebase.firestore.SetOptions.merge())
        }

        batch.commit()
            .addOnSuccessListener {
                sendInvitationEmail(faculty.email, faculty.name, newFacultyRef.id, faculty.employeeId)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveFacultyToFirestore(faculty: Faculty) {
        firestore.collection("faculty")
            .add(faculty)
            .addOnSuccessListener { documentReference ->
                val newFacultyId = documentReference.id

                // Update faculty document with its own ID
                documentReference.update("id", newFacultyId)
                    .addOnSuccessListener {
                        sendInvitationEmail(faculty.email, faculty.name, newFacultyId, faculty.employeeId)
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        Toast.makeText(this, "Error updating faculty ID: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error creating faculty: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateFaculty() {
        // Implementation for edit mode
        showLoading(true)

        val updatedData = mapOf(
            "name" to nameInput.text.toString().trim(),
            "email" to emailInput.text.toString().trim(),
            "phone" to mobileInput.text.toString().trim(),
            "employeeId" to employeeIdInput.text.toString().trim(),
            "department" to departmentDropdown.text.toString(),
            ("departmentId" to departmentsMap[departmentDropdown.text.toString()] ?: "") as Pair<String, String>,
            "role" to roleDropdown.text.toString().lowercase()
        )

        firestore.collection("faculty").document(facultyId!!)
            .update(updatedData)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "✅ Faculty updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendInvitationEmail(email: String, name: String, facultyId: String, employeeId: String) {
        val invitationCode = (100000..999999).random().toString()

        val invitation = hashMapOf(
            "facultyId" to facultyId,
            "email" to email,
            "name" to name,
            "invitationCode" to invitationCode,
            "status" to "pending",
            "createdAt" to System.currentTimeMillis(),
            "expiresAt" to System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
        )

        firestore.collection("faculty_invitations")
            .document(facultyId)
            .set(invitation)
            .addOnSuccessListener {
                showLoading(false)
                showInvitationCodeDialog(name, email, invitationCode, facultyId, employeeId)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showInvitationCodeDialog(name: String, email: String, code: String, facultyId: String, employeeId: String) {
        val message = """
        ✅ Faculty Added Successfully!
        
        Name: $name
        Email: $email
        Employee ID: $employeeId
        Faculty ID: $facultyId
        
        📧 Invitation Code: $code
        
        Share this information with the faculty member to complete their profile setup.
        
        ⏰ Code expires in 7 days.
        💡 Employee ID can be used if code is forgotten.
    """.trimIndent()

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Faculty Created")
            .setMessage(message)
            .setPositiveButton("Copy Details") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipText = """
                    Email: $email
                    Employee ID: $employeeId
                    Invitation Code: $code
                """.trimIndent()
                val clip = android.content.ClipData.newPlainText("Faculty Invitation", clipText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 Copied to clipboard!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNeutralButton("Share via WhatsApp") { _, _ ->
                shareViaWhatsApp(name, email, code, facultyId, employeeId)
                finish()
            }
            .setNegativeButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun shareViaWhatsApp(name: String, email: String, code: String, facultyId: String, employeeId: String) {
        val message = """
        🎓 SYNAPSE MIS - Faculty Invitation
        
        Dear $name,
        
        You have been added to SYNAPSE Management Information System.
        
        📧 Email: $email
        🆔 Employee ID: $employeeId
        🔑 Invitation Code: $code
        
        For first-time login, use:
        ✅ Email + Invitation Code (expires in 7 days)
        OR
        ✅ Email + Employee ID (permanent - use if you forget the code)
        
        Please download SYNAPSE MIS app and complete your profile setup.
        
        For support, contact administration.
    """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            startActivity(Intent.createChooser(shareIntent, "Share invitation via"))
        }
    }

    private fun loadFacultyData(facultyId: String) {
        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                val faculty = document.toObject(Faculty::class.java)
                faculty?.let {
                    employeeIdInput.setText(it.employeeId)
                    nameInput.setText(it.name)
                    emailInput.setText(it.email)
                    mobileInput.setText(it.phone)
                    departmentDropdown.setText(it.department.ifEmpty { "None" }, false)
                    roleDropdown.setText(it.role.ifEmpty { ROLE_FACULTY }, false)

                    btnCreateFaculty.text = "Update Faculty"
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading faculty: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnCreateFaculty.isEnabled = !show
        btnGenerateId.isEnabled = !show
        employeeIdInput.isEnabled = !show
        nameInput.isEnabled = !show
        emailInput.isEnabled = !show
        mobileInput.isEnabled = !show
        departmentDropdown.isEnabled = !show
        roleDropdown.isEnabled = !show
    }
}