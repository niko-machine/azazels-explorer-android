package com.azazel.explorer.ui.organize

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.azazel.explorer.util.formatFileSize

class OrganizeFileAdapter(
    private val items: MutableList<OrganizeManager.OrganizeEntry>,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : RecyclerView.Adapter<OrganizeFileAdapter.ViewHolder>() {

    private val selectedPositions = mutableSetOf<Int>()

    init {
        items.indices.forEach { selectedPositions.add(it) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.cb_organize)
        val fileName: TextView = view.findViewById(R.id.tv_file_name)
        val destination: TextView = view.findViewById(R.id.tv_destination)
        val categoryBadge: TextView = view.findViewById(R.id.tv_category_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_organize_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        val file = entry.file

        holder.fileName.text = file.name
        holder.destination.text = entry.destinationLabel
        holder.categoryBadge.text = entry.category.replaceFirstChar { it.uppercase() }
        holder.checkbox.isChecked = selectedPositions.contains(position)

        holder.checkbox.setOnClickListener {
            if (holder.checkbox.isChecked) {
                selectedPositions.add(position)
            } else {
                selectedPositions.remove(position)
            }
            onSelectionChanged(selectedPositions.size)
        }

        holder.itemView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
            if (holder.checkbox.isChecked) {
                selectedPositions.add(position)
            } else {
                selectedPositions.remove(position)
            }
            onSelectionChanged(selectedPositions.size)
        }
    }

    override fun getItemCount() = items.size

    fun getSelectedEntries(): List<OrganizeManager.OrganizeEntry> {
        return selectedPositions.sorted().map { items[it] }
    }

    fun getSelectedCount() = selectedPositions.size

    fun selectAll() {
        selectedPositions.clear()
        items.indices.forEach { selectedPositions.add(it) }
        notifyDataSetChanged()
        onSelectionChanged(selectedPositions.size)
    }

    fun deselectAll() {
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun updateItems(newItems: List<OrganizeManager.OrganizeEntry>) {
        items.clear()
        items.addAll(newItems)
        selectedPositions.clear()
        items.indices.forEach { selectedPositions.add(it) }
        notifyDataSetChanged()
        onSelectionChanged(selectedPositions.size)
    }
}
