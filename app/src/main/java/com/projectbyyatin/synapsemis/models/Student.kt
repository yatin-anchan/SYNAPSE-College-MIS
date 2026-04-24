package com.projectbyyatin.synapsemis.models
import com.google.firebase.firestore.PropertyName


data class Student(
    var id: String = "",
    val studentId: String = "",           // Unique student ID (e.g., "STU2024001")
    @get:PropertyName("rollNumber")     // ← Line 1
    @set:PropertyName("rollNumber")     // ← Line 2 (separate lines!)
    var rollNumber: String = "",
    // Roll number in class
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val parentPhoneNumber: String = "",   // This is the correct field name
    val dateOfBirth: Long = 0L,
    val gender: String = "",              // Male/Female/Other
    val bloodGroup: String = "",

    // Academic Information
    val courseId: String = "",
    val courseName: String = "",
    val departmentId: String = "",
    val departmentName: String = "",
    val currentSemester: Int = 1,
    val classId: String = "",             // Current class/division
    val className: String = "",
    var status: String = "pending",
    val academicYear: String = "",        // e.g., "2024-2025"
    val admissionDate: Long = 0L,
    val admissionYear: Int = 0,           // Year of admission
    val batch: String = "",               // ADD THIS - e.g., "2024-2027"

    // Address Information
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",

    // Parent/Guardian Information - ADD THESE
    val parentName: String = "",          // ADD THIS
    val parentPhone: String = "",         // ADD THIS (alias for parentPhoneNumber)

    // Account Information
    val userId: String = "",              // Firebase Auth UID
    val role: String = "student",
    @PropertyName("isActive")
    var isActive: Boolean = true,
    val profileImageUrl: String = "",
    val profileCompleted: Boolean = false, // ADD THIS

    // Performance Metrics
    val totalAttendancePercentage: Double = 0.0,
    val cgpa: Double = 0.0,
    val sgpa: Double = 0.0,               // ADD THIS - Current semester SGPA
    val sgpaList: List<Double> = emptyList(), // SGPA for each semester
    val backlogs: Int = 0,                // ADD THIS

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    //Attendance
    var attendanceStatus: String = "present",  // "present", "absent"
    var remarks: String = "",

)
