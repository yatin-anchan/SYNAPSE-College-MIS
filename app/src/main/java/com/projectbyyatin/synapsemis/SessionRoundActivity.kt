package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.SessionRoundAdapter
import com.projectbyyatin.synapsemis.models.RoomAllocation
import com.projectbyyatin.synapsemis.models.SessionRound
import java.text.SimpleDateFormat
import java.util.*

class SessionRoundActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var dateText: TextView
    private lateinit var timeText: TextView
    private lateinit var statusText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar

    // 🔴 FIXED: MaterialButton instead of FloatingActionButton
    private lateinit var fabAddRoom: MaterialButton

    private lateinit var btnSelectDate: MaterialButton
    private lateinit var btnSelectTime: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnSubmit: MaterialButton
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: SessionRoundAdapter

    private var facultyId = ""
    private var facultyName = ""
    private var departmentId = ""

    private var selectedDate: Long = 0
    private var selectedTimeHour = 9
    private var selectedTimeMinute = 0

    private var sessionRoundId = ""
    private var roomAllocations = mutableListOf<RoomAllocation>()
    private var isSubmitted = false

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_round)

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        facultyName = intent.getStringExtra("FACULTY_NAME") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()

        val calendar = Calendar.getInstance()
        selectedDate = calendar.timeInMillis
        updateDateTimeDisplay()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        dateText = findViewById(R.id.date_text)
        timeText = findViewById(R.id.time_text)
        statusText = findViewById(R.id.status_text)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)

        // 🔴 FIXED CAST
        fabAddRoom = findViewById(R.id.fab_add_room)

        btnSelectDate = findViewById(R.id.btn_select_date)
        btnSelectTime = findViewById(R.id.btn_select_time)
        btnSave = findViewById(R.id.btn_save)
        btnSubmit = findViewById(R.id.btn_submit)
        emptyView = findViewById(R.id.empty_view)
        emptyText = findViewById(R.id.empty_text)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Session Round"
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = SessionRoundAdapter(
            roomAllocations,
            isLocked = false,
            onEdit = { editRoomAllocation(it) },
            onDelete = { deleteRoomAllocation(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        updateEmptyView()
    }

    private fun setupClickListeners() {
        btnSelectDate.setOnClickListener { showDatePicker() }
        btnSelectTime.setOnClickListener { showTimePicker() }

        fabAddRoom.setOnClickListener {
            if (isSubmitted) {
                Toast.makeText(this, "Session already submitted", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddRoomDialog()
        }

        btnSave.setOnClickListener { saveDraft() }
        btnSubmit.setOnClickListener { showSubmitConfirmation() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }

        DatePickerDialog(
            this,
            { _, y, m, d ->
                calendar.set(y, m, d)
                selectedDate = calendar.timeInMillis
                updateDateTimeDisplay()
                checkExistingSession()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, h, m ->
                selectedTimeHour = h
                selectedTimeMinute = m
                updateDateTimeDisplay()
            },
            selectedTimeHour,
            selectedTimeMinute,
            false
        ).show()
    }

    private fun updateDateTimeDisplay() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = selectedDate
            set(Calendar.HOUR_OF_DAY, selectedTimeHour)
            set(Calendar.MINUTE, selectedTimeMinute)
        }
        dateText.text = dateFormat.format(calendar.time)
        timeText.text = timeFormat.format(calendar.time)
    }

    private fun checkExistingSession() {
        showLoading(true)

        val cal = Calendar.getInstance().apply {
            timeInMillis = selectedDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val end = cal.timeInMillis

        firestore.collection("session_rounds")
            .whereEqualTo("createdBy", facultyId)
            .whereGreaterThanOrEqualTo("sessionDate", start)
            .whereLessThanOrEqualTo("sessionDate", end)
            .limit(1)
            .get()
            .addOnSuccessListener {
                if (!it.isEmpty) {
                    sessionRoundId = it.documents[0].id
                    loadSessionData(sessionRoundId)
                } else {
                    roomAllocations.clear()
                    adapter.updateList(roomAllocations, false)
                    updateEmptyView()
                    showLoading(false)
                }
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Error checking session", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadSessionData(id: String) {
        firestore.collection("session_rounds").document(id)
            .get()
            .addOnSuccessListener { doc ->
                val session = doc.toObject(SessionRound::class.java) ?: return@addOnSuccessListener
                isSubmitted = session.isSubmitted
                roomAllocations.clear()
                roomAllocations.addAll(session.roomAllocations)

                val cal = Calendar.getInstance().apply { timeInMillis = session.sessionDate }
                selectedTimeHour = cal.get(Calendar.HOUR_OF_DAY)
                selectedTimeMinute = cal.get(Calendar.MINUTE)

                updateDateTimeDisplay()
                adapter.updateList(roomAllocations, isSubmitted)
                updateEmptyView()
                updateSubmitButton()
                showLoading(false)
            }
    }

    private fun showAddRoomDialog() {
        val intent = android.content.Intent(this, AddRoomAllocationActivity::class.java)
        intent.putExtra("FACULTY_ID", facultyId)
        intent.putExtra("SESSION_DATE", selectedDate)
        intent.putExtra("SESSION_TIME_HOUR", selectedTimeHour)
        intent.putExtra("SESSION_TIME_MINUTE", selectedTimeMinute)
        startActivityForResult(intent, REQUEST_ADD_ROOM)
    }

    private fun editRoomAllocation(position: Int) {
        if (isSubmitted) return
        val a = roomAllocations[position]

        val intent = android.content.Intent(this, AddRoomAllocationActivity::class.java)
        intent.putExtra("EDIT_MODE", true)
        intent.putExtra("EDIT_POSITION", position)
        intent.putExtra("ROOM_NO", a.roomNo)
        intent.putExtra("TEACHER_ID", a.teacherId)
        intent.putExtra("TEACHER_NAME", a.teacherName)
        intent.putExtra("CLASS_ID", a.classId)
        intent.putExtra("CLASS_NAME", a.className)
        startActivityForResult(intent, REQUEST_EDIT_ROOM)
    }

    private fun deleteRoomAllocation(position: Int) {
        if (isSubmitted) return
        roomAllocations.removeAt(position)
        adapter.updateList(roomAllocations, false)
        updateEmptyView()
    }

    private fun saveDraft() {
        if (roomAllocations.isEmpty()) return
        showLoading(true)

        val cal = Calendar.getInstance().apply {
            timeInMillis = selectedDate
            set(Calendar.HOUR_OF_DAY, selectedTimeHour)
            set(Calendar.MINUTE, selectedTimeMinute)
        }

        val session = SessionRound(
            id = sessionRoundId,
            sessionDate = cal.timeInMillis,
            createdBy = facultyId,
            createdByName = facultyName,
            roomAllocations = roomAllocations,
            isSubmitted = false,
            createdAt = System.currentTimeMillis()
        )

        val ref = if (sessionRoundId.isEmpty())
            firestore.collection("session_rounds").document()
        else firestore.collection("session_rounds").document(sessionRoundId)

        sessionRoundId = ref.id
        ref.set(session).addOnSuccessListener {
            showLoading(false)
            Toast.makeText(this, "Draft saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSubmitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Submit Session")
            .setMessage("Once submitted, you cannot edit.")
            .setPositiveButton("Submit") { _, _ -> submitSession() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitSession() {
        isSubmitted = true
        saveDraft()
        adapter.updateList(roomAllocations, true)
        updateSubmitButton()
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (roomAllocations.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (roomAllocations.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateSubmitButton() {
        btnSave.isEnabled = !isSubmitted
        btnSubmit.isEnabled = !isSubmitted
        fabAddRoom.isEnabled = !isSubmitted
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onBackPressed() {
        if (!isSubmitted && roomAllocations.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("Save draft before exiting?")
                .setPositiveButton("Save") { _, _ -> saveDraft(); finish() }
                .setNegativeButton("Discard") { _, _ -> super.onBackPressed() }
                .show()
        } else super.onBackPressed()
    }

    companion object {
        private const val REQUEST_ADD_ROOM = 1001
        private const val REQUEST_EDIT_ROOM = 1002
    }
}
