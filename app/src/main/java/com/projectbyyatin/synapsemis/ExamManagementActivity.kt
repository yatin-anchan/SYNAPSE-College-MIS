    package com.projectbyyatin.synapsemis

    import android.content.Intent
    import android.os.Bundle
    import androidx.appcompat.app.AppCompatActivity
    import androidx.appcompat.widget.Toolbar
    import androidx.cardview.widget.CardView
    import com.projectbyyatin.synapsemis.R

    class ExamManagementActivity : AppCompatActivity() {

        private lateinit var toolbar: Toolbar
        private lateinit var cardCreateExam: CardView
        private lateinit var cardManageExams: CardView
        private lateinit var cardExamHistory: CardView

        private lateinit var cardResultGeneration: CardView
        private lateinit var cardTrackMarks: CardView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_exam_management)

            initializeViews()
            setupToolbar()
            setupCards()
        }

        private fun initializeViews() {
            toolbar = findViewById(R.id.toolbar)
            cardCreateExam = findViewById(R.id.card_create_exam)
            cardManageExams = findViewById(R.id.card_manage_exams)
            cardExamHistory = findViewById(R.id.card_exam_history)
            cardResultGeneration = findViewById(R.id.resultgeneration)
            cardTrackMarks = findViewById(R.id.trackmarks)
        }

        private fun setupToolbar() {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Exam Management"
            toolbar.setNavigationOnClickListener { finish() }
        }

        private fun setupCards() {
            cardCreateExam.setOnClickListener {
                startActivity(Intent(this, ExamCreationStep1Activity ::class.java))
            }

            cardManageExams.setOnClickListener {
                startActivity(Intent(this, ManageExamsActivity::class.java))
            }

            cardExamHistory.setOnClickListener {
                startActivity(Intent(this, ExamHistoryActivity::class.java))
            }

            cardResultGeneration.setOnClickListener {
                startActivity(Intent(this, GenerateResultActivity::class.java))
            }
            cardTrackMarks.setOnClickListener {
                startActivity(Intent(this, SelectExamTrackActivity::class.java))
            }
        }
    }
