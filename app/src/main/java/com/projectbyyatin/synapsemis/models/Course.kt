package com.projectbyyatin.synapsemis.models

data class Course(
    var id: String = "",
    val name: String = "",
    val code: String = "",
    val duration: String = "", // "3 Years", "2 Years"
    val totalSemesters: Int = 6,
    val departmentId: String = "",
    val department: String = "",
    val college: String = "", // JR or SR
    val stream: String = "", // Science, Commerce, Arts
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
