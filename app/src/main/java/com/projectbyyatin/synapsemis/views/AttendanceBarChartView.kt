package com.projectbyyatin.synapsemis.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

// ═════════════════════════════════════════════════════════════════════════════
// BAR CHART VIEW
// Usage: barChartView.setData(mapOf("01 Jan" to 85f, "02 Jan" to 60f, ...))
// ═════════════════════════════════════════════════════════════════════════════
class AttendanceBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class BarEntry(val label: String, val value: Float)  // value = 0..100

    private val entries = mutableListOf<BarEntry>()

    // ── Paints ────────────────────────────────────────────────────────────
    private val greenPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00C853") }
    private val amberPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB300") }
    private val redPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5252") }
    private val gridPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF"); strokeWidth = 1f; style = Paint.Style.STROKE
    }
    private val limitPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB300"); strokeWidth = 2f
        style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    private val labelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAFFFFFF"); textSize = 28f; textAlign = Paint.Align.CENTER
    }
    private val valuePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val limitLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB300"); textSize = 28f; textAlign = Paint.Align.RIGHT
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF"); strokeWidth = 1.5f; style = Paint.Style.STROKE
    }

    private val cornerRadius = 16f
    private val paddingLeft  = 80f
    private val paddingRight = 24f
    private val paddingTop   = 30f
    private val paddingBottom = 70f

    // ── NEW: sizing constants for horizontal scrolling ───────────────────
    private val barWidthDp = 32
    private val barGapDp   = 16
    private val extraDp    = 24

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    fun setData(data: Map<String, Float>) {
        entries.clear()
        entries.addAll(data.map { BarEntry(it.key, it.value.coerceIn(0f, 100f)) })
        requestLayout()   // IMPORTANT: re-measure for scroll
        invalidate()
    }

    // ── NEW: enable HorizontalScrollView to work correctly ────────────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = entries.size.coerceAtLeast(1)
        val desiredWidth =
            dp(barWidthDp + barGapDp) * count + dp(extraDp)

        val resolvedWidth  = resolveSize(desiredWidth, widthMeasureSpec)
        val resolvedHeight = MeasureSpec.getSize(heightMeasureSpec)

        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val chartLeft   = paddingLeft
        val chartRight  = w - paddingRight
        val chartTop    = paddingTop
        val chartBottom = h - paddingBottom
        val chartH      = chartBottom - chartTop
        val chartW      = chartRight - chartLeft

        // ── Grid lines at 0, 25, 50, 75, 100 ───────────────────────────
        val gridValues = listOf(0, 25, 50, 75, 100)
        gridValues.forEach { v ->
            val y = chartBottom - (v / 100f * chartH)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            val axisLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#88FFFFFF"); textSize = 26f; textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("$v%", chartLeft - 8f, y + 9f, axisLabel)
        }

        // ── 75% limit line ───────────────────────────────────────────────
        val limitY = chartBottom - (75f / 100f * chartH)
        canvas.drawLine(chartLeft, limitY, chartRight, limitY, limitPaint)
        canvas.drawText("75%", chartRight, limitY - 6f, limitLabelPaint)

        // ── Axes ─────────────────────────────────────────────────────────
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        // ── Bars ─────────────────────────────────────────────────────────
        val totalBars  = entries.size
        val barSpacing = chartW / totalBars
        val barWidth   = barSpacing * 0.6f

        entries.forEachIndexed { i, entry ->
            val barLeft   = chartLeft + i * barSpacing + barSpacing * 0.2f
            val barRight  = barLeft + barWidth
            val barTop    = chartBottom - (entry.value / 100f * chartH)

            val paint = when {
                entry.value >= 75f -> greenPaint
                entry.value >= 60f -> amberPaint
                else               -> redPaint
            }

            val rect = RectF(barLeft, barTop, barRight, chartBottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            if (entry.value > 8f) {
                canvas.drawText("${entry.value.toInt()}%", barLeft + barWidth / 2, barTop - 6f, valuePaint)
            }

            canvas.drawText(entry.label, barLeft + barWidth / 2, h - 10f, labelPaint)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PIE / DONUT CHART VIEW
// (UNCHANGED)
// ═════════════════════════════════════════════════════════════════════════════
class AttendancePieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class PieEntry(val label: String, val value: Float, val color: Int)

    private val slices = mutableListOf<PieEntry>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#1AFFFFFF"); strokeWidth = 3f
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 42f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAFFFFFF"); textSize = 28f; textAlign = Paint.Align.CENTER
    }
    private val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f
    }
    private val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var centerLabel = ""
    private var centerSubLabel = ""

    fun setData(data: List<Pair<String, Float>>, colors: List<Int>? = null) {
        slices.clear()
        val defaultColors = listOf(
            Color.parseColor("#00C853"),
            Color.parseColor("#FF5252"),
            Color.parseColor("#FFB300"),
            Color.parseColor("#2979FF"),
            Color.parseColor("#AA00FF")
        )
        val total = data.sumOf { it.second.toDouble() }.toFloat()
        data.forEachIndexed { i, pair ->
            slices.add(PieEntry(pair.first, pair.second,
                colors?.getOrElse(i) { defaultColors[i % defaultColors.size] }
                    ?: defaultColors[i % defaultColors.size]))
        }
        val present = data.firstOrNull { it.first == "Present" }?.second ?: 0f
        val pct = if (total > 0) (present / total * 100).toInt() else 0
        centerLabel = "$pct%"
        centerSubLabel = "Attendance"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val legendH = slices.size * 50f + 20f
        val chartH = h - legendH
        val radius = min(w, chartH) * 0.42f
        val cx = w / 2f
        val cy = chartH / 2f

        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        var startAngle = -90f

        slices.forEach {
            val sweep = if (total > 0) it.value / total * 360f else 0f
            paint.color = it.color
            canvas.drawArc(oval, startAngle, sweep, true, paint)
            canvas.drawArc(oval, startAngle, sweep, true, strokePaint)
            startAngle += sweep
        }

        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A2E")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius * 0.58f, holePaint)

        canvas.drawText(centerLabel, cx, cy + 14f, centerTextPaint)
        canvas.drawText(centerSubLabel, cx, cy + 48f, centerSubPaint)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// STUDENT MINI TREND SPARKLINE
// (UNCHANGED)
// ═════════════════════════════════════════════════════════════════════════════
class AttendanceSparklineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val points = mutableListOf<Float>()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }

    fun setData(values: List<Float>) {
        points.clear()
        points.addAll(values.map { it.coerceIn(0f, 100f) })
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 6f
        val minV = points.min()
        val maxV = points.max()
        val range = (maxV - minV).let { if (it < 1f) 1f else it }

        fun xOf(i: Int) = pad + i * (w - 2 * pad) / (points.size - 1)
        fun yOf(v: Float) = h - pad - ((v - minV) / range) * (h - 2 * pad)

        val trendColor = when {
            points.last() - points.first() > 3f -> Color.parseColor("#00C853")
            points.first() - points.last() > 3f -> Color.parseColor("#FF5252")
            else -> Color.parseColor("#FFB300")
        }

        val fillPath = Path().apply {
            moveTo(xOf(0), h)
            points.forEachIndexed { i, v -> lineTo(xOf(i), yOf(v)) }
            lineTo(xOf(points.size - 1), h)
            close()
        }

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.argb(120, Color.red(trendColor), Color.green(trendColor), Color.blue(trendColor)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        linePaint.color = trendColor
        val linePath = Path()
        points.forEachIndexed { i, v ->
            if (i == 0) linePath.moveTo(xOf(i), yOf(v)) else linePath.lineTo(xOf(i), yOf(v))
        }
        canvas.drawPath(linePath, linePaint)

        points.forEachIndexed { i, v ->
            canvas.drawCircle(xOf(i), yOf(v), 5f, dotPaint)
        }
    }
}