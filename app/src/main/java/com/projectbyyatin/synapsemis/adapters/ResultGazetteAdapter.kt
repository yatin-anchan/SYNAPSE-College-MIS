package com.projectbyyatin.synapsemis.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.ResultDisplayActivity.StudentResult

class ResultGazetteAdapter(
    private val results: List<StudentResult>
) : RecyclerView.Adapter<ResultGazetteAdapter.ViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val rollNo           : TextView     = v.findViewById(R.id.result_roll_no)
        val name             : TextView     = v.findViewById(R.id.result_student_name)
        val cgpi             : TextView     = v.findViewById(R.id.result_cgpi)
        val badge            : TextView     = v.findViewById(R.id.result_badge)
        val expandIcon       : TextView     = v.findViewById(R.id.result_expand_icon)
        val expandLabel      : TextView     = v.findViewById(R.id.result_expand_label)
        val gradeStrip       : View         = v.findViewById(R.id.grade_strip)
        val subjectContainer : LinearLayout = v.findViewById(R.id.subject_breakdown_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result_student, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val sr = results[pos]

        h.rollNo.text = sr.rollNo
        h.name.text   = sr.studentName
        h.cgpi.text   = "CGPI  ${"%.2f".format(sr.cgpi)} / 10"

        // Badge + strip color
        val (badgeText, color) = when {
            sr.cgpi >= 8.5f -> "DISTINCTION"  to "#4CAF50"
            sr.cgpi >= 7.0f -> "FIRST CLASS"  to "#2196F3"
            sr.cgpi >= 6.0f -> "SECOND CLASS" to "#00BCD4"
            sr.cgpi >= 4.0f -> "PASS"         to "#FF9800"
            else            -> "FAIL"         to "#F44336"
        }
        val parsedColor = Color.parseColor(color)
        h.badge.text = badgeText
        h.badge.setBackgroundColor(parsedColor)
        h.gradeStrip.setBackgroundColor(parsedColor)

        // Expand / collapse
        val expanded = expandedPositions.contains(pos)
        h.subjectContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        h.expandIcon.text             = if (expanded) "  ▲" else "  ▼"
        h.expandLabel.text            = if (expanded) "Hide Subjects" else "View Subjects"

        h.itemView.setOnClickListener {
            if (expanded) expandedPositions.remove(pos) else expandedPositions.add(pos)
            notifyItemChanged(pos)
        }

        // Subject rows
        h.subjectContainer.removeAllViews()

        if (expanded) {
            if (sr.subjects.isEmpty()) {
                val noData = TextView(h.itemView.context).apply {
                    text     = "No subject data available"
                    textSize = 12f
                    setTextColor(Color.parseColor("#88FFFFFF"))
                    setPadding(8, 16, 8, 16)
                }
                h.subjectContainer.addView(noData)
                return
            }

            sr.subjects.forEachIndexed { idx, sub ->
                val row = LayoutInflater.from(h.itemView.context)
                    .inflate(R.layout.item_result_subject_row, h.subjectContainer, false)

                if (idx % 2 == 0) row.setBackgroundColor(Color.parseColor("#0D1133"))

                row.findViewById<TextView>(R.id.sub_code_result).text     = sub.subjectCode.ifBlank { "—" }
                row.findViewById<TextView>(R.id.sub_name_result).text     = sub.subjectName.ifBlank { "Unknown" }

                row.findViewById<TextView>(R.id.sub_written_result).text  =
                    if (sub.writtenMax > 0) "${fmt(sub.writtenObt)}/${sub.writtenMax}" else "--"

                row.findViewById<TextView>(R.id.sub_internal_result).text =
                    if (sub.internalMax > 0) "${fmt(sub.internalObt)}/${sub.internalMax}" else "--"

                row.findViewById<TextView>(R.id.sub_practical_result).text =
                    if (sub.practicalMax > 0) "${fmt(sub.practicalObt)}/${sub.practicalMax}" else "--"

                row.findViewById<TextView>(R.id.sub_total_result).text   =
                    "${fmt(sub.totalObt)}/${sub.totalMax}"

                row.findViewById<TextView>(R.id.sub_percent_result)?.text =
                    "${"%.1f".format(sub.percentage)}%"

                val gradeView = row.findViewById<TextView>(R.id.sub_grade_result)
                gradeView.text = sub.grade
                gradeView.setTextColor(gradeColor(sub.grade))

                h.subjectContainer.addView(row)
            }
        }
    }

    override fun getItemCount() = results.size

    private fun fmt(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(v)

    private fun gradeColor(g: String) = when (g) {
        "O"  -> Color.parseColor("#4CAF50")
        "A+" -> Color.parseColor("#8BC34A")
        "A"  -> Color.parseColor("#CDDC39")
        "B+" -> Color.parseColor("#2196F3")
        "B"  -> Color.parseColor("#03A9F4")
        "C"  -> Color.parseColor("#FF9800")
        "D"  -> Color.parseColor("#FF5722")
        else -> Color.parseColor("#F44336")
    }
}