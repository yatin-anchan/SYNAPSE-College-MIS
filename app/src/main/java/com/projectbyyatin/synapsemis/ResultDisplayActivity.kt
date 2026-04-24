package com.projectbyyatin.synapsemis

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.ResultGazetteAdapter
import com.projectbyyatin.synapsemis.models.ExamMarks
import com.projectbyyatin.synapsemis.models.ExamMarks.Companion.calculateGrade
import java.io.File

class ResultDisplayActivity : AppCompatActivity() {

    private lateinit var recyclerView    : RecyclerView
    private lateinit var loadingProgress : ProgressBar
    private lateinit var emptyView       : View
    private lateinit var emptyText       : TextView
    private lateinit var downloadButton  : MaterialButton
    private lateinit var toolbar         : Toolbar
    private lateinit var statsBar        : View
    private lateinit var statTotal       : TextView
    private lateinit var statPass        : TextView
    private lateinit var statFail        : TextView
    private lateinit var statAvgCgpi     : TextView

    private val writtenExamIds   = mutableListOf<String>()
    private val internalExamIds  = mutableListOf<String>()
    private val practicalExamIds = mutableListOf<String>()

    private val studentResults = mutableListOf<StudentResult>()
    private lateinit var adapter  : ResultGazetteAdapter
    private lateinit var firestore: FirebaseFirestore

    private var courseId   = ""
    private var courseName = ""
    private var deptName   = ""
    private var semester   = 0

    // Holds the URI of the last saved PDF so we can open/share it
    private var savedPdfUri: Uri? = null
    private var savedPdfFile: File? = null

    companion object {
        private const val REQUEST_WRITE_STORAGE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result_display)

        courseId   = intent.getStringExtra("COURSE_ID")   ?: ""
        courseName = intent.getStringExtra("COURSE_NAME") ?: ""
        deptName   = intent.getStringExtra("DEPT_NAME")   ?: ""
        semester   = intent.getIntExtra("SEMESTER", 0)

        intent.getStringArrayExtra("WRITTEN_EXAM_IDS")?.let   { writtenExamIds.addAll(it) }
        intent.getStringArrayExtra("INTERNAL_EXAM_IDS")?.let  { internalExamIds.addAll(it) }
        intent.getStringArrayExtra("PRACTICAL_EXAM_IDS")?.let { practicalExamIds.addAll(it) }

        firestore = FirebaseFirestore.getInstance()
        initViews()
        loadResults()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Result Gazette"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView    = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView       = findViewById(R.id.empty_view)
        emptyText       = findViewById(R.id.empty_text)
        downloadButton  = findViewById(R.id.download_button)
        statsBar        = findViewById(R.id.stats_bar)
        statTotal       = findViewById(R.id.stat_total)
        statPass        = findViewById(R.id.stat_pass)
        statFail        = findViewById(R.id.stat_fail)
        statAvgCgpi     = findViewById(R.id.stat_avg_cgpi)

        adapter = ResultGazetteAdapter(studentResults)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        downloadButton.setOnClickListener { checkPermissionAndDownload() }
    }

    // ─── LOAD ──────────────────────────────────────────────────────

    private fun loadResults() {
        showLoading(true)

        val allExamIds = (writtenExamIds + internalExamIds + practicalExamIds).distinct()
        if (allExamIds.isEmpty()) {
            showLoading(false); showEmpty("No exams selected."); return
        }

        val allMarks = mutableListOf<ExamMarks>()
        var loaded = 0

        allExamIds.forEach { examId ->
            firestore.collection("examMarks")
                .whereEqualTo("examId", examId)
                .whereEqualTo("courseId", courseId)
                .get()
                .addOnSuccessListener { docs ->
                    docs.forEach { allMarks.add(it.toObject(ExamMarks::class.java)) }
                    if (++loaded == allExamIds.size) computeResults(allMarks)
                }
                .addOnFailureListener {
                    if (++loaded == allExamIds.size) computeResults(allMarks)
                }
        }
    }

    // ─── COMPUTE ───────────────────────────────────────────────────

    private fun computeResults(allMarks: List<ExamMarks>) {
        studentResults.clear()

        if (allMarks.isEmpty()) {
            showLoading(false); showEmpty("No marks data found for this selection."); return
        }

        allMarks.groupBy { it.studentId }.forEach { (_, studentMarks) ->

            val subjectResults = studentMarks
                .groupBy { if (it.subjectId.isNotBlank()) it.subjectId else it.subjectCode }
                .map { (_, sm) ->

                    val writtenMarks  = sm.filter { writtenExamIds.contains(it.examId) }
                    val writtenObt    = writtenMarks.sumOf { it.writtenMarksObtained.toDouble() }.toFloat()
                    val writtenMax    = writtenMarks.sumOf { it.writtenMaxMarks.toLong() }.toInt()

                    val internalMarks = sm.filter { internalExamIds.contains(it.examId) }
                    val internalObt   = internalMarks.sumOf { it.internalMarksObtained.toDouble() }.toFloat()
                    val internalMax   = internalMarks.sumOf { it.internalMaxMarks.toLong() }.toInt()

                    val practicalMarks = sm.filter { practicalExamIds.contains(it.examId) }
                    val practicalObt   = practicalMarks.sumOf { it.internalMarksObtained.toDouble() }.toFloat()
                    val practicalMax   = practicalMarks.sumOf { it.internalMaxMarks.toLong() }.toInt()

                    val totalObt   = writtenObt + internalObt + practicalObt
                    val totalMax   = writtenMax + internalMax + practicalMax
                    val percentage = if (totalMax > 0) (totalObt / totalMax) * 100f else 0f
                    val grade      = calculateGrade(percentage)

                    SubjectResult(
                        subjectCode  = sm.first().subjectCode,
                        subjectName  = sm.first().subjectName,
                        writtenObt   = writtenObt,   writtenMax   = writtenMax,
                        internalObt  = internalObt,  internalMax  = internalMax,
                        practicalObt = practicalObt, practicalMax = practicalMax,
                        totalObt     = totalObt,     totalMax     = totalMax,
                        percentage   = percentage,   grade        = grade,
                        gradePoints  = gradePoints(grade)
                    )
                }

            val cgpi = if (subjectResults.isNotEmpty())
                subjectResults.sumOf { it.gradePoints.toDouble() }.toFloat() / subjectResults.size
            else 0f

            studentResults.add(StudentResult(
                rollNo      = studentMarks.first().studentRollNo,
                studentName = studentMarks.first().studentName,
                subjects    = subjectResults.sortedBy { it.subjectCode },
                cgpi        = cgpi
            ))
        }

        studentResults.sortBy { it.rollNo }
        adapter.notifyDataSetChanged()
        showLoading(false)

        if (studentResults.isEmpty()) {
            showEmpty("No results found.")
        } else {
            emptyView.visibility      = View.GONE
            recyclerView.visibility   = View.VISIBLE
            downloadButton.visibility = View.VISIBLE
            updateStatsBar()
        }
    }

    // ─── STATS BAR ─────────────────────────────────────────────────

    private fun updateStatsBar() {
        val pass    = studentResults.count { it.cgpi >= 4.0f }
        val fail    = studentResults.size - pass
        val avgCgpi = if (studentResults.isNotEmpty())
            studentResults.sumOf { it.cgpi.toDouble() } / studentResults.size else 0.0

        statsBar.visibility = View.VISIBLE
        statTotal.text      = studentResults.size.toString()
        statPass.text       = pass.toString()
        statFail.text       = fail.toString()
        statAvgCgpi.text    = "%.2f".format(avgCgpi)
    }

    // ─── PERMISSION + DOWNLOAD ─────────────────────────────────────

    private fun checkPermissionAndDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_STORAGE
                )
                return
            }
        }
        generateAndDownloadPdf()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            generateAndDownloadPdf()
        } else {
            Toast.makeText(this, "Storage permission required to save PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAndDownloadPdf() {
        if (studentResults.isEmpty()) {
            Toast.makeText(this, "No results to download", Toast.LENGTH_SHORT).show()
            return
        }

        downloadButton.isEnabled = false
        downloadButton.text      = "Generating PDF…"

        try {
            val safeCourse = courseName.replace(" ", "_").replace("/", "-")
            val fileName   = "Result_${safeCourse}_Sem${semester}.pdf"
            val generator  = ResultPdfGenerator(this)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ── API 29+ : MediaStore ──────────────────────────────────────
                val tempFile = File(cacheDir, fileName)
                generator.generate(
                    file = tempFile, students = studentResults,
                    deptName = deptName, courseName = courseName, semester = semester
                )

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE,    "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val mediaUri = contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                )
                if (mediaUri != null) {
                    contentResolver.openOutputStream(mediaUri)?.use { out ->
                        tempFile.inputStream().use { it.copyTo(out) }
                    }
                    savedPdfUri  = mediaUri
                    savedPdfFile = tempFile      // keep cache copy for FileProvider share
                    showPostDownloadDialog(fileName)
                } else {
                    Toast.makeText(this, "❌ Failed to create file", Toast.LENGTH_SHORT).show()
                }

            } else {
                // ── API 24–28 : public Downloads folder ───────────────────────
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                downloadsDir.mkdirs()
                val outFile = File(downloadsDir, fileName)
                generator.generate(
                    file = outFile, students = studentResults,
                    deptName = deptName, courseName = courseName, semester = semester
                )
                savedPdfFile = outFile
                savedPdfUri  = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    outFile
                )
                showPostDownloadDialog(fileName)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            downloadButton.isEnabled = true
            downloadButton.text      = "Download Result Gazette"
        }
    }

    // ─── POST-DOWNLOAD DIALOG ──────────────────────────────────────

    private fun showPostDownloadDialog(fileName: String) {
        AlertDialog.Builder(this)
            .setTitle("PDF Saved")
            .setMessage("\"$fileName\" has been saved to your Downloads folder.")
            .setPositiveButton("Open") { _, _ -> openPdf() }
            .setNeutralButton("Share") { _, _ -> sharePdf(fileName) }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    /** Open the PDF in the device's default PDF viewer. */
    private fun openPdf() {
        val uri = getShareableUri() ?: run {
            Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open PDF with…"))
        } catch (e: Exception) {
            Toast.makeText(this, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
        }
    }

    /** Share the PDF via any app (WhatsApp, email, Drive, etc.). */
    private fun sharePdf(fileName: String) {
        val uri = getShareableUri() ?: run {
            Toast.makeText(this, "Cannot share file", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Result Gazette – $courseName Sem $semester")
            putExtra(Intent.EXTRA_TEXT,    "Please find the result gazette attached.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Result Gazette via…"))
    }

    /**
     * Returns a URI that other apps can read.
     * On API 29+: use the MediaStore URI directly.
     * On API 24-28: use FileProvider for the file in Downloads.
     */
    private fun getShareableUri(): Uri? {
        // On API 29+ the MediaStore URI is already world-readable by other apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return savedPdfUri
        }
        // For older APIs use FileProvider (requires provider declared in AndroidManifest)
        val file = savedPdfFile ?: return null
        return try {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    // ─── HELPERS ───────────────────────────────────────────────────

    private fun gradePoints(g: String) = when (g) {
        "O" -> 10f; "A+" -> 9f; "A" -> 8f; "B+" -> 7f
        "B" -> 6f;  "C"  -> 5f; "D" -> 4f; else -> 0f
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmpty(msg: String) {
        emptyText.text          = msg
        emptyView.visibility    = View.VISIBLE
        recyclerView.visibility = View.GONE
        statsBar.visibility     = View.GONE
    }

    // ─── MODELS ────────────────────────────────────────────────────

    data class SubjectResult(
        val subjectCode  : String,
        val subjectName  : String,
        val writtenObt   : Float, val writtenMax   : Int,
        val internalObt  : Float, val internalMax  : Int,
        val practicalObt : Float, val practicalMax : Int,
        val totalObt     : Float, val totalMax     : Int,
        val percentage   : Float,
        val grade        : String,
        val gradePoints  : Float
    )

    data class StudentResult(
        val rollNo      : String,
        val studentName : String,
        val subjects    : List<SubjectResult>,
        val cgpi        : Float
    )
}