package com.projectbyyatin.synapsemis.models

data class Semester(
    var id: String = "",
    val semesterNumber: Int = 1, // 1, 2, 3, 4, 5, 6
    val semesterName: String = "", // "Semester I", "Semester II"
    val courseId: String = "",
    val courseName: String = "",
    val departmentId: String = "",
    val department: String = "",
    var totalSubjects: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = 0L
)
