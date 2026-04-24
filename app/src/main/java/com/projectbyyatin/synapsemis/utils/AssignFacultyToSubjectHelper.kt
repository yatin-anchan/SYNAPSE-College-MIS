package com.projectbyyatin.synapsemis.utils

import com.google.firebase.firestore.FirebaseFirestore

class AssignFacultyToSubjectHelper(private val firestore: FirebaseFirestore) {

    /**
     * Assigns a faculty to a subject with proper ID handling
     *
     * @param subjectId The subject document ID
     * @param facultyDocId The faculty DOCUMENT ID (not Auth UID)
     * @param onSuccess Callback on success
     * @param onError Callback on error
     */
    fun assignFaculty(
        subjectId: String,
        facultyDocId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // First, get the faculty details
        firestore.collection("faculty").document(facultyDocId)
            .get()
            .addOnSuccessListener { facultyDoc ->
                if (!facultyDoc.exists()) {
                    onError(Exception("Faculty not found"))
                    return@addOnSuccessListener
                }

                val facultyName = facultyDoc.getString("name") ?: ""
                val facultyEmail = facultyDoc.getString("email") ?: ""

                // Get the subject to update faculty's subjects array
                firestore.collection("subjects").document(subjectId)
                    .get()
                    .addOnSuccessListener { subjectDoc ->
                        if (!subjectDoc.exists()) {
                            onError(Exception("Subject not found"))
                            return@addOnSuccessListener
                        }

                        val subjectName = subjectDoc.getString("name") ?: ""

                        // Update subject with faculty assignment
                        val subjectUpdates = hashMapOf<String, Any>(
                            "assignedFacultyId" to facultyDocId, // Store faculty DOCUMENT ID
                            "assignedFacultyName" to facultyName,
                            "assignedFacultyEmail" to facultyEmail,
                            "updatedAt" to System.currentTimeMillis()
                        )

                        firestore.collection("subjects").document(subjectId)
                            .update(subjectUpdates)
                            .addOnSuccessListener {
                                // Update faculty's subjects array
                                updateFacultySubjects(facultyDocId, subjectName, onSuccess, onError)
                            }
                            .addOnFailureListener { e -> onError(e) }
                    }
                    .addOnFailureListener { e -> onError(e) }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    private fun updateFacultySubjects(
        facultyDocId: String,
        subjectName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("faculty").document(facultyDocId)
            .get()
            .addOnSuccessListener { doc ->
                val currentSubjects = (doc.get("subjects") as? List<String>)?.toMutableList()
                    ?: mutableListOf()

                if (!currentSubjects.contains(subjectName)) {
                    currentSubjects.add(subjectName)

                    val facultyUpdates = hashMapOf<String, Any>(
                        "subjects" to currentSubjects,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    firestore.collection("faculty").document(facultyDocId)
                        .update(facultyUpdates)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { e -> onError(e) }
                } else {
                    onSuccess()
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    /**
     * Unassigns faculty from a subject
     */
    fun unassignFaculty(
        subjectId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // First get the subject to know which faculty to update
        firestore.collection("subjects").document(subjectId)
            .get()
            .addOnSuccessListener { subjectDoc ->
                val facultyId = subjectDoc.getString("assignedFacultyId") ?: ""
                val subjectName = subjectDoc.getString("name") ?: ""

                // Clear faculty assignment from subject
                val updates = hashMapOf<String, Any>(
                    "assignedFacultyId" to "",
                    "assignedFacultyName" to "",
                    "assignedFacultyEmail" to "",
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("subjects").document(subjectId)
                    .update(updates)
                    .addOnSuccessListener {
                        // Remove from faculty's subjects array
                        if (facultyId.isNotEmpty()) {
                            removeFacultySubject(facultyId, subjectName, onSuccess, onError)
                        } else {
                            onSuccess()
                        }
                    }
                    .addOnFailureListener { e -> onError(e) }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    private fun removeFacultySubject(
        facultyDocId: String,
        subjectName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("faculty").document(facultyDocId)
            .get()
            .addOnSuccessListener { doc ->
                val currentSubjects = (doc.get("subjects") as? List<String>)?.toMutableList()
                    ?: mutableListOf()

                currentSubjects.remove(subjectName)

                firestore.collection("faculty").document(facultyDocId)
                    .update(
                        "subjects", currentSubjects,
                        "updatedAt", System.currentTimeMillis()
                    )
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e) }
            }
            .addOnFailureListener { e -> onError(e) }
    }
}