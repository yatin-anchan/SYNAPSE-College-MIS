package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.projectbyyatin.synapsemis.adapters.FacultyMarksEntryAdapter
import com.projectbyyatin.synapsemis.models.ExamMarks
import com.projectbyyatin.synapsemis.models.ExamMarks.Companion.calculateGrade
import com.projectbyyatin.synapsemis.models.Student
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus

// ─── FIX 1: Removed "import ExamMarks.Companion.recalculate" ─────────────────
// That import caused the compiler to treat `recalculate` as an infix/operator
// candidate, producing:
//   • 'infix' modifier is required on FirNamedFunctionSymbol kotlin/collections/get
//   • Cannot infer type for this parameter
//   • Expecting function type
// Instead, we call recalculate() via a local helper function below so the
// compiler always has full type information at the call site.

class FacultyMarksEntryActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examNameText: TextView
    private lateinit var subjectNameText: TextView
    private lateinit var examTypeChip: TextView
    private lateinit var marksSchemeText: TextView
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var submitButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var warningCard: View
    private lateinit var warningText: TextView
    private lateinit var bottomButtonsContainer: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: FacultyMarksEntryAdapter

    private var examId: String = ""
    private var subjectId: String = ""
    private var subjectName: String = ""
    private var courseId: String = ""
    private var examName: String = ""

    private var examType: String = "Written"
    private var writtenMaxMarks: Int = 0
    private var internalMaxMarks: Int = 0
    private var maxMarks: Int = 100

    private var marksList = mutableListOf<ExamMarks>()
    private var isSubmitted = false
    private var currentFacultyId = ""
    private var currentFacultyName = ""

    private data class StudentData(val id: String, val name: String, val rollNo: String)

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 2: Local recalculate helper with an explicit return type.
    // The companion extension returns a NEW copy (data class) — calling it
    // without capturing the result means marks are never updated.
    // This wrapper makes the intent clear and keeps full type info for the
    // compiler, avoiding all type-inference errors at every call site.
    // ─────────────────────────────────────────────────────────────────────────
    private fun ExamMarks.recalc(): ExamMarks {
        val total    = writtenMarksObtained + internalMarksObtained
        val maxTotal = writtenMaxMarks + internalMaxMarks
        val pct      = if (maxTotal > 0) (total / maxTotal) * 100f else 0f
        val gp       = if (maxTotal > 0) (total / maxTotal) * 10f  else 0f
        return copy(
            totalMarksObtained = total,
            totalMaxMarks      = maxTotal,
            percentage         = pct,
            cgpi               = gp,
            grade              = calculateGrade(pct)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_marks_entry)

        extractIntentExtras()
        if (!isValidIntentData()) {
            Toast.makeText(this, "Missing exam/subject/course data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        firestore = FirebaseFirestore.getInstance()
        auth      = FirebaseAuth.getInstance()

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupKeyboardListener()
        loadFacultyInfo()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isSubmitted && adapter.hasUnsavedChanges()) showExitConfirmation() else finish()
            }
        })
    }

    // ─── Intent ──────────────────────────────────────────────────────────────

    private fun extractIntentExtras() {
        examId      = intent.getStringExtra("EXAM_ID")      ?: ""
        subjectId   = intent.getStringExtra("SUBJECT_ID")   ?: ""
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        courseId    = intent.getStringExtra("COURSE_ID")    ?: ""
        examType    = intent.getStringExtra("EXAM_TYPE")    ?: "Written"

        writtenMaxMarks  = intent.getIntExtra("WRITTEN_MAX_MARKS",  0)
        internalMaxMarks = intent.getIntExtra("INTERNAL_MAX_MARKS", 0)

        if (writtenMaxMarks == 0 && internalMaxMarks == 0) {
            writtenMaxMarks = intent.getIntExtra("MAX_MARKS", 100)
        }
        maxMarks = writtenMaxMarks + internalMaxMarks

        Log.d("MarksEntry", "examId=$examId subjectId=$subjectId courseId=$courseId " +
                "examType=$examType written=$writtenMaxMarks internal=$internalMaxMarks")
    }

    private fun isValidIntentData(): Boolean =
        examId.isNotBlank() && subjectId.isNotBlank() && courseId.isNotBlank()

    // ─── Views ───────────────────────────────────────────────────────────────

    private fun initializeViews() {
        toolbar                = findViewById(R.id.toolbar)
        examNameText           = findViewById(R.id.exam_name)
        subjectNameText        = findViewById(R.id.subject_name)
        examTypeChip           = findViewById(R.id.exam_type_chip)
        marksSchemeText        = findViewById(R.id.marks_scheme_text)
        statusText             = findViewById(R.id.status_text)
        statsText              = findViewById(R.id.stats_text)
        recyclerView           = findViewById(R.id.recycler_view)
        loadingProgress        = findViewById(R.id.loading_progress)
        submitButton           = findViewById(R.id.submit_button)
        saveButton             = findViewById(R.id.save_button)
        warningCard            = findViewById(R.id.warning_card)
        warningText            = findViewById(R.id.warning_text)
        bottomButtonsContainer = findViewById(R.id.bottom_buttons_container)
        setupButtons()
        displayMarksScheme()
    }

    private fun displayMarksScheme() {
        examTypeChip.text = examType
        val parts = mutableListOf<String>()
        if (writtenMaxMarks  > 0) parts.add("Written: $writtenMaxMarks")
        if (internalMaxMarks > 0) parts.add("Internal: $internalMaxMarks")
        parts.add("Total: $maxMarks")
        marksSchemeText.text = parts.joinToString("  |  ")
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Enter Marks"
        toolbar.setNavigationOnClickListener {
            if (!isSubmitted && adapter.hasUnsavedChanges()) showExitConfirmation() else finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = FacultyMarksEntryAdapter(
            marksList        = marksList,
            writtenMaxMarks  = writtenMaxMarks,
            internalMaxMarks = internalMaxMarks,
            isLocked         = isSubmitted,
            onMarksChanged   = { updateStats() }   // FIX 3: named + explicit lambda
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupKeyboardListener() {
        val rootView = window.decorView.rootView
        rootView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                private var wasKeyboardOpen = false
                override fun onGlobalLayout() {
                    val rect = android.graphics.Rect()
                    rootView.getWindowVisibleDisplayFrame(rect)
                    val isKeyboardOpen = (rootView.height - rect.bottom) > rootView.height * 0.15
                    if (isKeyboardOpen == wasKeyboardOpen) return
                    wasKeyboardOpen = isKeyboardOpen
                    if (isKeyboardOpen) {
                        bottomButtonsContainer.animate()
                            .translationY(bottomButtonsContainer.height.toFloat())
                            .alpha(0f).setDuration(200)
                            .withEndAction { bottomButtonsContainer.visibility = View.GONE }
                            .start()
                    } else {
                        bottomButtonsContainer.visibility = View.VISIBLE
                        bottomButtonsContainer.animate()
                            .translationY(0f).alpha(1f).setDuration(200).start()
                    }
                }
            }
        )
    }

    private fun setupButtons() {
        saveButton.setOnClickListener   { saveDraft() }
        submitButton.setOnClickListener { showSubmitConfirmation() }
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    private fun loadFacultyInfo() {
        val facultyId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        firestore.collection("faculty").document(facultyId).get()
            .addOnSuccessListener { doc ->
                currentFacultyId   = facultyId
                currentFacultyName = if (doc.exists()) doc.getString("name") ?: "Faculty" else "Faculty"
                loadExamData()
            }
            .addOnFailureListener {
                currentFacultyId   = facultyId
                currentFacultyName = "Faculty"
                loadExamData()
            }
    }

    private fun loadExamData() {
        firestore.collection("exams").document(examId).get()
            .addOnSuccessListener { examDoc ->
                if (!examDoc.exists()) {
                    Toast.makeText(this, "Exam not found", Toast.LENGTH_SHORT).show()
                    finish(); return@addOnSuccessListener
                }
                examName             = examDoc.getString("examName") ?: "Exam"
                examNameText.text    = examName
                subjectNameText.text = subjectName
                loadStudents()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load exam details", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadStudents() {
        loadingProgress.visibility = View.VISIBLE
        recyclerView.visibility    = View.GONE

        firestore.collection("students")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { studentDocs ->
                if (studentDocs.isEmpty) {
                    loadingProgress.visibility = View.GONE
                    Toast.makeText(this, "No students enrolled", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                val list = studentDocs.documents.map { doc ->
                    val student = doc.toObject(Student::class.java)
                    val rollNo  = student?.rollNumber?.takeIf { it.isNotBlank() }
                        ?: doc.getString("rollNumber")?.takeIf { it.isNotBlank() }
                        ?: doc.id.takeLast(6).padStart(6, '0')
                    StudentData(
                        id     = doc.id,
                        name   = student?.fullName ?: doc.getString("fullName") ?: "Unknown",
                        rollNo = rollNo
                    )
                }
                loadDraftData(list)
            }
            .addOnFailureListener {
                loadingProgress.visibility = View.GONE
                Toast.makeText(this, "Failed to load students", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDraftData(studentDataList: List<StudentData>) {
        firestore.collection("exams").document(examId).get()
            .addOnSuccessListener { examDoc ->
                if (!examDoc.exists()) {
                    setupEmptyMarksList(studentDataList)
                    return@addOnSuccessListener
                }

                // FIX: Break the deep chain into two explicit typed steps.
                // A single long chain with a ?.get("students") at the end caused the
                // Kotlin parser to treat it as a separate statement, producing:
                //   • Incomplete code / 'infix' modifier required on kotlin/collections/get
                //   • Call requires API level 26 (Matcher#start via named-group regex)
                // Splitting into subjectNode → studentsMap gives the compiler a fully
                // typed intermediate value and eliminates all three errors at once.
                @Suppress("UNCHECKED_CAST")
                val subjectNode: Map<*, *>? =
                    ((examDoc.get("marksDraft") as? Map<*, *>)
                        ?.get("subjects") as? Map<*, *>)
                        ?.get(subjectId) as? Map<*, *>

                @Suppress("UNCHECKED_CAST")
                val studentsMap: Map<String, Any?>? =
                    subjectNode?.get("students") as? Map<String, Any?>

                @Suppress("UNCHECKED_CAST")
                isSubmitted =
                    ((examDoc.get("subjectsMarksStatus") as? Map<*, *>)
                        ?.get(subjectId) as? Map<*, *>)
                        ?.get("marksSubmitted") as? Boolean ?: false

                marksList.clear()

                studentDataList.forEach { sd ->
                    @Suppress("UNCHECKED_CAST")
                    val draft: Map<String, Any?>? = studentsMap?.get(sd.id) as? Map<String, Any?>

                    // FIX 4: Explicit return types on helper lambdas.
                    // Original code used `val numFloat = { key: String -> ... }`
                    // without a declared return type, which caused:
                    //   • "Cannot infer type for this parameter. Specify it explicitly."
                    //   • "Expecting function type"
                    // Adding `: Float` and `: Int` return types resolves both.
                    val numFloat: (String) -> Float = { key ->
                        when (val v = draft?.get(key)) {
                            is Double -> v.toFloat()
                            is Float  -> v
                            is Long   -> v.toFloat()
                            is Int    -> v.toFloat()
                            else      -> 0f
                        }
                    }

                    val numInt: (String) -> Int = { key ->
                        when (val v = draft?.get(key)) {
                            is Long   -> v.toInt()
                            is Int    -> v
                            is Double -> v.toInt()
                            else      -> 0
                        }
                    }

                    val dWritten  = numFloat("writtenMarksObtained")
                        .let { if (it == 0f) numFloat("marksObtained") else it }
                    val dInternal = numFloat("internalMarksObtained")
                    val dAbsent   = draft?.get("isAbsent") as? Boolean ?: false

                    val wMax = numInt("writtenMaxMarks") .let { if (it == 0) writtenMaxMarks  else it }
                    val iMax = numInt("internalMaxMarks").let { if (it == 0) internalMaxMarks else it }

                    // FIX 5: Capture the result of recalc() — the original called
                    // recalculate() but never stored the returned copy, so totals,
                    // percentage, CGPI and grade were always 0 / blank.
                    val entry = ExamMarks(
                        examId               = examId,
                        subjectId            = subjectId,
                        subjectName          = subjectName,
                        studentId            = sd.id,
                        studentName          = sd.name,
                        studentRollNo        = sd.rollNo,
                        courseId             = courseId,
                        examType             = examType,
                        writtenMaxMarks      = wMax,
                        internalMaxMarks     = iMax,
                        totalMaxMarks        = wMax + iMax,
                        writtenMarksObtained  = dWritten,
                        internalMarksObtained = dInternal,
                        isAbsent             = dAbsent
                    ).recalc()   // result captured ✓

                    marksList.add(entry)
                }

                marksList.sortBy { it.studentRollNo }
                adapter.updateList(marksList)
                adapter.setLocked(isSubmitted)
                updateStats()
                updateWarning()
                loadingProgress.visibility = View.GONE
                recyclerView.visibility    = View.VISIBLE
            }
            .addOnFailureListener {
                setupEmptyMarksList(studentDataList)
            }
    }

    private fun setupEmptyMarksList(studentDataList: List<StudentData>) {
        marksList.clear()
        studentDataList.forEach { sd ->
            marksList.add(
                ExamMarks(
                    examId           = examId,
                    subjectId        = subjectId,
                    subjectName      = subjectName,
                    studentId        = sd.id,
                    studentName      = sd.name,
                    studentRollNo    = sd.rollNo,
                    courseId         = courseId,
                    examType         = examType,
                    writtenMaxMarks  = writtenMaxMarks,
                    internalMaxMarks = internalMaxMarks,
                    totalMaxMarks    = maxMarks
                )
            )
        }
        marksList.sortBy { it.studentRollNo }
        adapter.updateList(marksList)
        updateStats()
        recyclerView.visibility    = View.VISIBLE
        loadingProgress.visibility = View.GONE
    }

    // ─── Draft save ───────────────────────────────────────────────────────────

    private fun saveDraft() {
        if (isSubmitted) {
            Snackbar.make(recyclerView, "Marks are locked after submission", Snackbar.LENGTH_SHORT).show()
            return
        }

        loadingProgress.visibility = View.VISIBLE

        firestore.collection("exams").document(examId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { loadingProgress.visibility = View.GONE; return@addOnSuccessListener }

                val studentsMap = mutableMapOf<String, Any>()
                marksList.forEach { m ->
                    studentsMap[m.studentId] = mapOf(
                        "writtenMarksObtained"  to m.writtenMarksObtained,
                        "internalMarksObtained" to m.internalMarksObtained,
                        "writtenMaxMarks"        to m.writtenMaxMarks,
                        "internalMaxMarks"       to m.internalMaxMarks,
                        "marksObtained"          to m.totalMarksObtained,
                        "isAbsent"               to m.isAbsent,
                        "updatedAt"              to FieldValue.serverTimestamp()
                    )
                }

                val completed = marksList.count { it.totalMarksObtained > 0 || it.isAbsent }
                val progress  = "$completed / ${marksList.size} students"

                val update = mapOf(
                    "marksDraft.subjects.$subjectId.students"    to studentsMap,
                    "marksDraft.subjects.$subjectId.subjectName" to subjectName,
                    "marksDraft.subjects.$subjectId.progress"    to progress,
                    "marksDraft.subjects.$subjectId.updatedAt"   to FieldValue.serverTimestamp(),
                    "lastDraftUpdated"                           to FieldValue.serverTimestamp()
                )

                firestore.collection("exams").document(examId).update(update)
                    .addOnSuccessListener {
                        adapter.clearUnsavedChanges()
                        marksList.indices.forEach { index ->
                            marksList[index] = marksList[index].recalc()
                        } // Update model data
                        adapter.notifyDataSetChanged()
                        updateStats()
                        updateWarning()

                        statusText.text = "Draft Saved"
                        statusText.setTextColor(
                            ContextCompat.getColor(this, android.R.color.holo_green_dark)
                        )
                        statsText.text             = progress
                        loadingProgress.visibility = View.GONE
                        Snackbar.make(recyclerView, "Draft saved successfully", Snackbar.LENGTH_SHORT).show()
                        lifecycleScope.launch {
                            delay(2000)
                            statusText.text = "Draft"
                            statusText.setTextColor(
                                ContextCompat.getColor(
                                    this@FacultyMarksEntryActivity,
                                    android.R.color.holo_orange_dark
                                )
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        loadingProgress.visibility = View.GONE
                        Snackbar.make(recyclerView, "Failed to save draft: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                loadingProgress.visibility = View.GONE
                Snackbar.make(recyclerView, "Network error", Snackbar.LENGTH_LONG).show()
            }
    }

    // ─── Submit ───────────────────────────────────────────────────────────────

    private fun showSubmitConfirmation() {
        val allFilled = marksList.all { it.totalMarksObtained > 0 || it.isAbsent }
        if (!allFilled) {
            Snackbar.make(recyclerView, "Please enter marks for all students first", Snackbar.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Submit Marks")
            .setMessage(
                "Are you sure you want to submit marks?\n\n" +
                        "⚠️ Once submitted, marks will be LOCKED and only moderators can make changes."
            )
            .setPositiveButton("Submit") { _, _ -> submitMarks() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitMarks() {
        if (isSubmitted) return

        loadingProgress.visibility = View.VISIBLE
        val batch       = firestore.batch()
        val submittedAt = System.currentTimeMillis()

        marksList.forEach { marks ->
            // FIX 6: recalc() result is captured before .copy() — original called
            // recalculate().copy(...) which works, but only because it chained on
            // the returned copy. Replaced with local recalc() for consistency and
            // to avoid the removed companion import.
            val finalMarks = marks.recalc().copy(
                isSubmitted   = true,
                enteredBy     = currentFacultyId,
                enteredByName = currentFacultyName,
                submittedAt   = submittedAt
            )

            val docRef = if (marks.id.isEmpty())
                firestore.collection("examMarks").document()
            else
                firestore.collection("examMarks").document(marks.id)

            if (marks.id.isEmpty()) marks.id = docRef.id
            batch.set(docRef, finalMarks)
        }

        val statusUpdate = SubjectMarksStatus(
            subjectId      = subjectId,
            totalStudents  = marksList.size,
            marksEntered   = marksList.size,
            marksSubmitted = true,
            submittedBy    = currentFacultyId,
            submittedAt    = submittedAt
        )
        batch.update(
            firestore.collection("exams").document(examId),
            "subjectsMarksStatus.$subjectId",
            statusUpdate
        )

        batch.commit()
            .addOnSuccessListener {
                isSubmitted = true
                adapter.setLocked(true)
                adapter.clearUnsavedChanges()
                updateStats()
                updateWarning()
                loadingProgress.visibility = View.GONE
                Snackbar.make(recyclerView, "✓ Marks submitted and locked", Snackbar.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                loadingProgress.visibility = View.GONE
                Snackbar.make(recyclerView, "Error submitting: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    // ─── Stats / Warning ──────────────────────────────────────────────────────

    private fun updateStats() {
        val entered = marksList.count { it.totalMarksObtained > 0 || it.isAbsent }
        statsText.text = "$entered / ${marksList.size} students"
        if (isSubmitted) {
            statusText.text = "Submitted"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            statusText.text = "Draft"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        }
    }

    private fun updateWarning() {
        when {
            isSubmitted -> {
                warningCard.visibility = View.VISIBLE
                warningText.text = "⚠️ Marks submitted and locked. Only moderators can make changes."
            }
            !marksList.all { it.totalMarksObtained > 0 || it.isAbsent } -> {
                warningCard.visibility = View.VISIBLE
                warningText.text = "ℹ️ Enter marks for all students before submitting."
            }
            else -> warningCard.visibility = View.GONE
        }
    }

    // ─── Back / Exit ─────────────────────────────────────────────────────────

    private fun showExitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to save before exiting?")
            .setPositiveButton("Save Draft") { _, _ ->
                saveDraft()
                lifecycleScope.launch { delay(500); finish() }
            }
            .setNegativeButton("Exit Without Saving") { _, _ -> finish() }
            .setNeutralButton("Cancel", null)
            .show()
    }
}