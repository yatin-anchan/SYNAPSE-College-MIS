package com.projectbyyatin.synapsemis.models

data class Class(
    var id: String = "",
    val className: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val departmentId: String = "",
    val departmentName: String = "",
    val classTeacherId: String = "",
    val classTeacherName: String = "",
    val maxSize: Int = 60,
    val currentSize: Int = 0,
    val currentSemester: Int = 1,
    val totalSemesters: Int = 6, // Total semesters in course
    val academicYear: String = "",
    val batch: String = "", // e.g., "2023-2026", "2024-2027"
    val inviteCode: String = "",
    val isActive: Boolean = true,
    val isCompleted: Boolean = false, // True when all semesters completed
    val completedAt: Long = 0L,
    val canPromote: Boolean = false, // Can only promote after results released
    val resultsReleasedForSemester: Int = 0, // Track which semester results are released
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0L
)
