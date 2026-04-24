package com.projectbyyatin.synapsemis.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class ExamMarks(
    var id: String = "",
    val examId: String = "",
    val subjectId: String = "",
    val subjectCode: String = "",
    val subjectName: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentRollNo: String = "",
    val courseId: String = "",
    val semester: Int = 1,

    // ─── Exam type for this record ───────────────────────────────────────────
    // "Written", "Internal", "Practical"
    val examType: String = "Written",

    // ─── Written Exam Marks ──────────────────────────────────────────────────
    var writtenMarksObtained: Float = 0f,
    var writtenMaxMarks: Int = 0,          // 0 means this exam has no written component

    // ─── Internal Marks (Internal Assessment + Practical combined) ───────────
    var internalMarksObtained: Float = 0f,
    var internalMaxMarks: Int = 0,         // 0 means this exam has no internal component

    // ─── Computed / Aggregate Fields ─────────────────────────────────────────
    // Total = written + internal
    var totalMarksObtained: Float = 0f,
    var totalMaxMarks: Int = 0,

    // percentage  = (totalMarksObtained / totalMaxMarks) * 100
    var percentage: Float = 0f,

    // CGPI (Cumulative Grade Point Index) on a 10-point scale
    // Formula: (totalMarksObtained / totalMaxMarks) * 10
    var cgpi: Float = 0f,

    var grade: String = "",

    @get:PropertyName("isAbsent")
    @set:PropertyName("isAbsent")
    var isAbsent: Boolean = false,

    // ─── Entry tracking ──────────────────────────────────────────────────────
    val enteredBy: String = "",
    val enteredByName: String = "",
    val enteredAt: Long = 0L,

    @get:PropertyName("isSubmitted")
    @set:PropertyName("isSubmitted")
    var isSubmitted: Boolean = false,
    val submittedAt: Long = 0L,

    // ─── Moderation ──────────────────────────────────────────────────────────
    @get:PropertyName("isModerated")
    @set:PropertyName("isModerated")
    var isModerated: Boolean = false,
    var moderatedBy: String = "",
    var moderatedByName: String = "",
    var moderatedAt: Long = 0L,
    var moderationSubmittedAt: Long = 0L,
    var moderationRemarks: String = "",
    var previousWrittenMarks: Float = 0f,
    var previousInternalMarks: Float = 0f,

    // ─── Re-evaluation ───────────────────────────────────────────────────────
    @get:PropertyName("isReEvaluated")
    @set:PropertyName("isReEvaluated")
    var isReEvaluated: Boolean = false,
    var reEvaluatedBy: String = "",
    var reEvaluatedAt: Long = 0L,
    var reEvaluationRemarks: String = "",

    // ─── Publishing ──────────────────────────────────────────────────────────
    @get:PropertyName("isPublished")
    @set:PropertyName("isPublished")
    var isPublished: Boolean = false,
    var publishedAt: Long = 0L,

    // Audit trail — stored as Timestamp in Firestore, excluded to avoid crash
    @get:Exclude @set:Exclude
    var createdAt: Long = 0L,
    @get:Exclude @set:Exclude
    var updatedAt: Long = 0L
) {
    // ─── Convenience helpers (not stored in Firestore) ────────────────────────

    /** True when this record carries a written mark (written exam or combined). */
    @get:Exclude
    val hasWrittenComponent: Boolean
        get() = writtenMaxMarks > 0

    /** True when this record carries an internal / practical mark. */
    @get:Exclude
    val hasInternalComponent: Boolean
        get() = internalMaxMarks > 0

    companion object {
        /**
         * Recalculates [totalMarksObtained], [totalMaxMarks], [percentage],
         * [cgpi] and [grade] from the written / internal component values.
         * Call this before persisting whenever marks change.
         */
        fun ExamMarks.recalculate(): ExamMarks {
            val total = writtenMarksObtained + internalMarksObtained
            val maxTotal = writtenMaxMarks + internalMaxMarks
            val pct = if (maxTotal > 0) (total / maxTotal) * 100f else 0f
            val gp = if (maxTotal > 0) (total / maxTotal) * 10f else 0f
            return copy(
                totalMarksObtained = total,
                totalMaxMarks = maxTotal,
                percentage = pct,
                cgpi = gp,
                grade = calculateGrade(pct)
            )
        }

        /**
         * Standard 10-point grading scale.
         * Adjust thresholds to match your institution's scheme.
         */
        fun calculateGrade(percentage: Float): String = when {
            percentage >= 90 -> "O"   // Outstanding
            percentage >= 80 -> "A+"
            percentage >= 70 -> "A"
            percentage >= 60 -> "B+"
            percentage >= 55 -> "B"
            percentage >= 50 -> "C"
            percentage >= 40 -> "D"
            else             -> "F"
        }
    }
}