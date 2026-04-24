package com.projectbyyatin.synapsemis.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.Subject

class SubjectListAdapter(
    private val originalList: MutableList<Subject>,
    private val onItemClick: (Subject) -> Unit
) : RecyclerView.Adapter<SubjectListAdapter.SubjectViewHolder>(), Filterable {

    private var filteredList: MutableList<Subject> = ArrayList(originalList)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject_hod, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(filteredList[position])
    }

    override fun getItemCount(): Int = filteredList.size

    fun updateList(newList: List<Subject>) {
        originalList.clear()
        originalList.addAll(newList)
        filteredList.clear()
        filteredList.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterPattern = constraint?.toString()?.lowercase()?.trim() ?: ""
                val results = FilterResults()

                val filteredItems = if (filterPattern.isEmpty()) {
                    originalList.toMutableList()
                } else {
                    originalList.filter { subject ->
                        subject.name.lowercase().contains(filterPattern) ||
                                subject.code.lowercase().contains(filterPattern) ||
                                subject.courseName.lowercase().contains(filterPattern)
                    }.toMutableList()
                }

                results.values = filteredItems
                results.count = filteredItems.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList.clear()
                @Suppress("UNCHECKED_CAST")
                filteredList.addAll(results?.values as? Collection<Subject> ?: emptyList())
                notifyDataSetChanged()
            }
        }
    }

    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.subject_name)
        private val codeText: TextView = itemView.findViewById(R.id.subject_code)
        private val detailsText: TextView = itemView.findViewById(R.id.subject_details)
        private val facultyText: TextView = itemView.findViewById(R.id.subject_faculty)
        private val card: CardView = itemView.findViewById(R.id.card)

        fun bind(subject: Subject) {
            nameText.text = subject.name
            codeText.text = subject.code
            detailsText.text = "${subject.type} • Sem ${subject.semesterNumber} • ${subject.credits}cr"

            facultyText.text = if (subject.assignedFacultyName.isNotEmpty()) {
                "👤 ${subject.assignedFacultyName}"
            } else {
                "👤 Not Assigned"
            }
            facultyText.setTextColor(
                if (subject.assignedFacultyName.isNotEmpty())
                    ContextCompat.getColor(itemView.context, R.color.green_500)
                else
                    ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
            )

            card.setOnClickListener { onItemClick(subject) }
        }
    }
}
