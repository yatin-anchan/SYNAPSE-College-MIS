package com.projectbyyatin.synapsemis.models

data class AttendanceDraft(
    var id: String = "",
    val classId: String? = null,
    val className: String? = null,
    val subjectId: String? = null,
    val subjectName: String? = null,
    val departmentId: String? = null,
    val departmentName: String? = null,
    val facultyId: String? = null,
    val date: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: Int? = null,
    val status: String? = "draft", // draft, published
    val createdAt: Long? = null,
    val publishedAt: Long? = null,
    val createdBy: String? = null,
    val totalStudents: Int? = null,
    val presentCount: Int? = null,
    val absentCount: Int? = null,
    var students: List<StudentAttendance> = emptyList()
)