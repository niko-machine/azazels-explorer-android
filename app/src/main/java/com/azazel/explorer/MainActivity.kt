package com.azazel.explorer

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.azazel.explorer.data.ThemePreferences
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val themePrefs = ThemePreferences(this)
        themePrefs.applyTheme(themePrefs.isDarkMode())

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        val detailDestinations = setOf(
            R.id.fileDetailFragment,
            R.id.filePropertiesFragment,
            R.id.imageViewerFragment,
            R.id.zipContentsFragment,
            R.id.settingsFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (detailDestinations.contains(destination.id)) {
                ObjectAnimator.ofFloat(bottomNav, "translationY", bottomNav.height.toFloat()).apply {
                    duration = 200
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            bottomNav.visibility = View.GONE
                        }
                    })
                    start()
                }
            } else {
                bottomNav.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(bottomNav, "translationY", 0f).apply {
                    duration = 200
                    start()
                }
            }
        }
    }
}
