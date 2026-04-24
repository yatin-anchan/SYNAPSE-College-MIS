package com.projectbyyatin.synapsemis

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.projectbyyatin.synapsemis.ResultDisplayActivity.StudentResult
import com.projectbyyatin.synapsemis.ResultDisplayActivity.SubjectResult
import com.projectbyyatin.synapsemis.models.ExamMarks
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ResultPdfGenerator(private val context: Context) {

    // A4 Landscape in points (1pt = 1/72 inch; Android PdfDocument uses pixels at 72dpi)
    private val PW = 842f   // ~11.69 inches
    private val PH = 595f   // ~8.27 inches
    private val M  = 28f    // margin

    // ── Colors ───────────────────────────────────────────────────────────────
    private val NAVY       = Color.parseColor("#1A237E")
    private val NAVY_LIGHT = Color.parseColor("#E8EAF6")
    private val ROW_ALT    = Color.parseColor("#F3F4FB")
    private val FOOTER_BG  = Color.parseColor("#BBDEFB")
    private val GREEN      = Color.parseColor("#1B5E20")
    private val BLUE_DARK  = Color.parseColor("#0D47A1")
    private val ORANGE     = Color.parseColor("#E65100")
    private val RED        = Color.parseColor("#B71C1C")
    private val GREY       = Color.parseColor("#757575")

    // ── Text size helper (pt → pixels at 72dpi, Android PDF canvas scale) ───
    // PdfDocument canvas units ≈ 1/72 inch = 1pt. So just use pt directly.
    private fun sp(pt: Float) = pt

    private fun gradeColor(g: String) = when (g) {
        "O", "A+"      -> GREEN
        "A"            -> Color.parseColor("#2E7D32")
        "B+", "B"      -> BLUE_DARK
        "C"            -> ORANGE
        else           -> RED
    }

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    fun generate(
        file      : File,
        students  : List<StudentResult>,
        deptName  : String,
        courseName: String,
        semester  : Int,
        college   : String = "ROYAL COLLEGE OF ARTS, SCIENCE AND COMMERCE, MIRA ROAD (EAST)"
    ) {
        val doc = PdfDocument()
        students.forEachIndexed { i, sr ->
            val info = PdfDocument.PageInfo.Builder(PW.toInt(), PH.toInt(), i + 1).create()
            val page = doc.startPage(info)
            drawPage(page.canvas, sr, college, deptName, courseName, semester)
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    // ─── PAGE DRAW ───────────────────────────────────────────────────────────

    private fun drawPage(
        cv      : Canvas,
        sr      : StudentResult,
        college : String,
        dept    : String,
        course  : String,
        semester: Int
    ) {
        cv.drawColor(Color.WHITE)
        var y = M

        // ── Header ────────────────────────────────────────────────────────────
        y = drawCentered(cv, college.uppercase(),                         y, sp(9f),  true,  Color.BLACK)
        y = drawCentered(cv, "Department of ${dept.uppercase()}",         y, sp(8f),  false, NAVY)
        y = drawCentered(cv, course.uppercase(),                          y, sp(8f),  false, NAVY)
        y = drawCentered(cv, "Semester $semester  —  Examination Results",y, sp(9f),  true,  NAVY)

        // Divider
        cv.drawLine(M, y + 3, PW - M, y + 3, strokePaint(NAVY, 1.5f))
        y += 10f

        // ── Student info strip ────────────────────────────────────────────────
        cv.drawRect(M, y, PW - M, y + 20f, fillPaint(NAVY_LIGHT))
        cv.drawText(
            "Roll No: ${sr.rollNo}",
            M + 6f, y + 14f,
            textPaint(NAVY, sp(9f), bold = true)
        )
        cv.drawText(
            "Name: ${sr.studentName}",
            PW - M - 6f, y + 14f,
            textPaint(NAVY, sp(9f), bold = true, align = Paint.Align.RIGHT)
        )
        y += 24f

        // ── Table ─────────────────────────────────────────────────────────────
        // Column weight fractions (must sum to 1.0)
        val hasPractical = sr.subjects.any { it.practicalMax > 0 }

        val colFractions: List<Float>
        val headers: List<String>

        if (hasPractical) {
            // Show WRT | INT | PRC | TOT | % | GR
            colFractions = listOf(0.07f, 0.25f, 0.10f, 0.10f, 0.10f, 0.13f, 0.10f, 0.07f, 0.08f)
            headers      = listOf("CODE", "SUBJECT", "WRT", "INT", "PRC", "TOTAL", "%", "GR", "GP")
        } else {
            // Theory-only: hide PRC column
            colFractions = listOf(0.08f, 0.28f, 0.13f, 0.13f, 0.15f, 0.10f, 0.07f, 0.06f)
            headers      = listOf("CODE", "SUBJECT", "WRT", "INT", "TOTAL", "%", "GR", "GP")
        }

        val tableW = PW - 2 * M
        val colW   = colFractions.map { it * tableW }
        val colX   = List(colW.size) { i -> M + colW.take(i).sum() }
        val rowH   = 17f
        val hdrH   = 18f

        // Header row background
        cv.drawRect(M, y, PW - M, y + hdrH, fillPaint(NAVY))
        headers.forEachIndexed { i, h ->
            cv.drawText(
                h,
                colX[i] + colW[i] / 2f,
                y + 13f,
                textPaint(Color.WHITE, sp(7f), bold = true, align = Paint.Align.CENTER)
            )
        }
        y += hdrH

        // Data rows
        sr.subjects.forEachIndexed { idx, s ->
            if (idx % 2 == 1) cv.drawRect(M, y, PW - M, y + rowH, fillPaint(ROW_ALT))

            val rowValues: List<String>
            if (hasPractical) {
                rowValues = listOf(
                    s.subjectCode,
                    s.subjectName,
                    if (s.writtenMax  > 0) "${fmt(s.writtenObt)}/${s.writtenMax}"   else "--",
                    if (s.internalMax > 0) "${fmt(s.internalObt)}/${s.internalMax}" else "--",
                    if (s.practicalMax> 0) "${fmt(s.practicalObt)}/${s.practicalMax}" else "--",
                    "${fmt(s.totalObt)}/${s.totalMax}",
                    "%.1f".format(s.percentage),
                    s.grade,
                    "%.1f".format(s.gradePoints)
                )
            } else {
                rowValues = listOf(
                    s.subjectCode,
                    s.subjectName,
                    if (s.writtenMax  > 0) "${fmt(s.writtenObt)}/${s.writtenMax}"   else "--",
                    if (s.internalMax > 0) "${fmt(s.internalObt)}/${s.internalMax}" else "--",
                    "${fmt(s.totalObt)}/${s.totalMax}",
                    "%.1f".format(s.percentage),
                    s.grade,
                    "%.1f".format(s.gradePoints)
                )
            }

            rowValues.forEachIndexed { i, v ->
                // Grade column — use colored text
                val isGradeCol = (hasPractical && i == 7) || (!hasPractical && i == 6)
                val paint = if (isGradeCol)
                    textPaint(gradeColor(v), sp(7.5f), bold = true, align = Paint.Align.CENTER)
                else
                    textPaint(Color.BLACK, sp(7.5f), bold = false, align = Paint.Align.CENTER)

                // Subject name — left-align
                val finalPaint = if (i == 1) paint.apply { textAlign = Paint.Align.LEFT } else paint
                val xPos = if (i == 1) colX[i] + 2f else colX[i] + colW[i] / 2f

                cv.drawText(v, xPos, y + 12f, finalPaint)
            }

            // Row bottom border
            cv.drawLine(M, y + rowH, PW - M, y + rowH, strokePaint(Color.parseColor("#E0E0E0"), 0.5f))
            y += rowH
        }

        // ── Footer summary ────────────────────────────────────────────────────
        y += 8f
        cv.drawRect(M, y, PW - M, y + 24f, fillPaint(FOOTER_BG))

        val totalObt = sr.subjects.sumOf { it.totalObt.toDouble() }.toFloat()
        val totalMax = sr.subjects.sumOf { it.totalMax.toDouble() }.toFloat()
        val pct      = if (totalMax > 0f) (totalObt / totalMax) * 100f else 0f
        val overGrade = ExamMarks.calculateGrade(pct)

        cv.drawText(
            "Grand Total: ${fmt(totalObt)} / ${fmt(totalMax)}",
            M + 6f, y + 16f,
            textPaint(NAVY, sp(8.5f), bold = true)
        )
        cv.drawText(
            "Percentage: ${"%.2f".format(pct)}%",
            PW / 2f, y + 16f,
            textPaint(NAVY, sp(8.5f), bold = true, align = Paint.Align.CENTER)
        )
        cv.drawText(
            "CGPI: ${"%.2f".format(sr.cgpi)} / 10",
            PW - M - 6f, y + 16f,
            textPaint(NAVY, sp(8.5f), bold = true, align = Paint.Align.RIGHT)
        )
        y += 30f

        // ── Overall grade + result class ──────────────────────────────────────
        val resultClass = when {
            sr.cgpi >= 8.5f -> "DISTINCTION"
            sr.cgpi >= 7.0f -> "FIRST CLASS"
            sr.cgpi >= 6.0f -> "SECOND CLASS"
            sr.cgpi >= 4.0f -> "PASS"
            else            -> "FAIL"
        }
        cv.drawText(
            "Grade: $overGrade   |   Result: $resultClass",
            PW / 2f, y,
            textPaint(gradeColor(overGrade), sp(10f), bold = true, align = Paint.Align.CENTER)
        )

        // ── Footer line ───────────────────────────────────────────────────────
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        cv.drawLine(M, PH - 18f, PW - M, PH - 18f, strokePaint(GREY, 0.5f))
        cv.drawText(
            "Generated on $today  •  SynapsMIS",
            M, PH - 8f,
            textPaint(GREY, sp(6.5f))
        )
        cv.drawText(
            "Page 1",
            PW - M, PH - 8f,
            textPaint(GREY, sp(6.5f), align = Paint.Align.RIGHT)
        )
    }

    // ─── PAINT HELPERS ────────────────────────────────────────────────────────

    private fun textPaint(
        color: Int,
        size : Float,
        bold : Boolean        = false,
        align: Paint.Align    = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color     = color
        textSize       = size
        textAlign      = align
        typeface       = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fillPaint(c: Int)         = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }
    private fun strokePaint(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = c
        style       = Paint.Style.STROKE
        strokeWidth = w
    }

    private fun drawCentered(
        cv   : Canvas,
        text : String,
        y    : Float,
        size : Float,
        bold : Boolean,
        color: Int
    ): Float {
        cv.drawText(text, PW / 2f, y + size + 2f, textPaint(color, size, bold, Paint.Align.CENTER))
        return y + size + 7f
    }

    private fun fmt(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString()
        else "%.1f".format(v)
}