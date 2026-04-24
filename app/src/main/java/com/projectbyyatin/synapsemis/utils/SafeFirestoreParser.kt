package com.projectbyyatin.synapsemis.utils

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus

object SafeFirestoreParser {

    fun safeParseExam(document: DocumentSnapshot): Exam? {
        return try {
            val data = document.data ?: return null
            Exam().apply {
                id = document.id
                examName = data["examName"]?.toString() ?: ""
                semester = (data["semester"] as? Number)?.toInt() ?: 1
                academicYear = data["academicYear"]?.toString() ?: ""

                startDate = safeTimestampToLong(data["startDate"])
                endDate = safeTimestampToLong(data["endDate"])
                createdAt = safeTimestampToLong(data["createdAt"])
                confirmedAt = safeTimestampToLong(data["confirmedAt"])
                marksPublishedAt = safeTimestampToLong(data["marksPublishedAt"])

                courseId = data["courseId"]?.toString() ?: ""
                courseName = data["courseName"]?.toString() ?: ""
                isActive = data["isActive"] as? Boolean ?: true
                status = data["status"]?.toString() ?: "draft"
                isConfirmed = data["isConfirmed"] as? Boolean ?: false
                timetableGenerated = data["timetableGenerated"] as? Boolean ?: false
                createdBy = data["createdBy"]?.toString() ?: ""
                marksEntryEnabled = data["marksEntryEnabled"] as? Boolean ?: false
                marksPublished = data["marksPublished"] as? Boolean ?: false
                marksPublishedBy = data["marksPublishedBy"]?.toString() ?: ""

                // ✅ FIX: Parse subjects list (was missing — this is why subjects weren't loading)
                val rawSubjects = data["subjects"] as? List<*>
                if (rawSubjects != null) {
                    subjects = rawSubjects.mapNotNull { item -> parseExamSubject(item) }
                }

                val subjectsStatus = data["subjectsMarksStatus"] as? Map<*, *>
                if (subjectsStatus != null) {
                    subjectsMarksStatus = parseSubjectsMarksStatus(subjectsStatus)
                }
            }
        } catch (e: Exception) {
            Log.e("SafeParser", "Failed to parse exam ${document.id}: ${e.message}")
            null
        }
    }

    private fun parseExamSubject(item: Any?): ExamSubject? {
        return try {
            val map = item as? Map<*, *> ?: return null
            ExamSubject(
                subjectId = map["subjectId"]?.toString() ?: "",
                subjectName = map["subjectName"]?.toString() ?: "",
                subjectCode = map["subjectCode"]?.toString() ?: "",
                courseId = map["courseId"]?.toString() ?: "",
                courseName = map["courseName"]?.toString() ?: "",
                examDate = safeTimestampToLong(map["examDate"]),
                startTime = map["startTime"]?.toString() ?: "",
                endTime = map["endTime"]?.toString() ?: "",
                venue = map["venue"]?.toString() ?: "",
                maxMarks = (map["maxMarks"] as? Number)?.toInt() ?: 100,
                duration = (map["duration"] as? Number)?.toFloat() ?: 3f,
                assignedFacultyId = map["assignedFacultyId"]?.toString() ?: ""
            )
        } catch (e: Exception) {
            Log.e("SafeParser", "Failed to parse ExamSubject: ${e.message}")
            null
        }
    }

    private fun safeTimestampToLong(value: Any?): Long {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }
    }

    private fun parseSubjectsMarksStatus(rawStatus: Map<*, *>): Map<String, SubjectMarksStatus> {
        val result = mutableMapOf<String, SubjectMarksStatus>()
        rawStatus.forEach { (subjectId, statusData) ->
            try {
                val statusMap = statusData as? Map<*, *>
                if (statusMap != null) {
                    val status = SubjectMarksStatus().apply {
                        this.subjectId = subjectId.toString()
                        totalStudents = (statusMap["totalStudents"] as? Number)?.toInt() ?: 0
                        marksEntered = (statusMap["marksEntered"] as? Number)?.toInt() ?: 0
                        marksSubmitted = statusMap["marksSubmitted"] as? Boolean ?: false
                        submittedBy = statusMap["submittedBy"]?.toString() ?: ""
                        submittedAt = safeTimestampToLong(statusMap["submittedAt"])
                        moderated = statusMap["moderated"] as? Boolean ?: false
                        moderatedBy = statusMap["moderatedBy"]?.toString() ?: ""
                        moderatedAt = safeTimestampToLong(statusMap["moderatedAt"])
                        readyForPublish = statusMap["readyForPublish"] as? Boolean ?: false
                    }
                    result[subjectId.toString()] = status
                }
            } catch (e: Exception) {
                Log.e("SafeParser", "Failed SubjectMarksStatus $subjectId")
            }
        }
        return result
    }
}