package com.projectbyyatin.synapsemis.models

data class Exam(
    var id: String = "",
    var examName: String = "",
    var semester: Int = 1,
    var academicYear: String = "",
    var startDate: Long = 0L,
    var endDate: Long = 0L,
    var examType: String = "",

    var courseId: String = "",
    var courseName: String = "",
    var isActive: Boolean = true,

    var marksDraft: Map<String, Any>? = null,
    var lastDraftUpdated: com.google.firebase.Timestamp? = null,
    var draftProgress: String? = null,

    val courses: List<String> = emptyList(),
    var subjects: List<ExamSubject> = emptyList(),
    var status: String = "draft",
    var isConfirmed: Boolean = false,
    var timetableGenerated: Boolean = false,
    var createdBy: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var confirmedAt: Long = 0L,

    var marksEntryEnabled: Boolean = false,
    var marksPublished: Boolean = false,
    var marksPublishedAt: Long = 0L,
    var marksPublishedBy: String = "",
    var subjectsMarksStatus: Map<String, SubjectMarksStatus> = emptyMap()
)

data class SubjectMarksStatus(
    var subjectId: String = "",
    var totalStudents: Int = 0,
    var marksEntered: Int = 0,
    var marksSubmitted: Boolean = false,
    var submittedBy: String = "",
    var submittedAt: Long = 0L,
    var moderated: Boolean = false,
    var moderatedBy: String = "",
    var moderatedAt: Long = 0L,
    var readyForPublish: Boolean = false
)
