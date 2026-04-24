package com.projectbyyatin.synapsemis.models

data class Attendance(
    var id: String = "",
    val classId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentRollNo: String = "",
    val date: String = "", // Format: "2026-01-03"
    val month: String = "", // Format: "2026-01"
    val semester: Int = 1,
    val academicYear: String = "",
    var status: String = "", // "present", "absent", "late"
    val markedBy: String = "",
    val markedByName: String = "",
    val markedAt: Long = 0L,
    val remarks: String = ""
)
