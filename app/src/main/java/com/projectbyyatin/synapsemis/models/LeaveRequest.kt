// models/LeaveRequest.kt
package com.projectbyyatin.synapsemis.models

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.*

data class LeaveRequest(
    var id: String = "",

    val applicantName: String = "",
    val applicantType: String = "", // "faculty" or "student"
    val departmentId: String = "",

    val startDate: String = "",
    val endDate: String = "",
    var numberOfDays: Int = 0,
    val reason: String = "",

    var status: String? = "pending", // "pending", "approved", "rejected"
    var approvedBy: String? = null,

    @ServerTimestamp
    @PropertyName("requestDate")
    var requestDate: Date? = null,

    @ServerTimestamp
    @PropertyName("approvalDate")
    var approvalDate: Date? = null
)
