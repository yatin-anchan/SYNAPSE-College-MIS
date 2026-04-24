package com.projectbyyatin.synapsemis.models

data class ClassStudent(
    var id: String = "",
    val classId: String = "",
    val className: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val rollNumber: String = "",  // ← Changed from studentRollNo
    val status: String = "",
    val joinedAt: Long = 0L,
    val approvedAt: Long = 0L,
    val approvedBy: String = ""
)
