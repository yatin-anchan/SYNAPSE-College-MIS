package com.projectbyyatin.synapsemis.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.ExamMarks

class ModeratorMarksAdapter(
    private var marksList: List<ExamMarks>,
    private val maxMarks: Int,
    private var isLocked: Boolean,
    private val onMarksChanged: () -> Unit
) : RecyclerView.Adapter<ModeratorMarksAdapter.ModeratorViewHolder>() {

    private val modifiedItems = mutableSetOf<String>()

    inner class ModeratorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rollNo: TextView = itemView.findViewById(R.id.roll_no)
        val studentName: TextView = itemView.findViewById(R.id.student_name)
        val marksInput: EditText = itemView.findViewById(R.id.marks_input)
        val maxMarksText: TextView = itemView.findViewById(R.id.max_marks_text)
        val percentageText: TextView = itemView.findViewById(R.id.percentage_text)
        val gradeText: TextView = itemView.findViewById(R.id.grade_text)
        val absentCheckbox: CheckBox = itemView.findViewById(R.id.absent_checkbox)
        val originalMarksText: TextView = itemView.findViewById(R.id.original_marks_text)
        val modifiedBadge: View = itemView.findViewById(R.id.modified_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModeratorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_moderator_marks_entry, parent, false)
        return ModeratorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModeratorViewHolder, position: Int) {
        val marks = marksList[position]

        holder.rollNo.text = marks.studentRollNo
        holder.studentName.text = marks.studentName
        holder.maxMarksText.text = "/ $maxMarks"

        // Show original marks if moderated
        // Use totalMarksObtained as the current mark and previousWrittenMarks + previousInternalMarks as the original
        val previousTotal = marks.previousWrittenMarks + marks.previousInternalMarks
        if (marks.isModerated && previousTotal > 0) {
            holder.originalMarksText.visibility = View.VISIBLE
            holder.originalMarksText.text = "Original: $previousTotal"
            holder.modifiedBadge.visibility = View.VISIBLE
        } else {
            holder.originalMarksText.visibility = View.GONE
            holder.modifiedBadge.visibility = View.GONE
        }

        // Remove previous text watcher
        holder.marksInput.tag?.let {
            holder.marksInput.removeTextChangedListener(it as TextWatcher)
        }

        // For display/editing we use writtenMarksObtained as the primary editable field.
        // If the exam has only an internal component, fall back to internalMarksObtained.
        val currentDisplayMarks = if (marks.hasWrittenComponent) {
            marks.writtenMarksObtained
        } else {
            marks.internalMarksObtained
        }

        if (currentDisplayMarks > 0) {
            holder.marksInput.setText(currentDisplayMarks.toString())
        } else {
            holder.marksInput.setText("")
        }

        holder.absentCheckbox.isChecked = marks.isAbsent

        // Update percentage and grade
        updatePercentageAndGrade(holder, marks)

        // Moderators can always edit (unless system locked)
        holder.marksInput.isEnabled = !isLocked && !marks.isAbsent
        holder.absentCheckbox.isEnabled = !isLocked

        // Add text watcher for marks input
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()

                if (input.isEmpty()) {
                    setMarksValue(marks, 0f)
                } else {
                    try {
                        val value = input.toFloat()
                        when {
                            value > maxMarks -> {
                                holder.marksInput.error = "Cannot exceed $maxMarks"
                                setMarksValue(marks, maxMarks.toFloat())
                                holder.marksInput.setText(maxMarks.toString())
                            }
                            value < 0 -> {
                                holder.marksInput.error = "Cannot be negative"
                                setMarksValue(marks, 0f)
                                holder.marksInput.setText("")
                            }
                            else -> {
                                setMarksValue(marks, value)
                                holder.marksInput.error = null
                            }
                        }
                    } catch (e: NumberFormatException) {
                        setMarksValue(marks, 0f)
                    }
                }

                modifiedItems.add(marks.id)
                updatePercentageAndGrade(holder, marks)
                onMarksChanged()
            }
        }

        holder.marksInput.tag = textWatcher
        holder.marksInput.addTextChangedListener(textWatcher)

        // Absent checkbox listener
        holder.absentCheckbox.setOnCheckedChangeListener { _, isChecked ->
            marks.isAbsent = isChecked

            if (isChecked) {
                setMarksValue(marks, 0f)
                holder.marksInput.setText("")
                holder.marksInput.isEnabled = false
            } else {
                holder.marksInput.isEnabled = !isLocked
            }

            modifiedItems.add(marks.id)
            updatePercentageAndGrade(holder, marks)
            onMarksChanged()
        }
    }

    /**
     * Sets the appropriate marks field(s) based on the exam type,
     * then recalculates totals, percentage, cgpi, and grade.
     */
    private fun setMarksValue(marks: ExamMarks, value: Float) {
        when {
            marks.hasWrittenComponent && marks.hasInternalComponent -> {
                // Combined: edit only the written portion here; internal unchanged
                marks.writtenMarksObtained = value
            }
            marks.hasWrittenComponent -> {
                marks.writtenMarksObtained = value
            }
            else -> {
                marks.internalMarksObtained = value
            }
        }
        // Recalculate derived fields
        val total = marks.writtenMarksObtained + marks.internalMarksObtained
        val maxTotal = marks.writtenMaxMarks + marks.internalMaxMarks
        marks.totalMarksObtained = total
        marks.totalMaxMarks = maxTotal
        marks.percentage = if (maxTotal > 0) (total / maxTotal) * 100f else 0f
        marks.cgpi = if (maxTotal > 0) (total / maxTotal) * 10f else 0f
        marks.grade = ExamMarks.calculateGrade(marks.percentage)
    }

    override fun getItemCount(): Int = marksList.size

    private fun updatePercentageAndGrade(holder: ModeratorViewHolder, marks: ExamMarks) {
        if (marks.isAbsent) {
            holder.percentageText.text = "AB"
            holder.gradeText.text = "AB"
            holder.percentageText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
            holder.gradeText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
        } else if (marks.totalMarksObtained > 0) {
            holder.percentageText.text = String.format("%.1f%%", marks.percentage)
            holder.gradeText.text = marks.grade

            val color = when (marks.grade) {
                "O", "A+", "A" -> android.R.color.holo_green_dark
                "B+", "B"      -> android.R.color.holo_blue_dark
                "C"            -> android.R.color.holo_orange_dark
                else           -> android.R.color.holo_red_dark
            }
            holder.percentageText.setTextColor(holder.itemView.context.getColor(color))
            holder.gradeText.setTextColor(holder.itemView.context.getColor(color))
        } else {
            holder.percentageText.text = "-"
            holder.gradeText.text = "-"
            holder.percentageText.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
            holder.gradeText.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        }
    }

    fun updateList(newList: List<ExamMarks>) {
        marksList = newList
        notifyDataSetChanged()
    }

    fun setLocked(locked: Boolean) {
        isLocked = locked
        notifyDataSetChanged()
    }

    fun hasChanges(): Boolean = modifiedItems.isNotEmpty()

    fun getModifiedCount(): Int = modifiedItems.size
}