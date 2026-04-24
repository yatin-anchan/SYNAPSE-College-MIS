package com.projectbyyatin.synapsemis.models

data class Application(
    val applicationId: String = "",
    val referenceNumber: String = "",

    // Personal Info
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val dateOfBirth: Long = 0L,

    // Academic Info - NEW
    val tenthPercentage: Double = 0.0,
    val tenthBoard: String = "",
    val tenthYear: Int = 0,
    val twelfthPercentage: Double = 0.0,
    val twelfthBoard: String = "",
    val twelfthYear: Int = 0,
    val stream: String = "", // Science, Commerce, Arts

    // Application Details
    val appliedFor: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val preferredBatch: String = "",

    // Documents - NEW
    val photoUrl: String = "",
    val tenthMarksheetUrl: String = "",
    val twelfthMarksheetUrl: String = "",

    // Status Tracking
    val status: String = "pending", // pending, accepted, rejected, enrolled
    val appliedDate: Long = System.currentTimeMillis(),
    val reviewedDate: Long? = null,
    val reviewedBy: String = "",
    val reviewedByName: String = "",
    val rejectionReason: String = "",

    // Next Phase Flags
    val accountCreated: Boolean = false,
    val profileCompleted: Boolean = false,
    val finallyApproved: Boolean = false
)

data class EnrollmentApplication(
    val applicationId: String = "",
    val referenceNumber: String = "",
    val userId: String = "",

    // Personal Info (Complete)
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val parentPhone: String = "",
    val dateOfBirth: Long = 0L,
    val gender: String = "",
    val bloodGroup: String = "",

    // Address
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",

    // Parent/Guardian Info
    val parentName: String = "",
    val parentOccupation: String = "",
    val parentEmail: String = "",

    // Academic History
    val previousSchool: String = "",
    val previousPercentage: Double = 0.0,
    val previousBoard: String = "",
    val previousYear: Int = 0,

    // Documents
    val photoUrl: String = "",
    val marksheetUrl: String = "",
    val idProofUrl: String = "",
    val birthCertificateUrl: String = "",
    val casteCertificateUrl: String = "",

    // Application Reference
    val appliedFor: String = "",
    val courseId: String = "",
    val courseName: String = "",

    // Approval Status
    val status: String = "profile_submitted",
    val submittedDate: Long = System.currentTimeMillis(),
    val approvedDate: Long? = null,
    val approvedBy: String = "",
    val approvedByName: String = "",
    val rejectionReason: String = "",

    // Assignment (after approval)
    val assignedDepartmentId: String = "",
    val assignedDepartmentName: String = "",
    val assignedClassId: String = "",
    val assignedClassName: String = "",
    val assignedSemester: Int = 1,
    val studentId: String = "",
    val rollNumber: String = ""
)
