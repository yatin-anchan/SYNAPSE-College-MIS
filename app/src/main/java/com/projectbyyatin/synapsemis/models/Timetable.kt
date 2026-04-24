package com.projectbyyatin.synapsemis.models

data class Timetable(
    var id: String = "",
    val classId: String = "",
    val className: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val departmentId: String = "",
    val departmentName: String = "",
    val semester: Int = 1,
    val academicYear: String = "",
    val defaultLectureDurationMinutes: Int = 60,
    // Key = day name: "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    val schedule: Map<String, List<TimetableSlot>> = emptyMap(),
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

data class TimetableSlot(
    val id: String = "",
    val slotType: String = "lecture",       // "lecture" or "break"
    // Only for lectures:
    val lectureType: String = "theory",     // "theory" or "practical"
    val subjectId: String = "",
    val subjectName: String = "",
    val subjectCode: String = "",
    val subjectType: String = "",           // Subject.type: "Theory", "Practical", "Elective"
    val facultyId: String = "",
    val facultyName: String = "",
    val room: String = "",
    // Only for breaks:
    val breakLabel: String = "Break",       // e.g., "Lunch Break", "Short Break"
    // Time (stored as minutes since midnight for easy sorting/calc)
    val startTimeMinutes: Int = 0,
    val durationMinutes: Int = 60,
    val endTimeMinutes: Int = 60            // always = startTimeMinutes + durationMinutes
)