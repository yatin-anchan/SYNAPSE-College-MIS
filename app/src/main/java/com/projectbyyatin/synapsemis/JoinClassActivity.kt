package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.R

class JoinClassActivity : AppCompatActivity() {

    private lateinit var etClassCode: TextInputEditText
    private lateinit var btnJoinClass: MaterialButton
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var isValidating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_class)

        initViews()
        setupClickListeners()
        setupTextWatcher()
    }

    private fun initViews() {
        etClassCode = findViewById(R.id.et_class_code)
        btnJoinClass = findViewById(R.id.btn_join_class)
        progressBar = findViewById(R.id.progress_bar)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        btnJoinClass.isEnabled = false
    }

    private fun setupClickListeners() {
        btnJoinClass.setOnClickListener {
            val code = etClassCode.text.toString().trim()
            if (code.isNotEmpty()) {
                joinClass(code)
            } else {
                Toast.makeText(this, "❌ Enter class code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTextWatcher() {
        etClassCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val code = s.toString().trim()
                btnJoinClass.isEnabled = code.length >= 4

                if (code.isNotEmpty() && code != code.uppercase()) {
                    etClassCode.setText(code.uppercase())
                    etClassCode.setSelection(etClassCode.text?.length ?: 0)
                }
            }
        })
    }

    private fun joinClass(inviteCode: String) {
        if (isValidating) return

        val cleanCode = inviteCode.trim().uppercase()
        Log.d("JoinClass", "Searching for code: '$cleanCode'")

        isValidating = true
        showProgress(true)
        btnJoinClass.isEnabled = false

        firestore.collection("classes")
            .whereEqualTo("inviteCode", cleanCode)
            .whereEqualTo("active", true)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("JoinClass", "Query returned ${documents.size()} results")

                if (documents.isEmpty) {
                    Toast.makeText(this, "❌ Invalid invite code", Toast.LENGTH_SHORT).show()
                } else {
                    val classDoc = documents.documents[0]
                    val classId = classDoc.id
                    val className = classDoc.getString("className") ?: "Unknown Class"

                    Log.d("JoinClass", "✅ Found class: $className (ID: $classId)")
                    updateStudentClass(classId, className)
                }
            }
            .addOnFailureListener { e ->
                Log.e("JoinClass", "Query failed", e)
                Toast.makeText(this, "❌ Network error. Try again.", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                isValidating = false
                showProgress(false)
                btnJoinClass.isEnabled = true
            }
    }

    private fun updateStudentClass(classId: String, className: String) {
        auth.currentUser?.uid?.let { userId ->
            firestore.collection("students").document(userId)
                .update(
                    "classId", classId,
                    "className", className,
                    "currentSemester", 1
                )
                .addOnSuccessListener {
                    Toast.makeText(this, "✅ Joined '$className' successfully!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("JoinClass", "Update failed", e)
                    Toast.makeText(this, "❌ Failed to join: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "❌ Please login again", Toast.LENGTH_SHORT).show()
            finish()
        }
    }


    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
