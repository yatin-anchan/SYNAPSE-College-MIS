package com.projectbyyatin.synapsemis.models

import com.google.firebase.firestore.PropertyName

data class Faculty(
    var id: String = "",
    val employeeId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val designation: String = "",
    var role: String = "Faculty Member",
    val department: String = "",
    val departmentId: String = "",
    val college: String = "",
    val stream: String = "",
    val photoUrl: String = "",
    val qualifications: String = "",
    val experience: String = "",
    val subjects: List<String> = emptyList(),
    val appAccessEnabled: Boolean = false,
    val profileCompleted: Boolean = false,

    // ✅ Changed to use @get:PropertyName to ensure proper serialization
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,

    // ✅ Map for Firestore's hodOf (which is a map)
    @PropertyName("hodOf")
    val hodOf: Map<String, Any>? = null,

    // ✅ Use Long to match your Firestore data (1769608012776)
    val createdAt: Long = 0L,
    var updatedAt: Long = 0L
)