package com.projectbyyatin.synapsemis.faculty

data class ViewAttendanceItem(
    val subjectName: String,
    val className: String,
    val period: String,
    val date: String,
    val present: Int,
    val absent: Int
)