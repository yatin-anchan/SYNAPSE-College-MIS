package com.projectbyyatin.synapsemis.models

data class Invigilator(
    val teacherId: String = "",
    val teacherName: String = "",
    val role: String = "invigilator", // invigilator or supervisor
    val assignedAt: Long = 0L
)
