package com.projectbyyatin.synapsemis.models

import com.google.firebase.firestore.Exclude

data class ExamSubject(
    val subjectId: String = "",
    val subjectName: String = "",
    val subjectCode: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val examDate: Long = 0L,
    val startTime: String = "",
    val endTime: String = "",
    val venue: String = "",

    // ─── Exam Type ───────────────────────────────────────────────────────────
    // "Written"   → has writtenMaxMarks only
    // "Internal"  → has internalMaxMarks only (covers internal assessment)
    // "Practical" → has internalMaxMarks only (treated as internal component)
    // "Combined"  → has BOTH writtenMaxMarks and internalMaxMarks
    val examType: String = "Written",

    // ─── Max Marks breakdown ─────────────────────────────────────────────────
    // writtenMaxMarks  : marks for the written/theory paper
    // internalMaxMarks : marks for internal assessment + practical combined
    // Use 0 for a component that doesn't apply to this subject.
    //
    // Legacy / convenience field — kept for backward-compat but prefer the two below:
    val maxMarks: Int = 100,

    val writtenMaxMarks: Int = 0,
    val internalMaxMarks: Int = 0,

    val duration: Float = 3f,

    // ─── Faculty ─────────────────────────────────────────────────────────────
    val assignedFacultyId: String = "",

    @get:Exclude
    var invigilators: List<Invigilator> = emptyList()
) {
    /** Total marks a student can score for this subject (written + internal). */
    @get:Exclude
    val totalMaxMarks: Int
        get() = writtenMaxMarks + internalMaxMarks

    companion object {
        /**
         * Factory helpers so call-sites don't have to remember which fields to fill.
         */
        fun written(
            subjectId: String, subjectName: String, subjectCode: String,
            courseId: String, courseName: String,
            examDate: Long, startTime: String, endTime: String,
            writtenMax: Int, duration: Float = 3f,
            assignedFacultyId: String = ""
        ) = ExamSubject(
            subjectId = subjectId, subjectName = subjectName, subjectCode = subjectCode,
            courseId = courseId, courseName = courseName,
            examDate = examDate, startTime = startTime, endTime = endTime,
            examType = "Written",
            maxMarks = writtenMax,
            writtenMaxMarks = writtenMax,
            internalMaxMarks = 0,
            duration = duration,
            assignedFacultyId = assignedFacultyId
        )

        fun internal(
            subjectId: String, subjectName: String, subjectCode: String,
            courseId: String, courseName: String,
            examDate: Long, startTime: String, endTime: String,
            internalMax: Int, duration: Float = 0f,
            assignedFacultyId: String = ""
        ) = ExamSubject(
            subjectId = subjectId, subjectName = subjectName, subjectCode = subjectCode,
            courseId = courseId, courseName = courseName,
            examDate = examDate, startTime = startTime, endTime = endTime,
            examType = "Internal",
            maxMarks = internalMax,
            writtenMaxMarks = 0,
            internalMaxMarks = internalMax,
            duration = duration,
            assignedFacultyId = assignedFacultyId
        )

        fun combined(
            subjectId: String, subjectName: String, subjectCode: String,
            courseId: String, courseName: String,
            examDate: Long, startTime: String, endTime: String,
            writtenMax: Int, internalMax: Int, duration: Float = 3f,
            assignedFacultyId: String = ""
        ) = ExamSubject(
            subjectId = subjectId, subjectName = subjectName, subjectCode = subjectCode,
            courseId = courseId, courseName = courseName,
            examDate = examDate, startTime = startTime, endTime = endTime,
            examType = "Combined",
            maxMarks = writtenMax + internalMax,
            writtenMaxMarks = writtenMax,
            internalMaxMarks = internalMax,
            duration = duration,
            assignedFacultyId = assignedFacultyId
        )
    }
}