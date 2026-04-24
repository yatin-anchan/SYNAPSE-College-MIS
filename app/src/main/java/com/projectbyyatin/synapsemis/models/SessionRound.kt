package com.projectbyyatin.synapsemis.models

data class SessionRound(
    var id: String = "",
    val sessionDate: Long = 0,
    val createdBy: String = "",
    val createdByName: String = "",
    val roomAllocations: List<RoomAllocation> = emptyList(),
    val isSubmitted: Boolean = false,
    val submittedAt: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

data class RoomAllocation(
    val roomNo: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val classId: String = "",
    val className: String = "",
    val isPresent: Boolean = true
)

data class ClassInfo(
    val id: String = "",
    val className: String = "",
    val departmentId: String = "",
    val departmentName: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val semester: Int = 0,
    val section: String = "",
    val isActive: Boolean = true
)