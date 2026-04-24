package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Student

class DeptStudentAdapter(
    private val onAssign: (Student) -> Unit,
    private val onRemove: (Student) -> Unit,
    private val onOpenProfile: (Student) -> Unit
) : RecyclerView.Adapter<DeptStudentAdapter.VH>() {

    private val students = mutableListOf<Student>()
    private val fullList = mutableListOf<Student>()

    fun submit(list: List<Student>) {
        fullList.clear()
        fullList.addAll(list)
        students.clear()
        students.addAll(list)
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        students.clear()
        if (query.isEmpty()) {
            students.addAll(fullList)
        } else {
            val q = query.lowercase()
            students.addAll(fullList.filter {
                it.fullName.lowercase().contains(q) ||
                        it.studentId.lowercase().contains(q) ||
                        it.rollNumber.lowercase().contains(q)
            })
        }
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.student_name)
        val course: TextView = v.findViewById(R.id.student_course)
        val btnAssign: MaterialButton = v.findViewById(R.id.btn_assign)
        val btnRemove: MaterialButton = v.findViewById(R.id.btn_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dept_student, parent, false))
    }

    override fun onBindViewHolder(h: VH, p: Int) {
        val s = students[p]

        h.name.text = s.fullName
        h.course.text = "${s.courseName} • Sem ${s.currentSemester} • ${s.batch}"

        h.itemView.setOnClickListener { onOpenProfile(s) }

        if (s.departmentId.isEmpty()) {
            h.btnAssign.visibility = View.VISIBLE
            h.btnRemove.visibility = View.GONE
            h.btnAssign.setOnClickListener { onAssign(s) }
        } else {
            h.btnAssign.visibility = View.GONE
            h.btnRemove.visibility = View.VISIBLE
            h.btnRemove.setOnClickListener { onRemove(s) }
        }
    }

    override fun getItemCount() = students.size
}
