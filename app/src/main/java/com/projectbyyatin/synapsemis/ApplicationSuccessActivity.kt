package com.projectbyyatin.synapsemis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ApplicationSuccessActivity : AppCompatActivity() {

    private lateinit var tvReferenceNumber: TextView
    private lateinit var btnCopyReference: MaterialButton
    private lateinit var btnTrackStatus: MaterialButton
    private lateinit var btnGoHome: MaterialButton

    private var referenceNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_application_success)

        supportActionBar?.hide()

        referenceNumber = intent.getStringExtra("REFERENCE_NUMBER") ?: ""

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        tvReferenceNumber = findViewById(R.id.tv_reference_number)
        btnCopyReference = findViewById(R.id.btn_copy_reference)
        btnTrackStatus = findViewById(R.id.btn_track_status)
        btnGoHome = findViewById(R.id.btn_go_home)

        tvReferenceNumber.text = referenceNumber
    }

    private fun setupClickListeners() {
        btnCopyReference.setOnClickListener {
            copyToClipboard()
        }

        btnTrackStatus.setOnClickListener {
            val intent = Intent(this, TrackApplicationActivity::class.java)
            intent.putExtra("REFERENCE_NUMBER", referenceNumber)
            startActivity(intent)
            finish()
        }

        btnGoHome.setOnClickListener {
            val intent = Intent(this, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Reference Number", referenceNumber)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Reference number copied!", Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        // Disable back button
        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
