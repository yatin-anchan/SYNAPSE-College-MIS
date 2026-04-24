package com.projectbyyatin.synapsemis.models

data class AttendanceSession(
    var id: String = "",
    val classId          : String = "",
    val className        : String = "",
    val subjectId        : String = "",
    val subjectName      : String = "",
    val departmentId     : String = "",
    val departmentName   : String = "",
    val semester         : Int    = 1,
    val academicYear     : String = "",
    val date             : String = "",   // "2026-02-23"
    val month            : String = "",   // "2026-02"
    val day              : String = "",   // "Monday"
    val sessionStartTime : String = "",
    val sessionEndTime   : String = "",
    val sessionDuration  : Int    = 0,
    val markedBy         : String = "",
    val markedByName     : String = "",
    val markedAt         : Long   = 0L,
    val totalStudents    : Int = 0,
    val presentCount     : Int = 0,
    val absentCount      : Int = 0,
    val lateCount        : Int = 0,
    val students         : List<StudentRecord> = emptyList()
)

data class StudentRecord(
    val studentId       : String = "",
    val studentName     : String = "",
    val studentRollNo   : String = "",
    val status          : String = "",
    val remarks         : String = "",
    val classId         : String = "",
    val className       : String = "",
    val subjectId       : String = "",
    val subjectName     : String = "",
    val departmentId    : String = "",
    val departmentName  : String = "",
    val semester        : Int    = 1,
    val academicYear    : String = "",
    val date            : String = "",
    val month           : String = "",
    val day             : String = "",
    val sessionStartTime: String = "",
    val sessionEndTime  : String = "",
    val sessionDuration : Int    = 0,
    val markedBy        : String = "",
    val markedByName    : String = "",
    val markedAt        : Long   = 0L
)