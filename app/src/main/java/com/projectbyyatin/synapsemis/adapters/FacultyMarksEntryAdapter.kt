package com.projectbyyatin.synapsemis.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ExamMarks
import com.projectbyyatin.synapsemis.models.ExamMarks.Companion.calculateGrade

class FacultyMarksEntryAdapter(
    private var marksList: MutableList<ExamMarks>,
    private val writtenMaxMarks: Int,
    private val internalMaxMarks: Int,
    private var isLocked: Boolean = false,
    private val onMarksChanged: () -> Unit = {}
) : RecyclerView.Adapter<FacultyMarksEntryAdapter.MarksViewHolder>() {

    private val changedItems = mutableSetOf<String>()

    private val TAG_WRITTEN_WATCHER  = R.id.writteninput
    private val TAG_INTERNAL_WATCHER = R.id.internalinput

    // ─────────────────────────────────────────────────────────────────────────
    inner class MarksViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rollNo: TextView            = itemView.findViewById(R.id.rollno)
        val studentName: TextView       = itemView.findViewById(R.id.studentname)
        val writtenInput: EditText      = itemView.findViewById(R.id.writteninput)
        val internalInput: EditText     = itemView.findViewById(R.id.internalinput)
        val writtenContainer: LinearLayout  = itemView.findViewById(R.id.written_container)
        val internalContainer: LinearLayout = itemView.findViewById(R.id.internal_container)
        val columnsDivider: View        = itemView.findViewById(R.id.columns_divider)
        val writtenMaxLabel: TextView   = itemView.findViewById(R.id.written_max_label)
        val internalMaxLabel: TextView  = itemView.findViewById(R.id.internal_max_label)
        val maxMarksText: TextView      = itemView.findViewById(R.id.maxmarkstext)
        val percentageText: TextView    = itemView.findViewById(R.id.percentagetext)
        val gradeText: TextView         = itemView.findViewById(R.id.gradetext)
        val absentCheckbox: CheckBox    = itemView.findViewById(R.id.absentcheckbox)
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarksViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_faculty_marks_entry, parent, false)
        return MarksViewHolder(view)
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onBindViewHolder(holder: MarksViewHolder, position: Int) {
        val marks = marksList[position]

        // ── Basic info ────────────────────────────────────────────────────────
        holder.rollNo.text       = marks.studentRollNo
        holder.studentName.text  = marks.studentName
        holder.maxMarksText.text = "Total: ${writtenMaxMarks + internalMaxMarks}"

        // ── Show/hide columns and divider based on exam type ──────────────────
        val showWritten  = writtenMaxMarks  > 0
        val showInternal = internalMaxMarks > 0

        holder.writtenContainer.visibility  = if (showWritten)  View.VISIBLE else View.GONE
        holder.internalContainer.visibility = if (showInternal) View.VISIBLE else View.GONE
        // Only show the divider when BOTH columns are present
        holder.columnsDivider.visibility    = if (showWritten && showInternal) View.VISIBLE else View.GONE

        // Update the "/ XX" labels to reflect actual max values
        holder.writtenMaxLabel.text  = "/ $writtenMaxMarks"
        holder.internalMaxLabel.text = "/ $internalMaxMarks"

        // ── Detach old watchers before setting text ───────────────────────────
        detachWatchers(holder)

        // ── Populate fields ───────────────────────────────────────────────────
        holder.writtenInput.setText(
            if (marks.writtenMarksObtained == 0f && !marks.isAbsent) ""
            else formatMarks(marks.writtenMarksObtained)
        )
        holder.internalInput.setText(
            if (marks.internalMarksObtained == 0f && !marks.isAbsent) ""
            else formatMarks(marks.internalMarksObtained)
        )

        holder.absentCheckbox.setOnCheckedChangeListener(null)
        holder.absentCheckbox.isChecked = marks.isAbsent

        // ── Re-attach watchers ────────────────────────────────────────────────
        val writtenWatcher  = createWrittenWatcher(holder, marks)
        val internalWatcher = createInternalWatcher(holder, marks)

        holder.writtenInput.setTag(TAG_WRITTEN_WATCHER,   writtenWatcher)
        holder.internalInput.setTag(TAG_INTERNAL_WATCHER, internalWatcher)

        holder.writtenInput.addTextChangedListener(writtenWatcher)
        holder.internalInput.addTextChangedListener(internalWatcher)

        // ── Absent checkbox ───────────────────────────────────────────────────
        holder.absentCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isLocked) {
                holder.absentCheckbox.isChecked = marks.isAbsent
                return@setOnCheckedChangeListener
            }
            marks.isAbsent = isChecked
            if (isChecked) {
                detachWatchers(holder)
                marks.writtenMarksObtained  = 0f
                marks.internalMarksObtained = 0f
                holder.writtenInput.setText("")
                holder.internalInput.setText("")
                val wW = createWrittenWatcher(holder, marks)
                val wI = createInternalWatcher(holder, marks)
                holder.writtenInput.setTag(TAG_WRITTEN_WATCHER,   wW)
                holder.internalInput.setTag(TAG_INTERNAL_WATCHER, wI)
                holder.writtenInput.addTextChangedListener(wW)
                holder.internalInput.addTextChangedListener(wI)
            }
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                marksList[pos] = marks.recalculateAndCopy()
                changedItems.add(marks.studentId)
                updatePercentageAndGrade(holder, marksList[pos])
            }
            onMarksChanged()
        }

        // ── Lock state ────────────────────────────────────────────────────────
        setViewStates(holder, isLocked)
        updatePercentageAndGrade(holder, marks)
    }

    // ─── Watcher factories ────────────────────────────────────────────────────

    private fun createWrittenWatcher(
        holder: MarksViewHolder,
        marks: ExamMarks
    ): TextWatcher = object : TextWatcher {

        private var isSelfUpdate = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (isLocked || isSelfUpdate) return

            val input  = s?.toString()?.trim() ?: ""
            val parsed = input.toFloatOrNull()

            when {
                input.isEmpty() -> {
                    marks.writtenMarksObtained = 0f
                    holder.writtenInput.error  = null
                }
                parsed == null -> {
                    holder.writtenInput.error  = "Invalid number"
                    marks.writtenMarksObtained = 0f
                }
                parsed > writtenMaxMarks -> {
                    holder.writtenInput.error  = "Max $writtenMaxMarks"
                    marks.writtenMarksObtained = writtenMaxMarks.toFloat()
                    isSelfUpdate = true
                    holder.writtenInput.setText(writtenMaxMarks.toString())
                    holder.writtenInput.setSelection(holder.writtenInput.text.length)
                    isSelfUpdate = false
                    return
                }
                else -> {
                    marks.writtenMarksObtained = parsed
                    holder.writtenInput.error  = null
                }
            }

            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                marksList[pos] = marks.recalculateAndCopy()
                changedItems.add(marks.studentId)
                updatePercentageAndGrade(holder, marksList[pos])
            }
            onMarksChanged()
        }
    }

    private fun createInternalWatcher(
        holder: MarksViewHolder,
        marks: ExamMarks
    ): TextWatcher = object : TextWatcher {

        private var isSelfUpdate = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (isLocked || isSelfUpdate) return

            val input  = s?.toString()?.trim() ?: ""
            val parsed = input.toFloatOrNull()

            when {
                input.isEmpty() -> {
                    marks.internalMarksObtained = 0f
                    holder.internalInput.error  = null
                }
                parsed == null -> {
                    holder.internalInput.error  = "Invalid number"
                    marks.internalMarksObtained = 0f
                }
                parsed > internalMaxMarks -> {
                    holder.internalInput.error  = "Max $internalMaxMarks"
                    marks.internalMarksObtained = internalMaxMarks.toFloat()
                    isSelfUpdate = true
                    holder.internalInput.setText(internalMaxMarks.toString())
                    holder.internalInput.setSelection(holder.internalInput.text.length)
                    isSelfUpdate = false
                    return
                }
                else -> {
                    marks.internalMarksObtained = parsed
                    holder.internalInput.error  = null
                }
            }

            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                marksList[pos] = marks.recalculateAndCopy()
                changedItems.add(marks.studentId)
                updatePercentageAndGrade(holder, marksList[pos])
            }
            onMarksChanged()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun detachWatchers(holder: MarksViewHolder) {
        (holder.writtenInput.getTag(TAG_WRITTEN_WATCHER) as? TextWatcher)?.let {
            holder.writtenInput.removeTextChangedListener(it)
        }
        (holder.internalInput.getTag(TAG_INTERNAL_WATCHER) as? TextWatcher)?.let {
            holder.internalInput.removeTextChangedListener(it)
        }
    }

    /**
     * FIX: use the marks object's own writtenMaxMarks/internalMaxMarks instead
     * of the adapter-level vars. They're the same for new entries, but differ
     * when a draft was saved with different values — using the wrong source
     * produces an incorrect percentage even though total marks are correct.
     */
    private fun ExamMarks.recalculateAndCopy(): ExamMarks {
        val total    = writtenMarksObtained + internalMarksObtained
        val maxTotal = this.writtenMaxMarks + this.internalMaxMarks   // ← marks object fields
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

    private fun formatMarks(value: Float): String =
        if (value == value.toLong().toFloat()) value.toLong().toString()
        else String.format("%.1f", value)

    private fun setViewStates(holder: MarksViewHolder, locked: Boolean) {
        val alpha = if (locked) 0.6f else 1.0f
        listOf(holder.writtenInput, holder.internalInput, holder.absentCheckbox).forEach {
            it.isEnabled = !locked
            it.alpha     = alpha
        }
    }

    private fun updatePercentageAndGrade(holder: MarksViewHolder, marks: ExamMarks) {
        val ctx = holder.itemView.context
        when {
            marks.isAbsent -> {
                holder.percentageText.text = "AB"
                holder.gradeText.text      = "AB"
                val red = ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
                holder.percentageText.setTextColor(red)
                holder.gradeText.setTextColor(red)
            }
            marks.totalMarksObtained > 0 -> {
                holder.percentageText.text = String.format("%.1f%%", marks.percentage)
                holder.gradeText.text      = marks.grade
                val colorRes = when (marks.grade) {
                    "O", "A+", "A" -> android.R.color.holo_green_dark
                    "B+", "B"      -> android.R.color.holo_blue_dark
                    "C"            -> android.R.color.holo_orange_dark
                    else           -> android.R.color.holo_red_dark
                }
                holder.percentageText.setTextColor(ContextCompat.getColor(ctx, colorRes))
                holder.gradeText.setTextColor(ContextCompat.getColor(ctx, colorRes))
            }
            else -> {
                holder.percentageText.text = "--"
                holder.gradeText.text      = "--"
                val grey = ContextCompat.getColor(ctx, android.R.color.darker_gray)
                holder.percentageText.setTextColor(grey)
                holder.gradeText.setTextColor(grey)
            }
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    override fun getItemCount(): Int = marksList.size

    fun setLocked(locked: Boolean) {
        isLocked = locked
        notifyDataSetChanged()
    }

    fun updateList(newList: List<ExamMarks>) {
        marksList = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun hasUnsavedChanges(): Boolean = changedItems.isNotEmpty()

    fun clearUnsavedChanges() = changedItems.clear()
}