package com.projectbyyatin.synapsemis.models

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.*

data class TimetableEntry(
    var id: String = "",

    val departmentId: String = "",
    val day: String = "", // "Monday", "Tuesday", etc.

    val subject: String = "",
    val subjectCode: String = "",
    val facultyName: String = "",
    val facultyId: String = "",

    val startTime: String = "", // "09:00"
    val endTime: String = "",   // "10:00"
    val room: String = "",
    val semester: String = "",  // "Sem 1", "Sem 2", etc.

    var isActive: Boolean = true,

    @ServerTimestamp
    @PropertyName("createdAt")
    var createdAt: Date? = null
)
