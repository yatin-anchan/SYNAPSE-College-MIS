package com.projectbyyatin.synapsemis

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.projectbyyatin.synapsemis.models.Subject
import com.projectbyyatin.synapsemis.models.TimetableSlot
import java.util.*

/**
 * Shared helper to show the Add/Edit Slot dialog.
 * Used by both [CreateTimetableActivity] and [EditTimetableActivity] to avoid duplicating logic.
 */
object TimetableSlotDialogHelper {

    fun show(
        activity: AppCompatActivity,
        subjects: List<Subject>,
        isBreak: Boolean,
        editSlot: TimetableSlot?,
        defaultDuration: Int,
        currentDaySlots: List<TimetableSlot>,
        onSave: (TimetableSlot) -> Unit
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_slot, null)

        // ── View references ───────────────────────────────────────────────────
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val layoutLecture = dialogView.findViewById<View>(R.id.layout_lecture_fields)
        val layoutBreak = dialogView.findViewById<View>(R.id.layout_break_fields)
        val spinnerSubject = dialogView.findViewById<Spinner>(R.id.spinner_subject)
        val rbTheory = dialogView.findViewById<RadioButton>(R.id.rb_theory)
        val rbPractical = dialogView.findViewById<RadioButton>(R.id.rb_practical)
        val tvStartTime = dialogView.findViewById<TextView>(R.id.tv_start_time)
        val tvEndTime = dialogView.findViewById<TextView>(R.id.tv_end_time)
        val checkCustomDuration = dialogView.findViewById<CheckBox>(R.id.check_custom_duration)
        val etCustomDuration = dialogView.findViewById<TextInputEditText>(R.id.et_custom_duration)
        val etRoom = dialogView.findViewById<TextInputEditText>(R.id.et_room)
        val etBreakLabel = dialogView.findViewById<TextInputEditText>(R.id.et_break_label)
        val etBreakDuration = dialogView.findViewById<TextInputEditText>(R.id.et_break_duration)

        // ── Determine slot type ───────────────────────────────────────────────
        val showingBreak = if (editSlot != null) editSlot.slotType == "break" else isBreak
        tvDialogTitle.text = when {
            editSlot != null -> "Edit Slot"
            showingBreak -> "Add Break"
            else -> "Add Lecture"
        }
        layoutLecture.visibility = if (showingBreak) View.GONE else View.VISIBLE
        layoutBreak.visibility = if (showingBreak) View.VISIBLE else View.GONE

        // ── Smart start time: pick up where last slot ended ───────────────────
        val smartStart = if (editSlot != null) {
            editSlot.startTimeMinutes
        } else {
            currentDaySlots.maxByOrNull { it.endTimeMinutes }?.endTimeMinutes ?: (8 * 60)
        }
        var selectedStartMinutes = smartStart
        var selectedDuration = editSlot?.durationMinutes ?: defaultDuration
        var selectedSubject: Subject? = null

        // ── Time helpers ──────────────────────────────────────────────────────
        fun minutesToTimeStr(minutes: Int): String {
            val h = (minutes / 60) % 24
            val m = minutes % 60
            val amPm = if (h < 12) "AM" else "PM"
            val dH = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
            return String.format("%d:%02d %s", dH, m, amPm)
        }

        fun updateTimeDisplay() {
            tvStartTime.text = minutesToTimeStr(selectedStartMinutes)
            tvEndTime.text = minutesToTimeStr(selectedStartMinutes + selectedDuration)
        }
        updateTimeDisplay()

        // ── Start time picker ─────────────────────────────────────────────────
        tvStartTime.setOnClickListener {
            TimePickerDialog(activity, { _, hour, minute ->
                selectedStartMinutes = hour * 60 + minute
                updateTimeDisplay()
            }, selectedStartMinutes / 60, selectedStartMinutes % 60, false).show()
        }

        // ── Custom duration toggle ────────────────────────────────────────────
        val isCustom = editSlot != null && editSlot.durationMinutes != defaultDuration
        checkCustomDuration.isChecked = isCustom
        etCustomDuration.visibility = if (isCustom) View.VISIBLE else View.GONE
        if (isCustom) etCustomDuration.setText("${editSlot!!.durationMinutes}")

        checkCustomDuration.setOnCheckedChangeListener { _, checked ->
            etCustomDuration.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                selectedDuration = defaultDuration
                updateTimeDisplay()
            }
        }
        etCustomDuration.setOnFocusChangeListener { _, focused ->
            if (!focused) {
                val mins = etCustomDuration.text?.toString()?.toIntOrNull()
                if (mins != null && mins > 0) {
                    selectedDuration = mins
                    updateTimeDisplay()
                }
            }
        }

        // ── Lecture fields ────────────────────────────────────────────────────
        if (!showingBreak) {
            val subjectNames = listOf("Select Subject") + subjects.map { "${it.name} (${it.code})" }
            spinnerSubject.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, subjectNames)
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            // Restore subject if editing
            if (editSlot != null && editSlot.subjectId.isNotEmpty()) {
                val idx = subjects.indexOfFirst { it.id == editSlot.subjectId }
                if (idx >= 0) { spinnerSubject.setSelection(idx + 1); selectedSubject = subjects[idx] }
            }

            spinnerSubject.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    selectedSubject = if (pos == 0) null else subjects[pos - 1]
                    selectedSubject?.let { sub ->
                        // A "Theory"-only subject cannot have a practical lecture
                        val canBePractical = sub.type.equals("Practical", ignoreCase = true) ||
                                (!sub.type.equals("Theory", ignoreCase = true))
                        rbPractical.isEnabled = canBePractical
                        if (!canBePractical) rbTheory.isChecked = true
                    }
                }
            }

            // Restore lecture type
            if (editSlot != null && editSlot.lectureType == "practical") rbPractical.isChecked = true
            else rbTheory.isChecked = true

            // Room
            if (editSlot != null) etRoom.setText(editSlot.room)
        }

        // ── Break fields ──────────────────────────────────────────────────────
        if (showingBreak && editSlot != null) {
            etBreakLabel.setText(editSlot.breakLabel)
            etBreakDuration.setText("${editSlot.durationMinutes}")
        }

        // ── Build dialog ──────────────────────────────────────────────────────
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setPositiveButton("Save", null)   // Override to prevent auto-dismiss on validation failure
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // Re-read custom duration if set
                if (checkCustomDuration.isChecked) {
                    val mins = etCustomDuration.text?.toString()?.toIntOrNull()
                    if (mins != null && mins > 0) selectedDuration = mins
                }

                val slot: TimetableSlot = if (showingBreak) {
                    val label = etBreakLabel.text?.toString()?.trim()
                        .takeIf { !it.isNullOrEmpty() } ?: "Break"
                    val breakDur = etBreakDuration.text?.toString()?.toIntOrNull() ?: 15
                    TimetableSlot(
                        id = editSlot?.id ?: UUID.randomUUID().toString(),
                        slotType = "break",
                        breakLabel = label,
                        startTimeMinutes = selectedStartMinutes,
                        durationMinutes = breakDur,
                        endTimeMinutes = selectedStartMinutes + breakDur
                    )
                } else {
                    if (selectedSubject == null) {
                        Toast.makeText(activity, "Please select a subject", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val lectureType = if (rbPractical.isChecked) "practical" else "theory"
                    val room = etRoom.text?.toString()?.trim() ?: ""
                    TimetableSlot(
                        id = editSlot?.id ?: UUID.randomUUID().toString(),
                        slotType = "lecture",
                        lectureType = lectureType,
                        subjectId = selectedSubject!!.id,
                        subjectName = selectedSubject!!.name,
                        subjectCode = selectedSubject!!.code,
                        subjectType = selectedSubject!!.type,
                        facultyId = selectedSubject!!.assignedFacultyId,
                        facultyName = selectedSubject!!.assignedFacultyName,
                        room = room,
                        startTimeMinutes = selectedStartMinutes,
                        durationMinutes = selectedDuration,
                        endTimeMinutes = selectedStartMinutes + selectedDuration
                    )
                }

                // Overlap check (exclude the slot being edited from comparison)
                val hasOverlap = currentDaySlots
                    .filter { it.id != editSlot?.id }
                    .any { existing ->
                        slot.startTimeMinutes < existing.endTimeMinutes &&
                                slot.endTimeMinutes > existing.startTimeMinutes
                    }

                if (hasOverlap) {
                    Toast.makeText(activity, "This slot overlaps with an existing one!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                onSave(slot)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}