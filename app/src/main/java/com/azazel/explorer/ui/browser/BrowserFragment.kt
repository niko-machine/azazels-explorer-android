package com.azazel.explorer.ui.browser

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azazel.explorer.R
import com.azazel.explorer.model.FakeFile

class BrowserFragment : Fragment(R.layout.fragment_browser) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fakeFiles = listOf(
            FakeFile("photo.jpg", "/sdcard/Download/photo.jpg"),
            FakeFile("resume.pdf", "/sdcard/Download/resume.pdf"),
            FakeFile("song.mp3", "/sdcard/Download/song.mp3")
        )

        val rv = view.findViewById<RecyclerView>(R.id.rv_files)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = FileAdapter(fakeFiles) { file ->
            val action = BrowserFragmentDirections
                .actionBrowserToDetail(filePath = file.path, fileName = file.name)
            findNavController().navigate(action)
        }
    }
}
