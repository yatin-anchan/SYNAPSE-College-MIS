package com.projectbyyatin.synapsemis.models

import com.google.firebase.firestore.PropertyName

data class Subject(
    var id: String = "",
    val name: String = "",
    val code: String = "",
    val credits: Int = 4,
    val type: String = "", // "Theory", "Practical", "Elective"
    val semesterId: String = "",
    val semesterNumber: Int = 1,
    val courseId: String = "",
    val courseName: String = "",
    val departmentId: String = "",
    val department: String = "",
    val assignedFacultyId: String = "",
    val assignedFacultyName: String = "",
    val assignedFacultyEmail: String = "",

    // ✅ Changed to use @get:PropertyName to ensure proper serialization
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,

    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)