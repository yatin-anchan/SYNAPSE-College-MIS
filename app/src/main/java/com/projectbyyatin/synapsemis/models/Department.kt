package com.projectbyyatin.synapsemis.models

data class Department(
    var id: String = "",
    val name: String = "",
    val code: String = "",
    val college: String = "",
    val stream: String = "",
    var hod: String = "",
    var hodId: String = "",
    var hodEmail: String = "",
    var hodPhone: String = "",
    val description: String = "",
    val totalFaculty: Int = 0,
    val totalStudents: Int = 0,
    val establishedYear: String = "",
    val photoUrl: String = "",
    var isActive: Boolean = true,
    val createdAt: Long = 0L,
    var updatedAt: Long = 0L  // Changed from Timestamp? to Long
)