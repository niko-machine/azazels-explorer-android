package com.azazel.explorer.activities

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.azazel.explorer.R

class FilePropertiesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_properties)

        val path = intent.getStringExtra("filePath") ?: getString(R.string.label_unknown)
        findViewById<TextView>(R.id.tv_properties).text = getString(R.string.label_path, path)
    }
}
