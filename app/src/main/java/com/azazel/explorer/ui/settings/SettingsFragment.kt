package com.azazel.explorer.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.azazel.explorer.AboutActivity
import com.azazel.explorer.R
import com.azazel.explorer.data.ThemePreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupThemeSelection(view)
        setupAboutSection(view)
    }

    private fun setupThemeSelection(view: View) {
        val themePrefs = ThemePreferences(requireContext())
        val switchDarkMode = view.findViewById<MaterialSwitch>(R.id.switch_dark_mode)

        switchDarkMode.isChecked = themePrefs.isDarkMode()

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            themePrefs.setDarkMode(isChecked)
        }
    }

    private fun setupAboutSection(view: View) {
        val tvVersion = view.findViewById<TextView>(R.id.tv_version)
        tvVersion.text = getString(R.string.about_version, "1.0", 1)

        view.findViewById<MaterialButton>(R.id.btn_view_about).setOnClickListener {
            val intent = Intent(requireContext(), AboutActivity::class.java)
            startActivity(intent)
        }
    }
}
