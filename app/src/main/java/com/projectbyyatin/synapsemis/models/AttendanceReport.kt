package com.projectbyyatin.synapsemis.models

data class AttendanceReport(
    // Navigation / identity
    val studentId   : String = "",
    val studentName : String = "",
    val rollNumber  : String = "",
    val classId     : String = "",
    val className   : String = "",
    val subjectId   : String = "",
    val subjectName : String = "",
    val month       : String = "",

    // Display / analytics
    val percentage   : Float = 0f,   // ✅ single source of truth
    val totalStudents: Int   = 0,
    val presentCount : Int   = 0,
    val facultyName  : String = "",
    val recordedAt   : String = ""
) {
    companion object {

        /** Factory used by AttendanceReportsActivity */
        fun fromStudent(
            studentId   : String,
            studentName : String,
            rollNumber  : String,
            classId     : String,
            className   : String,
            subjectId   : String,
            subjectName : String,
            month       : String,
            total       : Int,
            present     : Int,
            facultyName : String = "",
            recordedAt  : String = ""
        ): AttendanceReport {

            val percent =
                if (total > 0) (present.toFloat() / total) * 100f else 0f

            return AttendanceReport(
                studentId     = studentId,
                studentName   = studentName,
                rollNumber    = rollNumber,
                classId       = classId,
                className     = className,
                subjectId     = subjectId,
                subjectName   = subjectName,
                month         = month,
                percentage    = percent,
                totalStudents = total,
                presentCount  = present,
                facultyName   = facultyName,
                recordedAt    = recordedAt
            )
        }
    }
}