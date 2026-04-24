package com.projectbyyatin.synapsemis.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.projectbyyatin.synapsemis.R
import kotlin.math.max
import kotlin.math.min

class SlideToConfirmView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Customizable properties
    private var slideText = "Slide to Publish"
    private var slideCompletedText = "✓ Published"
    private var slideThreshold = 0.85f

    // Colors
    private var backgroundColor: Int
    private var progressColor: Int
    private var thumbColor: Int
    private var textColor: Int
    private var completedColor: Int
    private var textSize: Float

    // Paint objects
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // State variables
    private var thumbX = 0f
    private var thumbRadius = 0f
    private var isSliding = false
    private var isCompleted = false

    private var onSlideCompleteListener: (() -> Unit)? = null

    init {
        // Read custom attributes
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SlideToConfirmView)

        slideText = typedArray.getString(R.styleable.SlideToConfirmView_slideText) ?: "Slide to Publish"
        slideCompletedText = typedArray.getString(R.styleable.SlideToConfirmView_slideCompletedText) ?: "✓ Published"
        slideThreshold = typedArray.getFloat(R.styleable.SlideToConfirmView_slideThreshold, 0.85f)

        backgroundColor = typedArray.getColor(
            R.styleable.SlideToConfirmView_slideBackgroundColor,
            ContextCompat.getColor(context, R.color.splash_card_background)
        )

        progressColor = typedArray.getColor(
            R.styleable.SlideToConfirmView_slideProgressColor,
            ContextCompat.getColor(context, R.color.splash_primary)
        )

        thumbColor = typedArray.getColor(
            R.styleable.SlideToConfirmView_slideThumbColor,
            ContextCompat.getColor(context, R.color.white)
        )

        textColor = typedArray.getColor(
            R.styleable.SlideToConfirmView_slideTextColor,
            ContextCompat.getColor(context, R.color.splash_text_primary)
        )

        completedColor = typedArray.getColor(
            R.styleable.SlideToConfirmView_slideCompletedColor,
            ContextCompat.getColor(context, R.color.splash_success)
        )

        textSize = typedArray.getDimension(
            R.styleable.SlideToConfirmView_slideTextSize,
            48f
        )

        typedArray.recycle()

        // Initialize paints
        backgroundPaint.apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }

        progressPaint.apply {
            color = progressColor
            style = Paint.Style.FILL
        }

        thumbPaint.apply {
            color = thumbColor
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 4f, 0x40000000)
        }

        textPaint.apply {
            color = textColor
            this.textSize = this@SlideToConfirmView.textSize
            textAlign = Paint.Align.CENTER
        }

        iconPaint.apply {
            color = thumbColor
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }

        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        thumbRadius = (h / 2f) - 16f
        thumbX = thumbRadius + 16f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cornerRadius = height / 2f
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // Draw background
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)

        // Draw progress
        val progressRect = RectF(0f, 0f, thumbX + thumbRadius, height.toFloat())
        canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint)

        // Draw text
        val displayText = if (isCompleted) slideCompletedText else slideText
        val currentTextColor = if (isCompleted) completedColor else textColor
        textPaint.color = currentTextColor

        val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(displayText, width / 2f, textY, textPaint)

        // Draw thumb
        canvas.drawCircle(thumbX, height / 2f, thumbRadius, thumbPaint)

        // Draw icon on thumb
        if (!isCompleted) {
            drawArrowIcon(canvas, thumbX, height / 2f)
        } else {
            drawCheckIcon(canvas, thumbX, height / 2f)
        }
    }

    private fun drawArrowIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        val size = thumbRadius * 0.4f

        iconPaint.color = progressColor

        // Draw arrow pointing right
        canvas.drawLine(
            centerX - size, centerY,
            centerX + size, centerY,
            iconPaint
        )
        canvas.drawLine(
            centerX + size - size * 0.5f, centerY - size * 0.5f,
            centerX + size, centerY,
            iconPaint
        )
        canvas.drawLine(
            centerX + size - size * 0.5f, centerY + size * 0.5f,
            centerX + size, centerY,
            iconPaint
        )
    }

    private fun drawCheckIcon(canvas: Canvas, centerX: Float, centerY: Float) {
        iconPaint.color = completedColor
        val size = thumbRadius * 0.4f

        // Draw check mark
        canvas.drawLine(
            centerX - size, centerY,
            centerX - size * 0.3f, centerY + size * 0.6f,
            iconPaint
        )
        canvas.drawLine(
            centerX - size * 0.3f, centerY + size * 0.6f,
            centerX + size, centerY - size * 0.4f,
            iconPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCompleted) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val distance = kotlin.math.abs(touchX - thumbX)
                if (distance <= thumbRadius * 1.5f) {
                    isSliding = true
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isSliding) {
                    val newX = event.x
                    val maxX = width - thumbRadius - 16f
                    val minX = thumbRadius + 16f

                    thumbX = max(minX, min(newX, maxX))
                    invalidate()

                    // Check if completed
                    val progress = (thumbX - minX) / (maxX - minX)
                    if (progress >= slideThreshold) {
                        completeSlide()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSliding && !isCompleted) {
                    // Animate back to start
                    animateToStart()
                    isSliding = false
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    private fun completeSlide() {
        isCompleted = true
        isSliding = false

        // Animate to end
        val endX = width - thumbRadius - 16f
        ValueAnimator.ofFloat(thumbX, endX).apply {
            duration = 200
            addUpdateListener { animator ->
                thumbX = animator.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Trigger callback after animation
        postDelayed({
            onSlideCompleteListener?.invoke()
        }, 300)
    }

    private fun animateToStart() {
        val startX = thumbRadius + 16f
        ValueAnimator.ofFloat(thumbX, startX).apply {
            duration = 300
            addUpdateListener { animator ->
                thumbX = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * Reset the slide to confirm view to its initial state
     */
    fun reset() {
        isCompleted = false
        isSliding = false
        thumbX = thumbRadius + 16f
        invalidate()
    }

    /**
     * Set listener for slide complete event
     */
    fun setOnSlideCompleteListener(listener: () -> Unit) {
        onSlideCompleteListener = listener
    }

    /**
     * Update the slide text programmatically
     */
    fun setSlideText(text: String) {
        slideText = text
        invalidate()
    }

    /**
     * Update the completed text programmatically
     */
    fun setCompletedText(text: String) {
        slideCompletedText = text
        invalidate()
    }

    /**
     * Get current completion state
     */
    fun isCompleted(): Boolean = isCompleted
}