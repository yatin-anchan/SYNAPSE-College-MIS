package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.HodMarksModerationAdapter
import com.projectbyyatin.synapsemis.models.ExamMarks
import com.projectbyyatin.synapsemis.models.ExamMarks.Companion.calculateGrade

// FIX 1: Removed "import ExamMarks.Companion.recalculate"
// That companion-extension import caused the compiler to use named-group regex
// internally when resolving import scope, triggering:
//   • 'infix' modifier required on FirNamedFunctionSymbol kotlin/collections/get
//   • Call requires API level 26 (Matcher#start via MatchGroupCollection#get(String))
// Replaced with a local recalc() extension (see below).

class HodMarksModerationActivity : AppCompatActivity() {

    companion object { private const val TAG = "HodMarksModeration" }

    private lateinit var toolbar: Toolbar
    private lateinit var examInfoCard: MaterialCardView
    private lateinit var examNameText: TextView
    private lateinit var subjectNameText: TextView
    private lateinit var examTypeChip: TextView
    private lateinit var marksSchemeText: TextView
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var lockIcon: ImageView

    private lateinit var filterCard: MaterialCardView
    private lateinit var filterHeaderLayout: LinearLayout
    private lateinit var filterToggleIcon: ImageView
    private lateinit var filterContentLayout: LinearLayout
    private lateinit var filterMarksMin: TextInputEditText
    private lateinit var filterMarksMax: TextInputEditText
    private lateinit var filterRollNo: TextInputEditText
    private lateinit var filterName: TextInputEditText
    private lateinit var filterAbsentCheckbox: CheckBox
    private lateinit var filterModifiedCheckbox: CheckBox
    private lateinit var sortSpinner: Spinner
    private lateinit var filterButton: MaterialButton
    private lateinit var clearFilterButton: MaterialButton

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var bottomButtonsContainer: View
    private lateinit var saveDraftButton: MaterialButton
    private lateinit var submitButton: MaterialButton

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: HodMarksModerationAdapter

    private var examId      = ""
    private var subjectId   = ""
    private var subjectName = ""
    private var courseId    = ""
    private var examName    = ""
    private var isModerated = false

    private var examType: String = "Written"
    private var writtenMaxMarks: Int = 0
    private var internalMaxMarks: Int = 0
    private var maxMarks: Int = 100

    private val originalMarks  = mutableListOf<ExamMarks>()
    private val moderatedMarks = mutableListOf<ExamMarks>()
    private val filteredMarks  = mutableListOf<ExamMarks>()

    private var isFilterExpanded = false

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 2: Local recalc() with explicit receiver + return type.
    // The companion extension returns a NEW copy (data class .copy()).
    // Original code called .recalculate() but never stored the result, so
    // totalMarksObtained / percentage / grade were always 0 / blank.
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
        setContentView(R.layout.activity_hod_marks_moderation)

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

        if (examId.isBlank() || subjectId.isBlank() || courseId.isBlank()) {
            Toast.makeText(this, "Missing exam/subject data", Toast.LENGTH_LONG).show()
            finish(); return
        }

        firestore = FirebaseFirestore.getInstance()
        auth      = FirebaseAuth.getInstance()

        initializeViews()
        setupToolbar()
        setupBackPressHandler()
        setupCollapsibleFilter()
        setupRecyclerView()
        setupFilters()
        setupButtons()
        setupKeyboardListener()
        loadExamData()
    }

    // ─── UI setup ─────────────────────────────────────────────────────────────

    private fun initializeViews() {
        toolbar             = findViewById(R.id.toolbar)
        examInfoCard        = findViewById(R.id.exam_info_card)
        examNameText        = findViewById(R.id.exam_name)
        subjectNameText     = findViewById(R.id.subject_name)
        examTypeChip        = findViewById(R.id.exam_type_chip)
        marksSchemeText     = findViewById(R.id.marks_scheme_text)
        statusText          = findViewById(R.id.status_text)
        statsText           = findViewById(R.id.stats_text)
        lockIcon            = findViewById(R.id.lock_icon)

        filterCard            = findViewById(R.id.filter_card)
        filterHeaderLayout    = findViewById(R.id.filter_header_layout)
        filterToggleIcon      = findViewById(R.id.filter_toggle_icon)
        filterContentLayout   = findViewById(R.id.filter_content_layout)
        filterMarksMin        = findViewById(R.id.filter_marks_min)
        filterMarksMax        = findViewById(R.id.filter_marks_max)
        filterRollNo          = findViewById(R.id.filter_roll_no)
        filterName            = findViewById(R.id.filter_name)
        filterAbsentCheckbox  = findViewById(R.id.filter_absent_checkbox)
        filterModifiedCheckbox = findViewById(R.id.filter_modified_checkbox)
        sortSpinner           = findViewById(R.id.sort_spinner)
        filterButton          = findViewById(R.id.filter_button)
        clearFilterButton     = findViewById(R.id.clear_filter_button)

        recyclerView           = findViewById(R.id.recycler_view)
        loadingProgress        = findViewById(R.id.loading_progress)
        emptyView              = findViewById(R.id.empty_view)
        emptyText              = findViewById(R.id.empty_text)
        bottomButtonsContainer = findViewById(R.id.bottom_buttons_container)
        saveDraftButton        = findViewById(R.id.save_draft_button)
        submitButton           = findViewById(R.id.submit_button)

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
        supportActionBar?.title = "Moderate Marks"
        toolbar.setNavigationOnClickListener { handleBackPress() }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBackPress() }
        })
    }

    private fun setupCollapsibleFilter() {
        filterHeaderLayout.setOnClickListener { toggleFilterSection() }
        filterContentLayout.visibility = View.GONE
    }

    private fun toggleFilterSection() {
        isFilterExpanded = !isFilterExpanded
        filterContentLayout.visibility = if (isFilterExpanded) View.VISIBLE else View.GONE
        filterToggleIcon.animate().rotation(if (isFilterExpanded) 180f else 0f).setDuration(300).start()
    }

    private fun setupRecyclerView() {
        // FIX 3: Named + explicit lambda — removes type-inference ambiguity.
        adapter = HodMarksModerationAdapter(
            moderatedMarks   = moderatedMarks,
            originalMarks    = originalMarks,
            writtenMaxMarks  = writtenMaxMarks,
            internalMaxMarks = internalMaxMarks,
            onStatsUpdate    = { updateStats() }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFilters() {
        val sortOptions = arrayOf(
            "Roll No ↑", "Roll No ↓", "Name A-Z", "Name Z-A",
            "Marks ↑", "Marks ↓", "Modified First", "Unmodified First"
        )
        sortSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortOptions)
        filterButton.setOnClickListener      { applyFilters() }
        clearFilterButton.setOnClickListener { clearFilters() }
    }

    private fun setupButtons() {
        saveDraftButton.setOnClickListener { saveModerationDraft() }
        submitButton.setOnClickListener    { showSubmitConfirmation() }
        updateButtonStates()
    }

    private fun setupKeyboardListener() {
        val rootView = window.decorView.rootView
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        rootView.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {

                private var wasKeyboardOpen = false

                override fun onGlobalLayout() {
                    val rect = android.graphics.Rect()
                    rootView.getWindowVisibleDisplayFrame(rect)
                    val isKeyboardOpen = (rootView.height - rect.bottom) > rootView.height * 0.15

                    if (isKeyboardOpen == wasKeyboardOpen) return
                    wasKeyboardOpen = isKeyboardOpen

                    if (isKeyboardOpen) {
                        // Hide bottom buttons so they don't overlap the list
                        bottomButtonsContainer.animate()
                            .translationY(bottomButtonsContainer.height.toFloat())
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction { bottomButtonsContainer.visibility = android.view.View.GONE }
                            .start()

                        // Scroll the currently focused EditText into view after a
                        // short delay so the keyboard has finished animating in.
                        recyclerView.postDelayed({
                            val focused = currentFocus
                            if (focused != null) {
                                // Find which RecyclerView child holds the focus
                                for (i in 0 until recyclerView.childCount) {
                                    val child = recyclerView.getChildAt(i)
                                    if (child.findFocus() != null) {
                                        recyclerView.smoothScrollToPosition(
                                            recyclerView.getChildAdapterPosition(child)
                                        )
                                        break
                                    }
                                }
                            }
                        }, 300)

                    } else {
                        // Show bottom buttons again when keyboard closes
                        bottomButtonsContainer.visibility = android.view.View.VISIBLE
                        bottomButtonsContainer.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                }
            }
        )
    }

    private fun updateButtonStates() {
        if (isModerated) {
            saveDraftButton.isEnabled = false; submitButton.isEnabled = false
            saveDraftButton.alpha = 0.5f;      submitButton.alpha = 0.5f
            lockIcon.visibility = View.VISIBLE
            statusText.text = "🔒 Moderated & Locked"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            saveDraftButton.isEnabled = true; submitButton.isEnabled = true
            saveDraftButton.alpha = 1f;       submitButton.alpha = 1f
            lockIcon.visibility = View.GONE
            statusText.text = "⚠️ Not Moderated"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        }
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    private fun loadExamData() {
        showLoading(true)
        firestore.collection("exams").document(examId).get()
            .addOnSuccessListener { examDoc ->
                examName = examDoc.getString("examName") ?: "Exam"

                @Suppress("UNCHECKED_CAST")
                isModerated = ((examDoc.get("subjectsMarksStatus") as? Map<*, *>)
                    ?.get(subjectId) as? Map<*, *>)
                    ?.get("moderated") as? Boolean == true

                examNameText.text    = examName
                subjectNameText.text = subjectName
                updateButtonStates()
                loadSubmittedMarks(examDoc)
            }
            .addOnFailureListener { showError("Failed to load exam data") }
    }

    private fun loadSubmittedMarks(examDoc: com.google.firebase.firestore.DocumentSnapshot) {
        firestore.collection("examMarks")
            .whereEqualTo("examId",    examId)
            .whereEqualTo("subjectId", subjectId)
            .get()
            .addOnSuccessListener { marksDocs ->
                originalMarks.clear(); moderatedMarks.clear()

                marksDocs.documents.forEach { doc ->
                    val m = doc.toObject(ExamMarks::class.java)?.apply {
                        id               = doc.id
                        writtenMaxMarks  = this@HodMarksModerationActivity.writtenMaxMarks
                        internalMaxMarks = this@HodMarksModerationActivity.internalMaxMarks
                        totalMaxMarks    = this@HodMarksModerationActivity.maxMarks
                    }
                    if (m != null) {
                        originalMarks.add(m.copy())
                        moderatedMarks.add(m.copy())
                    }
                }

                Log.d(TAG, "examMarks found: ${originalMarks.size}")
                if (originalMarks.isNotEmpty()) loadAllStudentsAndMerge()
                else loadFromDraftFallback(examDoc)
            }
            .addOnFailureListener { loadFromDraftFallback(examDoc) }
    }

    private fun loadFromDraftFallback(examDoc: com.google.firebase.firestore.DocumentSnapshot) {
        // FIX 4: Break the deep Firestore map chain into two explicitly-typed
        // intermediate variables. The original code had:
        //
        //   val studentsMapNew = ((...) as? Map<*,*>)
        //       ?.get(subjectId) as? Map<*,*>
        //   ?.get("students") as? Map<*,*>   ← orphaned line at column 0!
        //
        // Kotlin's parser saw the chain as complete after the first `as? Map<*,*>`
        // and treated `?.get("students")` as a brand-new statement (invalid), which
        // cascaded into "Incomplete code", "infix modifier required", and the
        // API-26 Matcher#start error (named-group regex used internally to resolve
        // the ambiguous operator). Splitting into subjectNode → studentsMapNew
        // gives the compiler a fully-typed value at every step.
        @Suppress("UNCHECKED_CAST")
        val subjectNode: Map<*, *>? =
            ((examDoc.get("marksDraft") as? Map<*, *>)
                ?.get("subjects") as? Map<*, *>)
                ?.get(subjectId) as? Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val studentsMapNew: Map<*, *>? = subjectNode?.get("students") as? Map<*, *>

        // Legacy flat-format fallback
        @Suppress("UNCHECKED_CAST")
        val studentsMapLegacy: Map<*, *>? =
            (examDoc.get("marksDraft") as? Map<*, *>)?.get("students") as? Map<*, *>

        val studentsMap: Map<*, *>? = studentsMapNew ?: studentsMapLegacy

        Log.d(TAG, "Draft fallback — students: ${studentsMap?.size ?: 0}")

        firestore.collection("students")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { studentDocs ->
                originalMarks.clear(); moderatedMarks.clear()

                studentDocs.documents.forEach { doc ->
                    val studentId   = doc.id
                    val studentName = doc.getString("fullName")   ?: "Unknown"
                    val rollNo      = doc.getString("rollNumber") ?: doc.id.takeLast(6).padStart(6, '0')

                    @Suppress("UNCHECKED_CAST")
                    val draft: Map<*, *>? = studentsMap?.get(studentId) as? Map<*, *>

                    // FIX 5: Explicit (String) -> Float return type on numFloat.
                    // Original had `fun numFloat(key: String) = when { ... }` which
                    // the compiler couldn't infer, causing "Cannot infer type /
                    // Expecting function type" errors.
                    val numFloat: (String) -> Float = { key ->
                        when (val v = draft?.get(key)) {
                            is Double -> v.toFloat()
                            is Float  -> v
                            is Long   -> v.toFloat()
                            is Int    -> v.toFloat()
                            else      -> 0f
                        }
                    }

                    val dWritten  = numFloat("writtenMarksObtained")
                        .let { if (it == 0f) numFloat("marksObtained") else it }
                    val dInternal = numFloat("internalMarksObtained")
                    val dAbsent   = draft?.get("isAbsent") as? Boolean ?: false

                    // FIX 6: Capture recalc() result — original discarded the copy.
                    val m = ExamMarks(
                        examId                = examId,
                        subjectId             = subjectId,
                        subjectName           = subjectName,
                        studentId             = studentId,
                        studentName           = studentName,
                        studentRollNo         = rollNo,
                        courseId              = courseId,
                        examType              = examType,
                        writtenMaxMarks       = writtenMaxMarks,
                        internalMaxMarks      = internalMaxMarks,
                        totalMaxMarks         = maxMarks,
                        writtenMarksObtained  = dWritten,
                        internalMarksObtained = dInternal,
                        isAbsent              = dAbsent
                    ).recalc()   // result captured ✓

                    originalMarks.add(m.copy())
                    moderatedMarks.add(m.copy())
                }

                finishLoading()
            }
            .addOnFailureListener { showError("Failed to load student data") }
    }

    private fun loadAllStudentsAndMerge() {
        firestore.collection("students")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { studentDocs ->
                val allStudents = studentDocs.documents.map { doc ->
                    StudentData(
                        id     = doc.id,
                        name   = doc.getString("fullName")   ?: "Unknown",
                        rollNo = doc.getString("rollNumber") ?: doc.id.takeLast(6).padStart(6, '0')
                    )
                }
                val complete = allStudents.map { s ->
                    moderatedMarks.find { it.studentId == s.id } ?: ExamMarks(
                        examId           = examId,
                        subjectId        = subjectId,
                        subjectName      = subjectName,
                        studentId        = s.id,
                        studentName      = s.name,
                        studentRollNo    = s.rollNo,
                        courseId         = courseId,
                        examType         = examType,
                        writtenMaxMarks  = writtenMaxMarks,
                        internalMaxMarks = internalMaxMarks,
                        totalMaxMarks    = maxMarks
                    )
                }
                originalMarks.clear();  originalMarks.addAll(complete.map { it.copy() })
                moderatedMarks.clear(); moderatedMarks.addAll(complete)
                finishLoading()
            }
            .addOnFailureListener { showError("Failed to load students") }
    }

    private fun finishLoading() {
        moderatedMarks.sortBy { it.studentRollNo }
        originalMarks.sortBy  { it.studentRollNo }
        filteredMarks.clear(); filteredMarks.addAll(moderatedMarks)
        adapter.updateLists(moderatedMarks, originalMarks)
        updateStats()
        showLoading(false)
    }

    // ─── Filters ──────────────────────────────────────────────────────────────

    private fun applyFilters() {
        val minM         = filterMarksMin.text.toString().toFloatOrNull()
        val maxM         = filterMarksMax.text.toString().toFloatOrNull()
        val rollF        = filterRollNo.text.toString().lowercase().trim()
        val nameF        = filterName.text.toString().lowercase().trim()
        val absentOnly   = filterAbsentCheckbox.isChecked
        val modifiedOnly = filterModifiedCheckbox.isChecked

        filteredMarks.clear()
        filteredMarks.addAll(moderatedMarks.filter { m ->
            val totalObt = m.totalMarksObtained
            val inRange = when {
                minM != null && maxM != null -> totalObt in minM..maxM
                minM != null -> totalObt >= minM
                maxM != null -> totalObt <= maxM
                else         -> true
            }
            val orig     = originalMarks.find { it.studentId == m.studentId }
            val modified = orig?.writtenMarksObtained  != m.writtenMarksObtained ||
                    orig?.internalMarksObtained != m.internalMarksObtained ||
                    orig?.isAbsent != m.isAbsent
            inRange &&
                    (rollF.isEmpty() || m.studentRollNo.lowercase().contains(rollF)) &&
                    (nameF.isEmpty() || m.studentName.lowercase().contains(nameF))  &&
                    (!absentOnly   || m.isAbsent)  &&
                    (!modifiedOnly || modified)
        })

        // FIX 7: Renamed inner lambda parameter from `it` to `m` to avoid
        // shadowing the outer `it` in sortByDescending/sortBy lambdas, which
        // caused type-resolution failures on the find{} call inside.
        when (sortSpinner.selectedItemPosition) {
            0 -> filteredMarks.sortBy           { it.studentRollNo }
            1 -> filteredMarks.sortByDescending { it.studentRollNo }
            2 -> filteredMarks.sortBy           { it.studentName }
            3 -> filteredMarks.sortByDescending { it.studentName }
            4 -> filteredMarks.sortBy           { it.totalMarksObtained }
            5 -> filteredMarks.sortByDescending { it.totalMarksObtained }
            6 -> filteredMarks.sortByDescending { m ->
                val o = originalMarks.find { it.studentId == m.studentId }
                o?.writtenMarksObtained  != m.writtenMarksObtained ||
                        o?.internalMarksObtained != m.internalMarksObtained
            }
            7 -> filteredMarks.sortBy { m ->
                val o = originalMarks.find { it.studentId == m.studentId }
                o?.writtenMarksObtained  != m.writtenMarksObtained ||
                        o?.internalMarksObtained != m.internalMarksObtained
            }
        }

        adapter.notifyDataSetChanged()
        updateStats()
        Toast.makeText(this, "Showing ${filteredMarks.size} students", Toast.LENGTH_SHORT).show()
    }

    private fun clearFilters() {
        filterMarksMin.text?.clear(); filterMarksMax.text?.clear()
        filterRollNo.text?.clear();   filterName.text?.clear()
        filterAbsentCheckbox.isChecked   = false
        filterModifiedCheckbox.isChecked = false
        sortSpinner.setSelection(0)
        filteredMarks.clear(); filteredMarks.addAll(moderatedMarks)
        filteredMarks.sortBy { it.studentRollNo }
        adapter.notifyDataSetChanged()
        updateStats()
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    private fun updateStats() {
        val total    = moderatedMarks.size
        val modified = moderatedMarks.count {
            val orig = originalMarks.find { o -> o.studentId == it.studentId }
            orig?.writtenMarksObtained  != it.writtenMarksObtained  ||
                    orig?.internalMarksObtained != it.internalMarksObtained ||
                    orig?.isAbsent != it.isAbsent
        }
        val absent = moderatedMarks.count { it.isAbsent }
        statsText.text = "$total students  |  $modified modified  |  $absent absent"
    }

    // ─── Save / Submit ────────────────────────────────────────────────────────

    private fun saveModerationDraft() {
        if (isModerated) {
            Toast.makeText(this, "This subject is locked", Toast.LENGTH_SHORT).show(); return
        }

        val batch = firestore.batch()
        var saveCount = 0

        moderatedMarks.forEach { m ->
            val orig = originalMarks.find { it.studentId == m.studentId }
            val changed = orig?.writtenMarksObtained  != m.writtenMarksObtained ||
                    orig?.internalMarksObtained != m.internalMarksObtained ||
                    orig?.isAbsent != m.isAbsent

            if (changed && m.id.isNotBlank()) {
                // FIX 8: Capture recalc() into a val before reading its fields.
                val recalc = m.recalc()
                batch.update(
                    firestore.collection("examMarks").document(m.id),
                    mapOf(
                        "writtenMarksObtained"  to recalc.writtenMarksObtained,
                        "internalMarksObtained" to recalc.internalMarksObtained,
                        "totalMarksObtained"    to recalc.totalMarksObtained,
                        "percentage"            to recalc.percentage,
                        "cgpi"                  to recalc.cgpi,
                        "grade"                 to recalc.grade,
                        "isAbsent"              to recalc.isAbsent,
                        "moderatedBy"           to (auth.currentUser?.uid ?: ""),
                        "moderatedByName"       to (auth.currentUser?.displayName ?: "HOD"),
                        "updatedAt"             to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                saveCount++
            }
        }

        if (saveCount == 0) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show(); return
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Draft saved: $saveCount marks updated", Toast.LENGTH_SHORT).show()
                statusText.text = "💾 Draft Saved"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save draft", e)
                Toast.makeText(this, "Failed to save draft", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSubmitConfirmation() {
        if (isModerated) {
            Toast.makeText(this, "Already moderated and locked", Toast.LENGTH_LONG).show(); return
        }

        val modifiedCount = moderatedMarks.count {
            val orig = originalMarks.find { o -> o.studentId == it.studentId }
            orig?.writtenMarksObtained  != it.writtenMarksObtained ||
                    orig?.internalMarksObtained != it.internalMarksObtained ||
                    orig?.isAbsent != it.isAbsent
        }

        AlertDialog.Builder(this)
            .setTitle("Submit Moderation")
            .setMessage(
                "You have modified $modifiedCount marks.\n\n" +
                        "✅ Modified marks will be OVERWRITTEN\n" +
                        "✅ Unmodified marks will remain AS-IS\n" +
                        "🔒 This subject will be LOCKED after submission\n\nContinue?"
            )
            .setPositiveButton("Submit & Lock") { _, _ -> submitModeration() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitModeration() {
        val batch   = firestore.batch()
        val hodId   = auth.currentUser?.uid ?: ""
        val hodName = auth.currentUser?.displayName ?: "HOD"
        val now     = System.currentTimeMillis()

        moderatedMarks.forEach { m ->
            val orig = originalMarks.find { it.studentId == m.studentId }
            val changed = orig?.writtenMarksObtained  != m.writtenMarksObtained ||
                    orig?.internalMarksObtained != m.internalMarksObtained ||
                    orig?.isAbsent != m.isAbsent

            if (changed && m.id.isNotBlank()) {
                // FIX 9: recalc() result captured before .copy() chain.
                val recalc = m.recalc()
                batch.set(
                    firestore.collection("examMarks").document(m.id),
                    recalc.copy(
                        isModerated           = true,
                        moderatedBy           = hodId,
                        moderatedByName       = hodName,
                        moderatedAt           = now,
                        moderationSubmittedAt = now,
                        previousWrittenMarks  = orig?.writtenMarksObtained  ?: 0f,
                        previousInternalMarks = orig?.internalMarksObtained ?: 0f
                    )
                )
            }
        }

        batch.update(
            firestore.collection("exams").document(examId),
            mapOf(
                "subjectsMarksStatus.$subjectId.moderated"       to true,
                "subjectsMarksStatus.$subjectId.moderatedBy"     to hodId,
                "subjectsMarksStatus.$subjectId.moderatedAt"     to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "subjectsMarksStatus.$subjectId.readyForPublish" to true
            )
        )

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Moderation submitted & locked", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to submit", e)
                Toast.makeText(this, "❌ Failed to submit moderation", Toast.LENGTH_LONG).show()
            }
    }

    // ─── Back / Helpers ───────────────────────────────────────────────────────

    private fun handleBackPress() {
        val hasChanges = moderatedMarks.any {
            val orig = originalMarks.find { o -> o.studentId == it.studentId }
            orig?.writtenMarksObtained  != it.writtenMarksObtained ||
                    orig?.internalMarksObtained != it.internalMarksObtained ||
                    orig?.isAbsent != it.isAbsent
        }
        if (hasChanges && !isModerated) {
            AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("Save draft before leaving?")
                .setPositiveButton("Save Draft") { _, _ -> saveModerationDraft(); finish() }
                .setNegativeButton("Discard")    { _, _ -> finish() }
                .setNeutralButton("Cancel", null)
                .show()
        } else finish()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility    = if (show) View.GONE    else View.VISIBLE
    }

    private fun showError(message: String) {
        showLoading(false)
        emptyView.visibility = View.VISIBLE
        emptyText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    data class StudentData(val id: String, val name: String, val rollNo: String)
}