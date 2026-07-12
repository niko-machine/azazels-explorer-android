package com.azazel.explorer.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.azazel.explorer.R
import com.azazel.explorer.activities.FilePropertiesActivity
import java.io.File

class FileDetailFragment : Fragment(R.layout.fragment_file_detail) {

    private val args: FileDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tv_file_name).text = args.fileName
        view.findViewById<TextView>(R.id.tv_file_path).text = args.filePath

        view.findViewById<Button>(R.id.btn_properties).setOnClickListener {
            val intent = Intent(requireContext(), FilePropertiesActivity::class.java).apply {
                putExtra("filePath", args.filePath)
            }
            startActivity(intent)
        }

        view.findViewById<Button>(R.id.btn_share).setOnClickListener {
            shareFile(args.filePath)
        }
    }

    private fun shareFile(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.msg_file_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = requireContext().contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.chooser_share_file)))
    }
}
