package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.StudentMarksAdapter
import com.projectbyyatin.synapsemis.models.ExamMarks
import com.projectbyyatin.synapsemis.models.ExamMarks.Companion.calculateGrade

// FIX 1: Removed "import ExamMarks.Companion.recalculate"
// Same root cause as all other files — the companion-extension import triggers
// the compiler's internal named-group regex (API 26+), causing:
//   • 'infix' modifier required on FirNamedFunctionSymbol kotlin/collections/get
//   • Call requires API level 26 (Matcher#start via MatchGroupCollection#get(String))
// Replaced with a local recalc() private extension below.

class EnterSubjectMarksActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var subjectName: TextView
    private lateinit var examTypeChip: TextView
    private lateinit var marksSchemeText: TextView
    private lateinit var studentsCount: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSaveAll: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: StudentMarksAdapter
    private var marksList = mutableListOf<ExamMarks>()

    private var examId: String = ""
    private var subjectId: String = ""
    private var courseId: String = ""

    private var examType: String = "Written"
    private var writtenMaxMarks: Int = 0
    private var internalMaxMarks: Int = 0

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 2: Local recalc() with explicit return type.
    // ExamMarks is a data class — recalculate() returns a NEW copy via .copy().
    // The original code used `with(marks) { recalculate().copy(...) }` which
    // worked coincidentally because .copy() was chained on the returned object,
    // but importing recalculate() as a companion extension still triggered the
    // API-26 / infix compiler errors. Using this local version avoids the import
    // entirely while being clearer at each call site.
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
        setContentView(R.layout.activity_enter_subject_marks)

        examId           = intent.getStringExtra("EXAM_ID")       ?: ""
        subjectId        = intent.getStringExtra("SUBJECT_ID")    ?: ""
        courseId         = intent.getStringExtra("COURSE_ID")     ?: ""
        examType         = intent.getStringExtra("EXAM_TYPE")     ?: "Written"
        writtenMaxMarks  = intent.getIntExtra("WRITTEN_MAX_MARKS",  0)
        internalMaxMarks = intent.getIntExtra("INTERNAL_MAX_MARKS", 0)

        val subjectNameText = intent.getStringExtra("SUBJECT_NAME") ?: ""

        // Backward-compat: if caller only sent MAX_MARKS, treat it as written.
        if (writtenMaxMarks == 0 && internalMaxMarks == 0) {
            writtenMaxMarks = intent.getIntExtra("MAX_MARKS", 100)
        }

        initializeViews()
        setupToolbar()
        displaySubjectInfo(subjectNameText)
        setupRecyclerView()
        loadStudents()
    }

    // ─── Views ────────────────────────────────────────────────────────────────

    private fun initializeViews() {
        toolbar         = findViewById(R.id.toolbar)
        subjectName     = findViewById(R.id.subject_name)
        examTypeChip    = findViewById(R.id.exam_type_chip)
        marksSchemeText = findViewById(R.id.marks_scheme_text)
        studentsCount   = findViewById(R.id.students_count)
        recyclerView    = findViewById(R.id.recycler_view)
        btnSaveAll      = findViewById(R.id.btn_save_all)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Enter Marks"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun displaySubjectInfo(name: String) {
        subjectName.text  = name
        examTypeChip.text = examType

        val parts = mutableListOf<String>()
        if (writtenMaxMarks  > 0) parts.add("Written: $writtenMaxMarks")
        if (internalMaxMarks > 0) parts.add("Internal: $internalMaxMarks")
        parts.add("Total: ${writtenMaxMarks + internalMaxMarks}")
        marksSchemeText.text = parts.joinToString("  |  ")
    }

    private fun setupRecyclerView() {
        adapter = StudentMarksAdapter(
            marksList        = marksList,
            writtenMaxMarks  = writtenMaxMarks,
            internalMaxMarks = internalMaxMarks,
            onMarksChanged   = {}   // FIX 3: named + explicit lambda, no trailing ambiguity
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnSaveAll.setOnClickListener { saveAllMarks() }
    }

    // ─── Load students ────────────────────────────────────────────────────────

    private fun loadStudents() {
        showLoading(true)

        firestore.collection("classes")
            .whereEqualTo("courseId", courseId)
            .get()
            .addOnSuccessListener { classDocuments ->
                if (classDocuments.isEmpty) {
                    showLoading(false)
                    Toast.makeText(this, "No classes found for this course", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                classDocuments.forEach { classDoc ->
                    firestore.collection("students")
                        .whereEqualTo("classId", classDoc.id)
                        .get()
                        .addOnSuccessListener { studentDocuments ->
                            marksList.clear()

                            studentDocuments.forEach { studentDoc ->
                                val studentId   = studentDoc.id
                                val studentName = studentDoc.getString("name")   ?: ""
                                val rollNo      = studentDoc.getString("rollNo") ?: ""

                                loadExistingMarks(studentId) { existingMarks ->
                                    val examMarks = existingMarks ?: ExamMarks(
                                        examId           = examId,
                                        subjectId        = subjectId,
                                        studentId        = studentId,
                                        studentName      = studentName,
                                        studentRollNo    = rollNo,
                                        examType         = examType,
                                        writtenMaxMarks  = writtenMaxMarks,
                                        internalMaxMarks = internalMaxMarks,
                                        totalMaxMarks    = writtenMaxMarks + internalMaxMarks
                                    )
                                    marksList.add(examMarks)
                                    adapter.updateList(marksList)
                                    studentsCount.text = "${marksList.size} Students"
                                    showLoading(false)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            Toast.makeText(this, "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadExistingMarks(studentId: String, callback: (ExamMarks?) -> Unit) {
        firestore.collection("examMarks")
            .whereEqualTo("examId",    examId)
            .whereEqualTo("subjectId", subjectId)
            .whereEqualTo("studentId", studentId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val marks = documents.first().toObject(ExamMarks::class.java)
                    marks?.id = documents.first().id
                    callback(marks)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { callback(null) }
    }

    // ─── Save marks ───────────────────────────────────────────────────────────

    private fun saveAllMarks() {
        val marksToSave = adapter.getMarksData()

        if (marksToSave.isEmpty()) {
            Toast.makeText(this, "No marks to save", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Save Marks")
            .setMessage("Save marks for ${marksToSave.size} students?")
            .setPositiveButton("Save") { _, _ ->
                showLoading(true)
                var savedCount = 0
                val totalCount = marksToSave.size

                marksToSave.forEach { marks ->
                    // FIX 4: Local recalc() used instead of imported companion recalculate().
                    // Result is captured before .copy() so computed fields are not lost.
                    val finalMarks = marks.recalc().copy(
                        enteredBy = "Admin",   // TODO: replace with logged-in uid
                        enteredAt = System.currentTimeMillis()
                    )

                    val onSuccess: () -> Unit = {
                        savedCount++
                        if (savedCount == totalCount) {
                            showLoading(false)
                            Toast.makeText(this, "Marks saved successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    val onFailure: () -> Unit = {
                        savedCount++
                        if (savedCount == totalCount) {
                            showLoading(false)
                            Toast.makeText(this, "Some marks failed to save", Toast.LENGTH_SHORT).show()
                        }
                    }

                    if (finalMarks.id.isEmpty()) {
                        firestore.collection("examMarks")
                            .add(finalMarks)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onFailure() }
                    } else {
                        firestore.collection("examMarks").document(finalMarks.id)
                            .set(finalMarks)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onFailure() }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnSaveAll.isEnabled       = !show
    }
}