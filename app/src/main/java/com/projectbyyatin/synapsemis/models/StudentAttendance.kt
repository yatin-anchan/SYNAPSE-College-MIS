    package com.projectbyyatin.synapsemis.models

    data class StudentAttendance(
        var id: String = "", // Firestore document ID of student
        var studentId: String = "",
        var studentName: String = "",
        var rollNumber: String = "",
        var email: String = "",
        var status: String = "present", // "present" or "absent"
        var remarks: String = ""
    )