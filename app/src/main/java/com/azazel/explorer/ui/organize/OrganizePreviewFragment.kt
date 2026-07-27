package com.azazel.explorer.ui.organize

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrganizePreviewFragment : Fragment(R.layout.fragment_organize_preview) {

    private var adapter: OrganizeFileAdapter? = null
    private var currentEntries = listOf<OrganizeManager.OrganizeEntry>()
    private var organizeJob: Job? = null
    private var isCancelled = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val chipGroup = view.findViewById<ChipGroup>(R.id.cg_category_filter)
        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            loadPreview(view)
        }

        loadPreview(view)

        view.findViewById<Button>(R.id.btn_select_all).setOnClickListener {
            val allSelected = adapter?.getSelectedCount() == adapter?.itemCount
            if (allSelected) adapter?.deselectAll() else adapter?.selectAll()
        }

        view.findViewById<Button>(R.id.btn_organize_now).setOnClickListener {
            val selected = adapter?.getSelectedEntries() ?: emptyList()
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), R.string.organize_no_selection, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmAndOrganize(view, selected)
        }
    }

    private fun loadPreview(view: View) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.cg_category_filter)
        val selectedFilters = mutableSetOf<OrganizeManager.FileTypeFilter>()

        if (chipGroup.checkedChipIds.contains(R.id.chip_images))
            selectedFilters.add(OrganizeManager.FileTypeFilter.IMAGES)
        if (chipGroup.checkedChipIds.contains(R.id.chip_documents))
            selectedFilters.add(OrganizeManager.FileTypeFilter.DOCUMENTS)
        if (chipGroup.checkedChipIds.contains(R.id.chip_videos))
            selectedFilters.add(OrganizeManager.FileTypeFilter.VIDEOS)
        if (chipGroup.checkedChipIds.contains(R.id.chip_audio_organize))
            selectedFilters.add(OrganizeManager.FileTypeFilter.AUDIO)

        if (selectedFilters.isEmpty()) {
            showEmptyState(view, true)
            return
        }

        view.findViewById<LinearLayout>(R.id.layout_loading).visibility = View.VISIBLE
        view.findViewById<RecyclerView>(R.id.rv_organize_files).visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.layout_empty).visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.layout_summary).visibility = View.GONE
        view.findViewById<Button>(R.id.btn_organize_now).visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                OrganizeManager.scanForOrganizable(requireContext(), selectedFilters)
            }
            currentEntries = entries

            view.findViewById<LinearLayout>(R.id.layout_loading).visibility = View.GONE

            val rv = view.findViewById<RecyclerView>(R.id.rv_organize_files)
            val summary = view.findViewById<TextView>(R.id.tv_summary)

            if (entries.isEmpty()) {
                showEmptyState(view, true)
                summary.text = getString(R.string.organize_summary_count, 0)
            } else {
                showEmptyState(view, false)
                summary.text = getString(R.string.organize_summary_count, entries.size)

                if (adapter == null) {
                    adapter = OrganizeFileAdapter(entries.toMutableList()) { count ->
                        val total = entries.size
                        summary.text = getString(R.string.organize_summary_selected, count, total)
                        view.findViewById<Button>(R.id.btn_organize_now).isEnabled = count > 0
                    }
                    rv.layoutManager = LinearLayoutManager(requireContext())
                    rv.adapter = adapter
                } else {
                    adapter?.updateItems(entries)
                }
            }
        }
    }

    private fun showEmptyState(view: View, empty: Boolean) {
        view.findViewById<LinearLayout>(R.id.layout_loading).visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.layout_empty).visibility = if (empty) View.VISIBLE else View.GONE
        view.findViewById<RecyclerView>(R.id.rv_organize_files).visibility = if (empty) View.GONE else View.VISIBLE
        view.findViewById<LinearLayout>(R.id.layout_summary).visibility = if (empty) View.GONE else View.VISIBLE
        view.findViewById<Button>(R.id.btn_organize_now).visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun confirmAndOrganize(view: View, selected: List<OrganizeManager.OrganizeEntry>) {
        val message = getString(R.string.organize_confirm_msg, selected.size)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.organize_confirm_title)
            .setMessage(message)
            .setPositiveButton(R.string.organize_move_files) { _, _ ->
                executeOrganize(view, selected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun executeOrganize(view: View, selected: List<OrganizeManager.OrganizeEntry>) {
        val layoutProgress = view.findViewById<LinearLayout>(R.id.layout_progress)
        val tvProgress = view.findViewById<TextView>(R.id.tv_progress)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_organize)
        val btnOrganize = view.findViewById<Button>(R.id.btn_organize_now)

        layoutProgress.visibility = View.VISIBLE
        btnOrganize.isEnabled = false
        btnOrganize.text = getString(R.string.status_starting)
        isCancelled = false

        progressBar.max = selected.size
        progressBar.progress = 0

        organizeJob = viewLifecycleOwner.lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                OrganizeManager.organizeFiles(
                    context = requireContext(),
                    entries = selected,
                    onProgress = { current, total ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            tvProgress.text = getString(R.string.organize_progress, current, total)
                            progressBar.progress = current
                        }
                    },
                    isCancelled = { isCancelled }
                )
            }

            layoutProgress.visibility = View.GONE
            btnOrganize.isEnabled = true
            btnOrganize.text = getString(R.string.organize_move_files)

            showResults(view, results, selected.size)
        }
    }

    private fun showResults(view: View, results: Map<String, Int>, totalAttempted: Int) {
        val totalMoved = results.values.sum()

        if (totalMoved == 0) {
            Toast.makeText(requireContext(), R.string.organize_nothing_moved, Toast.LENGTH_SHORT).show()
            loadPreview(view)
            return
        }

        val message = buildString {
            append(getString(R.string.organize_result_summary, totalMoved, totalAttempted))
            append("\n\n")
            results.forEach { (dest, count) ->
                append("$count → $dest\n")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.organize_complete_title)
            .setMessage(message.trimEnd())
            .setPositiveButton(R.string.organize_done) { _, _ ->
                findNavController().navigateUp()
            }
            .setNeutralButton(R.string.organize_undo) { _, _ ->
                Toast.makeText(requireContext(), R.string.organize_undo_not_available, Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()

        loadPreview(view)
    }

    override fun onDestroyView() {
        organizeJob?.cancel()
        super.onDestroyView()
    }
}
