package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.FirebaseFirestore

data class CourseExamConfig(
    val courseId: String,
    val courseName: String,
    var studentStyle: String = "", // NEP, ATKT, Regular
    var examType: String = "", // Written, Practical, Internal
    var maxMarks: Int = 100
)

class ExamCreationStep2Activity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddCourse: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var emptyView: View
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private val selectedCourses = mutableListOf<CourseExamConfig>()
    private lateinit var adapter: CourseConfigAdapter

    private var examName = ""
    private var semester = 0
    private var academicYear = ""
    private var startDate = 0L
    private var endDate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_creation_step2)

        getIntentData()
        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupButtons()
    }

    private fun getIntentData() {
        examName = intent.getStringExtra("EXAM_NAME") ?: ""
        semester = intent.getIntExtra("SEMESTER", 0)
        academicYear = intent.getStringExtra("ACADEMIC_YEAR") ?: ""
        startDate = intent.getLongExtra("START_DATE", 0L)
        endDate = intent.getLongExtra("END_DATE", 0L)
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        btnAddCourse = findViewById(R.id.btn_add_course)
        btnNext = findViewById(R.id.btn_next)
        emptyView = findViewById(R.id.empty_view)
        loadingProgress = findViewById(R.id.loading_progress)
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create Exam - Step 2 of 4"
        supportActionBar?.subtitle = "Select Courses"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = CourseConfigAdapter(
            selectedCourses,
            onEditClick = { config -> editCourseConfig(config) },
            onDeleteClick = { config -> removeCourse(config) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        updateUI()
    }

    private fun setupButtons() {
        btnAddCourse.setOnClickListener {
            showCourseSelectionDialog()
        }

        btnNext.setOnClickListener {
            if (validateStep2()) {
                proceedToStep3()
            }
        }
    }

    private fun showCourseSelectionDialog() {
        showLoading(true)

        firestore.collection("courses")
            .get()
            .addOnSuccessListener { documents ->
                showLoading(false)

                val availableCourses = documents.mapNotNull { doc ->
                    val id = doc.id
                    val name = doc.getString("name") ?: doc.getString("courseName") ?: ""
                    if (name.isNotEmpty() && !selectedCourses.any { it.courseId == id }) {
                        Pair(id, name)
                    } else null
                }.sortedBy { it.second }

                if (availableCourses.isEmpty()) {
                    Toast.makeText(this, "All courses already added", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val courseNames = availableCourses.map { it.second }.toTypedArray()

                AlertDialog.Builder(this, R.style.CustomAlertDialog)
                    .setTitle("Select Course")
                    .setItems(courseNames) { _, which ->
                        val selected = availableCourses[which]
                        showConfigurationDialog(selected.first, selected.second)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showConfigurationDialog(courseId: String, courseName: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_course_exam_config, null)

        val studentStyleGroup: ChipGroup = dialogView.findViewById(R.id.student_style_group)
        val examTypeGroup: ChipGroup = dialogView.findViewById(R.id.exam_type_group)
        val maxMarksInput: EditText = dialogView.findViewById(R.id.max_marks_input)

        // Pre-fill default values
        maxMarksInput.setText("100")

        AlertDialog.Builder(this, R.style.CustomAlertDialog_Schedule_Exam)
            .setTitle("Configure: $courseName")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val selectedStyleChip = studentStyleGroup.findViewById<Chip>(studentStyleGroup.checkedChipId)
                val selectedTypeChip = examTypeGroup.findViewById<Chip>(examTypeGroup.checkedChipId)
                val studentStyle = selectedStyleChip?.text?.toString() ?: ""
                val examType = selectedTypeChip?.text?.toString() ?: ""
                val maxMarks = maxMarksInput.text.toString().toIntOrNull() ?: 100

                if (studentStyle.isEmpty()) {
                    Toast.makeText(this, "Please select student style", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (examType.isEmpty()) {
                    Toast.makeText(this, "Please select exam type", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val config = CourseExamConfig(
                    courseId = courseId,
                    courseName = courseName,
                    studentStyle = studentStyle,
                    examType = examType,
                    maxMarks = maxMarks
                )

                selectedCourses.add(config)
                adapter.notifyItemInserted(selectedCourses.size - 1)
                updateUI()

                Toast.makeText(this, "$courseName added successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editCourseConfig(config: CourseExamConfig) {
        val index = selectedCourses.indexOf(config)
        if (index == -1) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_course_exam_config, null)

        val studentStyleGroup: ChipGroup = dialogView.findViewById(R.id.student_style_group)
        val examTypeGroup: ChipGroup = dialogView.findViewById(R.id.exam_type_group)
        val maxMarksInput: EditText = dialogView.findViewById(R.id.max_marks_input)

        // Pre-select existing values
        maxMarksInput.setText(config.maxMarks.toString())

        // Select student style chip
        for (i in 0 until studentStyleGroup.childCount) {
            val chip = studentStyleGroup.getChildAt(i) as? Chip
            if (chip?.text.toString() == config.studentStyle) {
                chip?.isChecked = true
                break
            }
        }

        // Select exam type chip
        for (i in 0 until examTypeGroup.childCount) {
            val chip = examTypeGroup.getChildAt(i) as? Chip
            if (chip?.text.toString() == config.examType) {
                chip?.isChecked = true
                break
            }
        }

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Edit: ${config.courseName}")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val selectedStyleChip = studentStyleGroup.findViewById<Chip>(studentStyleGroup.checkedChipId)
                val selectedTypeChip = examTypeGroup.findViewById<Chip>(examTypeGroup.checkedChipId)

                config.studentStyle = selectedStyleChip?.text?.toString() ?: config.studentStyle
                config.examType = selectedTypeChip?.text?.toString() ?: config.examType
                config.maxMarks = maxMarksInput.text.toString().toIntOrNull() ?: config.maxMarks

                adapter.notifyItemChanged(index)
                Toast.makeText(this, "Configuration updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeCourse(config: CourseExamConfig) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Remove Course")
            .setMessage("Remove ${config.courseName} from exam?")
            .setPositiveButton("Remove") { _, _ ->
                val index = selectedCourses.indexOf(config)
                selectedCourses.remove(config)
                adapter.notifyItemRemoved(index)
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateStep2(): Boolean {
        if (selectedCourses.isEmpty()) {
            Toast.makeText(this, "Please add at least one course", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun proceedToStep3() {
        val intent = Intent(this, ExamCreationStep3Activity::class.java)
        intent.putExtra("EXAM_NAME", examName)
        intent.putExtra("SEMESTER", semester)
        intent.putExtra("ACADEMIC_YEAR", academicYear)
        intent.putExtra("START_DATE", startDate)
        intent.putExtra("END_DATE", endDate)

        // Pass course configurations as JSON
        val coursesJson = com.google.gson.Gson().toJson(selectedCourses)
        intent.putExtra("COURSES_CONFIG", coursesJson)

        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateUI() {
        val hasCourses = selectedCourses.isNotEmpty()
        emptyView.visibility = if (!hasCourses) View.VISIBLE else View.GONE
        btnNext.visibility = if (hasCourses) View.VISIBLE else View.GONE
    }
}

// Adapter for course configuration
class CourseConfigAdapter(
    private val courses: MutableList<CourseExamConfig>,
    private val onEditClick: (CourseExamConfig) -> Unit,
    private val onDeleteClick: (CourseExamConfig) -> Unit
) : RecyclerView.Adapter<CourseConfigAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val courseName: TextView = view.findViewById(R.id.course_name)
        val configDetails: TextView = view.findViewById(R.id.config_details)
        val btnEdit: ImageButton = view.findViewById(R.id.btn_edit)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course_config, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = courses[position]
        holder.courseName.text = config.courseName
        holder.configDetails.text = "${config.studentStyle} • ${config.examType} • ${config.maxMarks} marks"

        holder.btnEdit.setOnClickListener { onEditClick(config) }
        holder.btnDelete.setOnClickListener { onDeleteClick(config) }
    }

    override fun getItemCount() = courses.size
}