package com.projectbyyatin.synapsemis.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ExamMarks
import com.projectbyyatin.synapsemis.models.ExamMarks.Companion.calculateGrade

// FIX: Removed "import ExamMarks.Companion.recalculate" — same root cause as all
// other adapters. The companion-extension import triggers the compiler's internal
// named-group regex (API 26+), causing:
//   • 'infix' modifier required on FirNamedFunctionSymbol kotlin/collections/get
//   • Call requires API level 26 (Matcher#start via MatchGroupCollection#get(String))
// Uses a local recalc() extension instead.

class StudentMarksAdapter(
    private var marksList: MutableList<ExamMarks>,
    private val writtenMaxMarks: Int,
    private val internalMaxMarks: Int,
    private val onMarksChanged: () -> Unit = {}   // explicit () -> Unit — no type-inference ambiguity
) : RecyclerView.Adapter<StudentMarksAdapter.ViewHolder>() {

    // Tag keys for watcher storage — avoids generic .tag collisions
    private val TAG_WRITTEN_WATCHER  = R.id.writteninput
    private val TAG_INTERNAL_WATCHER = R.id.internalinput

    // ─────────────────────────────────────────────────────────────────────────
    // Local recalc() — captures the new copy returned by ExamMarks.copy().
    // The original adapter stored marks in a separate marksData Map<String,Float>
    // and called marks.copy(marksObtained = ...) using a now-removed field.
    // We instead store the full recalculated ExamMarks back into marksList[pos].
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
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val studentName: TextView  = itemView.findViewById(R.id.studentname)
        val rollNo: TextView       = itemView.findViewById(R.id.rollno)
        val writtenInput: EditText = itemView.findViewById(R.id.writteninput)
        val internalInput: EditText = itemView.findViewById(R.id.internalinput)
        val maxMarksText: TextView = itemView.findViewById(R.id.maxmarkstext)
        val percentageText: TextView = itemView.findViewById(R.id.percentagetext)
        val gradeText: TextView    = itemView.findViewById(R.id.gradetext)
        val absentCheckbox: CheckBox = itemView.findViewById(R.id.absentcheckbox)
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_marks, parent, false)
        return ViewHolder(view)
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val marks = marksList[position]

        holder.studentName.text = marks.studentName
        holder.rollNo.text      = marks.studentRollNo
        holder.maxMarksText.text = "/ ${writtenMaxMarks + internalMaxMarks}"

        // Show/hide columns based on exam type
        holder.writtenInput.visibility  = if (writtenMaxMarks  > 0) View.VISIBLE else View.GONE
        holder.internalInput.visibility = if (internalMaxMarks > 0) View.VISIBLE else View.GONE

        // Detach old watchers BEFORE setText — prevents re-entry infinite loops
        detachWatchers(holder)

        // Populate fields (show blank when 0 so hint is visible)
        holder.writtenInput.setText(
            if (marks.writtenMarksObtained == 0f && !marks.isAbsent) ""
            else formatMarks(marks.writtenMarksObtained)
        )
        holder.internalInput.setText(
            if (marks.internalMarksObtained == 0f && !marks.isAbsent) ""
            else formatMarks(marks.internalMarksObtained)
        )

        // Set checkbox without firing listener
        holder.absentCheckbox.setOnCheckedChangeListener(null)
        holder.absentCheckbox.isChecked = marks.isAbsent

        // Attach fresh watchers
        val writtenWatcher  = createWrittenWatcher(holder,  marks)
        val internalWatcher = createInternalWatcher(holder, marks)
        holder.writtenInput.setTag(TAG_WRITTEN_WATCHER,   writtenWatcher)
        holder.internalInput.setTag(TAG_INTERNAL_WATCHER, internalWatcher)
        holder.writtenInput.addTextChangedListener(writtenWatcher)
        holder.internalInput.addTextChangedListener(internalWatcher)

        // Absent checkbox
        holder.absentCheckbox.setOnCheckedChangeListener { _, isChecked ->
            detachWatchers(holder)
            val updated = marks.copy(
                isAbsent              = isChecked,
                writtenMarksObtained  = if (isChecked) 0f else marks.writtenMarksObtained,
                internalMarksObtained = if (isChecked) 0f else marks.internalMarksObtained
            ).recalc()
            marksList[holder.adapterPosition] = updated

            if (isChecked) {
                holder.writtenInput.setText("")
                holder.internalInput.setText("")
            }

            val wW = createWrittenWatcher(holder, updated)
            val wI = createInternalWatcher(holder, updated)
            holder.writtenInput.setTag(TAG_WRITTEN_WATCHER,   wW)
            holder.internalInput.setTag(TAG_INTERNAL_WATCHER, wI)
            holder.writtenInput.addTextChangedListener(wW)
            holder.internalInput.addTextChangedListener(wI)

            updateDisplay(holder, updated)
            onMarksChanged()
        }

        updateDisplay(holder, marks)
    }

    // ─── Watcher factories ────────────────────────────────────────────────────

    private fun createWrittenWatcher(holder: ViewHolder, marks: ExamMarks): TextWatcher =
        object : TextWatcher {
            private var isSelfUpdate = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isSelfUpdate) return
                val input  = s?.toString()?.trim() ?: ""
                val parsed = input.toFloatOrNull()
                val newMarks = when {
                    input.isEmpty() -> marks.copy(writtenMarksObtained = 0f)
                    parsed == null  -> {
                        holder.writtenInput.error = "Invalid"
                        marks.copy(writtenMarksObtained = 0f)
                    }
                    parsed > writtenMaxMarks -> {
                        holder.writtenInput.error = "Max $writtenMaxMarks"
                        isSelfUpdate = true
                        holder.writtenInput.setText(writtenMaxMarks.toString())
                        holder.writtenInput.setSelection(holder.writtenInput.text.length)
                        isSelfUpdate = false
                        return
                    }
                    else -> {
                        holder.writtenInput.error = null
                        marks.copy(writtenMarksObtained = parsed)
                    }
                }
                val updated = newMarks.recalc()
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) marksList[pos] = updated
                updateDisplay(holder, updated)
                onMarksChanged()
            }
        }

    private fun createInternalWatcher(holder: ViewHolder, marks: ExamMarks): TextWatcher =
        object : TextWatcher {
            private var isSelfUpdate = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isSelfUpdate) return
                val input  = s?.toString()?.trim() ?: ""
                val parsed = input.toFloatOrNull()
                val newMarks = when {
                    input.isEmpty() -> marks.copy(internalMarksObtained = 0f)
                    parsed == null  -> {
                        holder.internalInput.error = "Invalid"
                        marks.copy(internalMarksObtained = 0f)
                    }
                    parsed > internalMaxMarks -> {
                        holder.internalInput.error = "Max $internalMaxMarks"
                        isSelfUpdate = true
                        holder.internalInput.setText(internalMaxMarks.toString())
                        holder.internalInput.setSelection(holder.internalInput.text.length)
                        isSelfUpdate = false
                        return
                    }
                    else -> {
                        holder.internalInput.error = null
                        marks.copy(internalMarksObtained = parsed)
                    }
                }
                val updated = newMarks.recalc()
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) marksList[pos] = updated
                updateDisplay(holder, updated)
                onMarksChanged()
            }
        }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun detachWatchers(holder: ViewHolder) {
        (holder.writtenInput.getTag(TAG_WRITTEN_WATCHER) as? TextWatcher)?.let {
            holder.writtenInput.removeTextChangedListener(it)
        }
        (holder.internalInput.getTag(TAG_INTERNAL_WATCHER) as? TextWatcher)?.let {
            holder.internalInput.removeTextChangedListener(it)
        }
    }

    private fun formatMarks(value: Float): String =
        if (value == value.toLong().toFloat()) value.toLong().toString()
        else String.format("%.1f", value)

    private fun updateDisplay(holder: ViewHolder, marks: ExamMarks) {
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
                val color = ContextCompat.getColor(ctx, colorRes)
                holder.percentageText.setTextColor(color)
                holder.gradeText.setTextColor(color)
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

    fun updateList(newList: List<ExamMarks>) {
        marksList = newList.toMutableList()
        notifyDataSetChanged()
    }

    /** Returns only students who have marks entered or are marked absent. */
    fun getMarksData(): List<ExamMarks> =
        marksList.filter { it.totalMarksObtained > 0 || it.isAbsent }
}