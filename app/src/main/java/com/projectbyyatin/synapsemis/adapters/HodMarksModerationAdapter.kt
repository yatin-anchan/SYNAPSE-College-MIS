package com.projectbyyatin.synapsemis.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ExamMarks
import com.projectbyyatin.synapsemis.models.ExamMarks.Companion.calculateGrade

class HodMarksModerationAdapter(
    private var moderatedMarks: MutableList<ExamMarks>,
    private var originalMarks: List<ExamMarks>,
    private val writtenMaxMarks: Int,
    private val internalMaxMarks: Int,
    private val onStatsUpdate: () -> Unit = {}
) : RecyclerView.Adapter<HodMarksModerationAdapter.ViewHolder>() {

    private val TAG_WRITTEN_WATCHER  = R.id.moderatedwritteninput
    private val TAG_INTERNAL_WATCHER = R.id.moderatedinternalinput

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

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rollNo: TextView               = itemView.findViewById(R.id.rollno)
        val studentName: TextView          = itemView.findViewById(R.id.studentname)
        val originalWrittenText: TextView  = itemView.findViewById(R.id.originalwrittentext)
        val originalInternalText: TextView = itemView.findViewById(R.id.originalinternaltext)
        val moderatedWrittenInput: EditText  = itemView.findViewById(R.id.moderatedwritteninput)
        val moderatedInternalInput: EditText = itemView.findViewById(R.id.moderatedinternalinput)
        val totalPercentageText: TextView  = itemView.findViewById(R.id.totalpercentagetext)
        val gradeText: TextView            = itemView.findViewById(R.id.gradetext)
        val absentCheckbox: CheckBox       = itemView.findViewById(R.id.absentcheckbox)
        // FIX: was ImageView but XML declares a plain <View> — cast now matches
        val changeIndicator: View          = itemView.findViewById(R.id.changeindicator)
        val revertButton: Button           = itemView.findViewById(R.id.revertbutton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hod_marks_moderation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val moderated = moderatedMarks[position]
        val original  = originalMarks.find { it.studentId == moderated.studentId } ?: return

        holder.rollNo.text      = moderated.studentRollNo
        holder.studentName.text = moderated.studentName

        holder.originalWrittenText.text  = formatMarks(original.writtenMarksObtained)
        holder.originalInternalText.text = formatMarks(original.internalMarksObtained)

        holder.moderatedWrittenInput.visibility  = if (writtenMaxMarks  > 0) View.VISIBLE else View.GONE
        holder.moderatedInternalInput.visibility = if (internalMaxMarks > 0) View.VISIBLE else View.GONE
        holder.originalWrittenText.visibility    = if (writtenMaxMarks  > 0) View.VISIBLE else View.GONE
        holder.originalInternalText.visibility   = if (internalMaxMarks > 0) View.VISIBLE else View.GONE

        detachWatchers(holder)

        holder.moderatedWrittenInput.setText(formatMarks(moderated.writtenMarksObtained))
        holder.moderatedInternalInput.setText(formatMarks(moderated.internalMarksObtained))

        holder.absentCheckbox.setOnCheckedChangeListener(null)
        holder.absentCheckbox.isChecked = moderated.isAbsent

        val writtenWatcher  = createWrittenWatcher(holder, moderated, position)
        val internalWatcher = createInternalWatcher(holder, moderated, position)
        holder.moderatedWrittenInput.setTag(TAG_WRITTEN_WATCHER,   writtenWatcher)
        holder.moderatedInternalInput.setTag(TAG_INTERNAL_WATCHER, internalWatcher)
        holder.moderatedWrittenInput.addTextChangedListener(writtenWatcher)
        holder.moderatedInternalInput.addTextChangedListener(internalWatcher)

        holder.absentCheckbox.setOnCheckedChangeListener { _, isChecked ->
            detachWatchers(holder)
            val updated = moderated.copy(
                isAbsent              = isChecked,
                writtenMarksObtained  = if (isChecked) 0f else moderated.writtenMarksObtained,
                internalMarksObtained = if (isChecked) 0f else moderated.internalMarksObtained
            ).recalc()

            moderatedMarks[holder.adapterPosition] = updated

            if (isChecked) {
                holder.moderatedWrittenInput.setText("0")
                holder.moderatedInternalInput.setText("0")
            }

            val wW = createWrittenWatcher(holder, updated, holder.adapterPosition)
            val wI = createInternalWatcher(holder, updated, holder.adapterPosition)
            holder.moderatedWrittenInput.setTag(TAG_WRITTEN_WATCHER,   wW)
            holder.moderatedInternalInput.setTag(TAG_INTERNAL_WATCHER, wI)
            holder.moderatedWrittenInput.addTextChangedListener(wW)
            holder.moderatedInternalInput.addTextChangedListener(wI)

            updateRowDisplay(holder, updated, original)
            onStatsUpdate()
        }

        holder.revertButton.setOnClickListener {
            detachWatchers(holder)
            val reverted = original.copy().recalc()
            moderatedMarks[holder.adapterPosition] = reverted

            holder.moderatedWrittenInput.setText(formatMarks(original.writtenMarksObtained))
            holder.moderatedInternalInput.setText(formatMarks(original.internalMarksObtained))
            holder.absentCheckbox.setOnCheckedChangeListener(null)
            holder.absentCheckbox.isChecked = original.isAbsent

            val wW = createWrittenWatcher(holder, reverted, holder.adapterPosition)
            val wI = createInternalWatcher(holder, reverted, holder.adapterPosition)
            holder.moderatedWrittenInput.setTag(TAG_WRITTEN_WATCHER,   wW)
            holder.moderatedInternalInput.setTag(TAG_INTERNAL_WATCHER, wI)
            holder.moderatedWrittenInput.addTextChangedListener(wW)
            holder.moderatedInternalInput.addTextChangedListener(wI)

            holder.absentCheckbox.setOnCheckedChangeListener { _, isChecked ->
                val u = moderatedMarks[holder.adapterPosition].copy(isAbsent = isChecked).recalc()
                moderatedMarks[holder.adapterPosition] = u
                updateRowDisplay(holder, u, original)
                onStatsUpdate()
            }

            updateRowDisplay(holder, reverted, original)
            onStatsUpdate()
        }

        updateRowDisplay(holder, moderated, original)
    }

    private fun createWrittenWatcher(
        holder: ViewHolder,
        marks: ExamMarks,
        position: Int
    ): TextWatcher = object : TextWatcher {

        private var isSelfUpdate = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (isSelfUpdate) return
            val input  = s?.toString()?.trim() ?: ""
            val parsed = input.toFloatOrNull()

            val newMarks = when {
                input.isEmpty() -> marks.copy(writtenMarksObtained = 0f)
                parsed == null  -> marks.copy(writtenMarksObtained = 0f).also {
                    holder.moderatedWrittenInput.error = "Invalid number"
                }
                parsed > writtenMaxMarks -> {
                    holder.moderatedWrittenInput.error = "Max $writtenMaxMarks"
                    isSelfUpdate = true
                    holder.moderatedWrittenInput.setText(writtenMaxMarks.toString())
                    holder.moderatedWrittenInput.setSelection(holder.moderatedWrittenInput.text.length)
                    isSelfUpdate = false
                    return
                }
                else -> {
                    holder.moderatedWrittenInput.error = null
                    marks.copy(writtenMarksObtained = parsed)
                }
            }

            val updated = newMarks.recalc()
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) moderatedMarks[pos] = updated

            val original = originalMarks.find { it.studentId == marks.studentId } ?: return
            updateRowDisplay(holder, updated, original)
            onStatsUpdate()
        }
    }

    private fun createInternalWatcher(
        holder: ViewHolder,
        marks: ExamMarks,
        position: Int
    ): TextWatcher = object : TextWatcher {

        private var isSelfUpdate = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (isSelfUpdate) return
            val input  = s?.toString()?.trim() ?: ""
            val parsed = input.toFloatOrNull()

            val newMarks = when {
                input.isEmpty() -> marks.copy(internalMarksObtained = 0f)
                parsed == null  -> marks.copy(internalMarksObtained = 0f).also {
                    holder.moderatedInternalInput.error = "Invalid number"
                }
                parsed > internalMaxMarks -> {
                    holder.moderatedInternalInput.error = "Max $internalMaxMarks"
                    isSelfUpdate = true
                    holder.moderatedInternalInput.setText(internalMaxMarks.toString())
                    holder.moderatedInternalInput.setSelection(holder.moderatedInternalInput.text.length)
                    isSelfUpdate = false
                    return
                }
                else -> {
                    holder.moderatedInternalInput.error = null
                    marks.copy(internalMarksObtained = parsed)
                }
            }

            val updated = newMarks.recalc()
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) moderatedMarks[pos] = updated

            val original = originalMarks.find { it.studentId == marks.studentId } ?: return
            updateRowDisplay(holder, updated, original)
            onStatsUpdate()
        }
    }

    private fun detachWatchers(holder: ViewHolder) {
        (holder.moderatedWrittenInput.getTag(TAG_WRITTEN_WATCHER) as? TextWatcher)?.let {
            holder.moderatedWrittenInput.removeTextChangedListener(it)
        }
        (holder.moderatedInternalInput.getTag(TAG_INTERNAL_WATCHER) as? TextWatcher)?.let {
            holder.moderatedInternalInput.removeTextChangedListener(it)
        }
    }

    private fun formatMarks(value: Float): String =
        if (value == value.toLong().toFloat()) value.toLong().toString() else String.format("%.1f", value)

    private fun updateRowDisplay(holder: ViewHolder, moderated: ExamMarks, original: ExamMarks) {
        val hasChanged = original.writtenMarksObtained  != moderated.writtenMarksObtained ||
                original.internalMarksObtained != moderated.internalMarksObtained ||
                original.isAbsent != moderated.isAbsent

        holder.changeIndicator.visibility = if (hasChanged) View.VISIBLE else View.GONE
        holder.revertButton.visibility    = if (hasChanged) View.VISIBLE else View.GONE

        val ctx = holder.itemView.context
        if (moderated.isAbsent) {
            holder.totalPercentageText.text = "AB"
            holder.gradeText.text           = "AB"
            val red = ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
            holder.totalPercentageText.setTextColor(red)
            holder.gradeText.setTextColor(red)
        } else {
            holder.totalPercentageText.text = String.format("%.1f%%", moderated.percentage)
            holder.gradeText.text           = moderated.grade
            val colorRes = when (moderated.grade) {
                "O", "A+", "A" -> android.R.color.holo_green_dark
                "B+", "B"      -> android.R.color.holo_blue_dark
                "C"            -> android.R.color.holo_orange_dark
                else           -> android.R.color.holo_red_dark
            }
            val color = ContextCompat.getColor(ctx, colorRes)
            holder.totalPercentageText.setTextColor(color)
            holder.gradeText.setTextColor(color)
        }
    }

    override fun getItemCount(): Int = moderatedMarks.size

    fun updateLists(moderatedList: List<ExamMarks>, originalList: List<ExamMarks>) {
        moderatedMarks = moderatedList.toMutableList()
        originalMarks  = originalList.toMutableList()
        notifyDataSetChanged()
    }
}