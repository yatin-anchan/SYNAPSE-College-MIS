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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.InvigilatorsAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import com.projectbyyatin.synapsemis.models.Invigilator

class AssignInvigilatorsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var subjectName: TextView
    private lateinit var examDateText: TextView
    private lateinit var examTimeText: TextView
    private lateinit var venueText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var fab: FloatingActionButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: InvigilatorsAdapter
    private var invigilatorsList = mutableListOf<Invigilator>()

    private var examId: String = ""
    private var subjectId: String = ""
    private var examData: Exam? = null
    private var subjectData: ExamSubject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assign_invigilators)

        examId = intent.getStringExtra("EXAM_ID") ?: ""
        subjectId = intent.getStringExtra("SUBJECT_ID") ?: ""
        val subjectNameText = intent.getStringExtra("SUBJECT_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadExamData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        subjectName = findViewById(R.id.subject_name)
        examDateText = findViewById(R.id.exam_date_text)
        examTimeText = findViewById(R.id.exam_time_text)
        venueText = findViewById(R.id.venue_text)
        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty_view)
        fab = findViewById(R.id.fab)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Assign Invigilators"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = InvigilatorsAdapter(
            invigilatorsList,
            onRemoveClick = { invigilator -> removeInvigilator(invigilator) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFab() {
        fab.setOnClickListener {
            showAddInvigilatorDialog()
        }
    }

    private fun loadExamData() {
        showLoading(true)

        firestore.collection("exams").document(examId)
            .get()
            .addOnSuccessListener { document ->
                examData = document.toObject(Exam::class.java)
                examData?.let { exam ->
                    exam.id = document.id

                    // Find subject data
                    subjectData = exam.subjects.find { it.subjectId == subjectId }
                    subjectData?.let { subject ->
                        displaySubjectData(subject)
                        invigilatorsList.clear()
                        invigilatorsList.addAll(subject.invigilators)
                        adapter.updateList(invigilatorsList)
                        updateEmptyView()
                    }
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displaySubjectData(subject: ExamSubject) {
        subjectName.text = subject.subjectName
        examDateText.text = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(subject.examDate))
        examTimeText.text = "${subject.startTime} - ${subject.endTime}"
        venueText.text = subject.venue
    }

    private fun showAddInvigilatorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_invigilator, null)

        val teacherDropdown: AutoCompleteTextView = dialogView.findViewById(R.id.teacher_dropdown)
        val roleDropdown: AutoCompleteTextView = dialogView.findViewById(R.id.role_dropdown)

        var selectedTeacherId = ""
        var selectedTeacherName = ""
        var selectedRole = "invigilator"

        // Load teachers
        loadTeachers(teacherDropdown) { teacherId, teacherName ->
            selectedTeacherId = teacherId
            selectedTeacherName = teacherName
        }

        // Role dropdown
        val roles = listOf("Invigilator", "Supervisor")
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles)
        roleDropdown.setAdapter(roleAdapter)
        roleDropdown.setText("Invigilator", false)

        roleDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedRole = if (position == 0) "invigilator" else "supervisor"
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Add Invigilator")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                if (selectedTeacherId.isEmpty()) {
                    Toast.makeText(this, "Please select a teacher", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Check if already assigned
                if (invigilatorsList.any { it.teacherId == selectedTeacherId }) {
                    Toast.makeText(this, "Teacher already assigned", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val invigilator = Invigilator(
                    teacherId = selectedTeacherId,
                    teacherName = selectedTeacherName,
                    role = selectedRole,
                    assignedAt = System.currentTimeMillis()
                )

                addInvigilator(invigilator)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun loadTeachers(dropdown: AutoCompleteTextView, callback: (String, String) -> Unit) {
        firestore.collection("teachers")
            .get()
            .addOnSuccessListener { documents ->
                val teacherMap = mutableMapOf<String, String>()

                documents.forEach { doc ->
                    val name = doc.getString("name") ?: ""
                    val id = doc.id
                    teacherMap[name] = id
                }

                val teacherNames = teacherMap.keys.toList()
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teacherNames)
                dropdown.setAdapter(adapter)

                dropdown.setOnItemClickListener { _, _, position, _ ->
                    val selected = teacherNames[position]
                    val teacherId = teacherMap[selected] ?: ""
                    callback(teacherId, selected)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading teachers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addInvigilator(invigilator: Invigilator) {
        showLoading(true)

        examData?.let { exam ->
            // Update subject's invigilators list
            val updatedSubjects = exam.subjects.map { subject ->
                if (subject.subjectId == subjectId) {
                    subject.copy(invigilators = subject.invigilators + invigilator)
                } else {
                    subject
                }
            }

            firestore.collection("exams").document(examId)
                .update("subjects", updatedSubjects)
                .addOnSuccessListener {
                    Toast.makeText(this, "Invigilator assigned successfully", Toast.LENGTH_SHORT).show()
                    loadExamData()
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun removeInvigilator(invigilator: Invigilator) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Remove Invigilator")
            .setMessage("Remove ${invigilator.teacherName} from this exam?")
            .setPositiveButton("Remove") { _, _ ->
                showLoading(true)

                examData?.let { exam ->
                    val updatedSubjects = exam.subjects.map { subject ->
                        if (subject.subjectId == subjectId) {
                            subject.copy(invigilators = subject.invigilators.filter { it.teacherId != invigilator.teacherId })
                        } else {
                            subject
                        }
                    }

                    firestore.collection("exams").document(examId)
                        .update("subjects", updatedSubjects)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Invigilator removed", Toast.LENGTH_SHORT).show()
                            loadExamData()
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        fab.isEnabled = !show
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (invigilatorsList.isEmpty()) View.VISIBLE else View.GONE
    }
}
