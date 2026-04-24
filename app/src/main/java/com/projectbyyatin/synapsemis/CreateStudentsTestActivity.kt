package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Student

class CreateStudentsTestActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var btnCreateStudents: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    private var studentsCreated = 0
    private var totalStudents = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_students_test)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        btnCreateStudents = findViewById(R.id.btn_create_students)
        progressBar = findViewById(R.id.progress_bar)
        tvStatus = findViewById(R.id.tv_status)

        btnCreateStudents.setOnClickListener {
            createMultipleStudents()
        }
    }

    private fun createMultipleStudents() {
        btnCreateStudents.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        studentsCreated = 0

        val students = getStudentData()
        totalStudents = students.size

        tvStatus.text = "Creating 0/$totalStudents students..."

        students.forEach { studentData ->
            createStudent(studentData)
        }
    }

    private fun getStudentData(): List<StudentData> {
        return listOf(
            StudentData(
                email = "yatin.student@synapse.edu",
                password = "Yatin@123",
                studentId = "STU2024001",
                rollNumber = "TY01",
                firstName = "Yatin",
                lastName = "Anchan",
                fullName = "Yatin Anchan",
                phoneNumber = "9876543210",
                gender = "Male",
                bloodGroup = "O+",
                currentSemester = 3,
                className = "TY A",
                cgpa = 8.5,
                attendance = 87.5,
                sgpaList = listOf(8.2, 8.6, 8.7)
            ),
            StudentData(
                email = "priya.sharma@synapse.edu",
                password = "Priya@123",
                studentId = "STU2024002",
                rollNumber = "TY02",
                firstName = "Priya",
                lastName = "Sharma",
                fullName = "Priya Sharma",
                phoneNumber = "9876543220",
                gender = "Female",
                bloodGroup = "A+",
                currentSemester = 3,
                className = "TY A",
                cgpa = 9.1,
                attendance = 92.0,
                sgpaList = listOf(9.0, 9.1, 9.2)
            ),
            StudentData(
                email = "rahul.verma@synapse.edu",
                password = "Rahul@123",
                studentId = "STU2024003",
                rollNumber = "TY03",
                firstName = "Rahul",
                lastName = "Verma",
                fullName = "Rahul Verma",
                phoneNumber = "9876543230",
                gender = "Male",
                bloodGroup = "B+",
                currentSemester = 3,
                className = "TY B",
                cgpa = 7.8,
                attendance = 82.5,
                sgpaList = listOf(7.5, 7.9, 8.0)
            ),
            StudentData(
                email = "ananya.patel@synapse.edu",
                password = "Ananya@123",
                studentId = "STU2024004",
                rollNumber = "TY04",
                firstName = "Ananya",
                lastName = "Patel",
                fullName = "Ananya Patel",
                phoneNumber = "9876543240",
                gender = "Female",
                bloodGroup = "O+",
                currentSemester = 3,
                className = "TY A",
                cgpa = 8.8,
                attendance = 89.0,
                sgpaList = listOf(8.5, 8.9, 9.0)
            ),
            StudentData(
                email = "arjun.singh@synapse.edu",
                password = "Arjun@123",
                studentId = "STU2024005",
                rollNumber = "TY05",
                firstName = "Arjun",
                lastName = "Singh",
                fullName = "Arjun Singh",
                phoneNumber = "9876543250",
                gender = "Male",
                bloodGroup = "AB+",
                currentSemester = 3,
                className = "TY B",
                cgpa = 8.2,
                attendance = 85.5,
                sgpaList = listOf(8.0, 8.2, 8.4)
            ),
            StudentData(
                email = "sneha.reddy@synapse.edu",
                password = "Sneha@123",
                studentId = "STU2024006",
                rollNumber = "SY01",
                firstName = "Sneha",
                lastName = "Reddy",
                fullName = "Sneha Reddy",
                phoneNumber = "9876543260",
                gender = "Female",
                bloodGroup = "A-",
                currentSemester = 2,
                className = "SY A",
                cgpa = 9.0,
                attendance = 91.0,
                sgpaList = listOf(8.9, 9.1)
            ),
            StudentData(
                email = "aditya.kumar@synapse.edu",
                password = "Aditya@123",
                studentId = "STU2024007",
                rollNumber = "SY02",
                firstName = "Aditya",
                lastName = "Kumar",
                fullName = "Aditya Kumar",
                phoneNumber = "9876543270",
                gender = "Male",
                bloodGroup = "B-",
                currentSemester = 2,
                className = "SY B",
                cgpa = 7.5,
                attendance = 78.0,
                sgpaList = listOf(7.3, 7.7)
            ),
            StudentData(
                email = "ishita.joshi@synapse.edu",
                password = "Ishita@123",
                studentId = "STU2024008",
                rollNumber = "FY01",
                firstName = "Ishita",
                lastName = "Joshi",
                fullName = "Ishita Joshi",
                phoneNumber = "9876543280",
                gender = "Female",
                bloodGroup = "O-",
                currentSemester = 1,
                className = "FY A",
                cgpa = 8.7,
                attendance = 88.0,
                sgpaList = listOf(8.7)
            ),
            StudentData(
                email = "rohan.mehta@synapse.edu",
                password = "Rohan@123",
                studentId = "STU2024009",
                rollNumber = "FY02",
                firstName = "Rohan",
                lastName = "Mehta",
                fullName = "Rohan Mehta",
                phoneNumber = "9876543290",
                gender = "Male",
                bloodGroup = "AB-",
                currentSemester = 1,
                className = "FY B",
                cgpa = 8.0,
                attendance = 84.0,
                sgpaList = listOf(8.0)
            ),
            StudentData(
                email = "kavya.nair@synapse.edu",
                password = "Kavya@123",
                studentId = "STU2024010",
                rollNumber = "TY06",
                firstName = "Kavya",
                lastName = "Nair",
                fullName = "Kavya Nair",
                phoneNumber = "9876543300",
                gender = "Female",
                bloodGroup = "A+",
                currentSemester = 3,
                className = "TY A",
                cgpa = 9.2,
                attendance = 94.0,
                sgpaList = listOf(9.0, 9.2, 9.4)
            )
        )
    }

    private fun createStudent(studentData: StudentData) {
        // First create Firebase Auth account
        auth.createUserWithEmailAndPassword(studentData.email, studentData.password)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: ""
                Log.d("CreateStudents", "Auth created for ${studentData.email}")

                // Create student document in Firestore
                val student = Student(
                    studentId = studentData.studentId,
                    rollNumber = studentData.rollNumber,
                    firstName = studentData.firstName,
                    lastName = studentData.lastName,
                    fullName = studentData.fullName,
                    email = studentData.email,
                    phoneNumber = studentData.phoneNumber,
                    parentPhoneNumber = "98765432${(10..99).random()}",
                    dateOfBirth = 1041379200000L, // Jan 1, 2003
                    gender = studentData.gender,
                    bloodGroup = studentData.bloodGroup,

                    // Academic Info
                    courseId = "T1VbhsdbGwkEac6JvZ5J",
                    courseName = "BSc Computer Science",
                    departmentId = "lKXWOLQN4mnFjhanI23N",
                    departmentName = "Computer Science",
                    currentSemester = studentData.currentSemester,
                    classId = "class123",
                    className = studentData.className,
                    academicYear = "2024-2025",
                    admissionDate = 1659312000000L,
                    admissionYear = 2022,

                    // Address
                    address = "Mumbai, Maharashtra",
                    city = "Mumbai",
                    state = "Maharashtra",
                    pincode = "400001",

                    // Account Info
                    userId = userId,
                    role = "student",
                    isActive = true,
                    profileImageUrl = "",

                    // Performance
                    totalAttendancePercentage = studentData.attendance,
                    cgpa = studentData.cgpa,
                    sgpaList = studentData.sgpaList,

                    // Timestamps
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // Add to students collection
                firestore.collection("students")
                    .add(student)
                    .addOnSuccessListener { docRef ->
                        Log.d("CreateStudents", "Student doc created: ${docRef.id}")

                        // Also create user document for role-based login
                        createUserDocument(userId, studentData.email, studentData.fullName)
                    }
                    .addOnFailureListener { e ->
                        Log.e("CreateStudents", "Error creating student doc: ${e.message}")
                        updateProgress()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CreateStudents", "Auth error for ${studentData.email}: ${e.message}")
                updateProgress()
            }
    }

    private fun createUserDocument(userId: String, email: String, fullName: String) {
        val userDoc = hashMapOf(
            "userId" to userId,
            "email" to email,
            "role" to "student",
            "fullName" to fullName,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .set(userDoc)
            .addOnSuccessListener {
                Log.d("CreateStudents", "User doc created for $email")
                updateProgress()
            }
            .addOnFailureListener { e ->
                Log.e("CreateStudents", "Error creating user doc: ${e.message}")
                updateProgress()
            }
    }

    private fun updateProgress() {
        studentsCreated++
        runOnUiThread {
            tvStatus.text = "Created $studentsCreated/$totalStudents students..."

            if (studentsCreated >= totalStudents) {
                progressBar.visibility = android.view.View.GONE
                btnCreateStudents.isEnabled = true
                tvStatus.text = "✅ Successfully created $totalStudents students!"
                Toast.makeText(this, "All students created!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Data class for student info
    data class StudentData(
        val email: String,
        val password: String,
        val studentId: String,
        val rollNumber: String,
        val firstName: String,
        var lastName: String,
        val fullName: String,
        val phoneNumber: String,
        val gender: String,
        val bloodGroup: String,
        val currentSemester: Int,
        val className: String,
        val cgpa: Double,
        val attendance: Double,
        val sgpaList: List<Double>
    )
}
