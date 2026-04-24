package com.projectbyyatin.synapsemis

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.projectbyyatin.synapsemis.R

class CustomAlertDialog(
    private val context: Context,
    private val title: String,
    private val message: String,
    private val positiveButtonText: String = "OK",
    private val negativeButtonText: String? = null,
    private val onPositiveClick: (() -> Unit)? = null,
    private val onNegativeClick: (() -> Unit)? = null
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_alert, null)
        setContentView(dialogView)

        setupDialog()
        setupViews(dialogView)
    }

    private fun setupDialog() {
        // Rounded corners + transparent background
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.attributes?.windowAnimations = R.style.DialogAnimation
        setCanceledOnTouchOutside(true)
    }

    private fun setupViews(dialogView: android.view.View) {
        // Title
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)

        tvTitle.text = title
        tvMessage.text = message

        // Positive Button (Always shown)
        val btnPositive = dialogView.findViewById<Button>(R.id.btnPositive)
        btnPositive.text = positiveButtonText
        btnPositive.setOnClickListener {
            dismiss()
            onPositiveClick?.invoke()
        }

        // Negative Button (Optional)
        val btnNegative = dialogView.findViewById<Button>(R.id.btnNegative)
        if (negativeButtonText != null) {
            btnNegative.text = negativeButtonText
            btnNegative.visibility = android.view.View.VISIBLE
            btnNegative.setOnClickListener {
                dismiss()
                onNegativeClick?.invoke()
            }
        } else {
            btnNegative.visibility = android.view.View.GONE
        }
    }
}
