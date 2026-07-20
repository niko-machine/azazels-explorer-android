package com.azazel.explorer.ui.preview

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.azazel.explorer.R
import com.github.chrisbanes.photoview.PhotoView
import java.io.File

class ImageViewerFragment : Fragment(R.layout.fragment_image_viewer) {

    private val args: ImageViewerFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val photoView = view.findViewById<PhotoView>(R.id.photo_view)
        val btnClose = view.findViewById<ImageButton>(R.id.btn_close)

        val file = File(args.filePath)
        if (file.exists()) {
            Glide.with(this)
                .load(file)
                .fitCenter()
                .into(photoView)
        }

        btnClose.setOnClickListener {
            findNavController().navigateUp()
        }
    }
}
