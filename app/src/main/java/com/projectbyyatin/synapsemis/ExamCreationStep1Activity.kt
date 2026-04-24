package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class ExamCreationStep1Activity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examNameInput: TextInputEditText
    private lateinit var semesterDropdown: AutoCompleteTextView
    private lateinit var academicYearInput: TextInputEditText
    private lateinit var startDateText: TextView
    private lateinit var endDateText: TextView
    private lateinit var btnSelectStartDate: MaterialButton
    private lateinit var btnSelectEndDate: MaterialButton
    private lateinit var btnNext: MaterialButton

    private var selectedSemester: Int = 0
    private var startDate: Long = 0L
    private var endDate: Long = 0L
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_creation_step1)

        initializeViews()
        setupToolbar()
        setupSemesterDropdown()
        setDefaultAcademicYear()
        setupButtons()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        examNameInput = findViewById(R.id.exam_name_input)
        semesterDropdown = findViewById(R.id.semester_dropdown)
        academicYearInput = findViewById(R.id.academic_year_input)
        startDateText = findViewById(R.id.start_date_text)
        endDateText = findViewById(R.id.end_date_text)
        btnSelectStartDate = findViewById(R.id.btn_select_start_date)
        btnSelectEndDate = findViewById(R.id.btn_select_end_date)
        btnNext = findViewById(R.id.btn_next)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create Exam - Step 1 of 4"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupSemesterDropdown() {
        val semesters = (1..8).map { "Semester $it" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, semesters)
        semesterDropdown.setAdapter(adapter)

        semesterDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedSemester = position + 1
        }
    }

    private fun setDefaultAcademicYear() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        val academicYear = if (currentMonth >= 6) {
            "$currentYear-${currentYear + 1}"
        } else {
            "${currentYear - 1}-$currentYear"
        }

        academicYearInput.setText(academicYear)
    }

    private fun setupButtons() {
        btnSelectStartDate.setOnClickListener {
            showDatePicker { selectedDate ->
                startDate = selectedDate
                startDateText.text = dateFormat.format(Date(selectedDate))
                startDateText.visibility = View.VISIBLE

                // Reset end date if it's before new start date
                if (endDate > 0 && endDate < startDate) {
                    endDate = 0L
                    endDateText.text = "Not Selected"
                }
            }
        }

        btnSelectEndDate.setOnClickListener {
            if (startDate == 0L) {
                Toast.makeText(this, "Please select start date first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showDatePicker(minDate = startDate) { selectedDate ->
                endDate = selectedDate
                endDateText.text = dateFormat.format(Date(selectedDate))
                endDateText.visibility = View.VISIBLE
            }
        }

        btnNext.setOnClickListener {
            if (validateStep1()) {
                proceedToStep2()
            }
        }
    }

    private fun showDatePicker(minDate: Long = System.currentTimeMillis(), callback: (Long) -> Unit) {
        val calendar = Calendar.getInstance()

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                callback(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePicker.datePicker.minDate = minDate
        datePicker.show()
    }

    private fun validateStep1(): Boolean {
        val examName = examNameInput.text.toString().trim()
        val semester = semesterDropdown.text.toString()
        val academicYear = academicYearInput.text.toString().trim()

        when {
            examName.isEmpty() -> {
                Toast.makeText(this, "Please enter exam name", Toast.LENGTH_SHORT).show()
                return false
            }
            semester.isEmpty() -> {
                Toast.makeText(this, "Please select semester", Toast.LENGTH_SHORT).show()
                return false
            }
            academicYear.isEmpty() -> {
                Toast.makeText(this, "Please enter academic year", Toast.LENGTH_SHORT).show()
                return false
            }
            startDate == 0L -> {
                Toast.makeText(this, "Please select start date", Toast.LENGTH_SHORT).show()
                return false
            }
            endDate == 0L -> {
                Toast.makeText(this, "Please select end date", Toast.LENGTH_SHORT).show()
                return false
            }
            endDate < startDate -> {
                Toast.makeText(this, "End date cannot be before start date", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun proceedToStep2() {
        val intent = Intent(this, ExamCreationStep2Activity::class.java)
        intent.putExtra("EXAM_NAME", examNameInput.text.toString().trim())
        intent.putExtra("SEMESTER", selectedSemester)
        intent.putExtra("ACADEMIC_YEAR", academicYearInput.text.toString().trim())
        intent.putExtra("START_DATE", startDate)
        intent.putExtra("END_DATE", endDate)
        startActivity(intent)
    }
}