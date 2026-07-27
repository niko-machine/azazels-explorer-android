package com.azazel.explorer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.azazel.explorer.R

class AboutActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AboutActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_title)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<android.widget.TextView>(R.id.tv_version).text =
            getString(R.string.about_version, "1.0", 1)
    }
}
