package com.projectbyyatin.synapsemis.models

data class StudentAttendanceRecord(
    val studentId: String = "",
    val studentName: String = "",
    val rollNumber: String = "",
    val status: String = "absent", // present, absent, late
    val remarks: String? = null,
    val profileImageUrl: String? = null // ADD THIS
)