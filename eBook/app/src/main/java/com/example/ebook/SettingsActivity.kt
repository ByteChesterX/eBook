package com.example.ebook

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var rbLightTheme: android.widget.RadioButton
    private lateinit var rbDarkTheme: android.widget.RadioButton
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var tvZoomValue: TextView
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        sharedPrefs = getSharedPreferences("ebook_settings", MODE_PRIVATE)

        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        rbLightTheme = findViewById(R.id.rbLightTheme)
        rbDarkTheme = findViewById(R.id.rbDarkTheme)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        tvZoomValue = findViewById(R.id.tvZoomValue)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        val zoomLevel = sharedPrefs.getInt("zoom_level", 100)

        if (isDarkMode) {
            rbDarkTheme.isChecked = true
        } else {
            rbLightTheme.isChecked = true
        }

        zoomSeekBar.progress = zoomLevel
        tvZoomValue.text = "$zoomLevel%"
    }

    private fun setupListeners() {
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isDarkMode = checkedId == R.id.rbDarkTheme
            sharedPrefs.edit().putBoolean("dark_mode", isDarkMode).apply()
            
            // Apply theme immediately
            AppCompatDelegate.setDefaultNightMode(
                if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvZoomValue.text = "$progress%"
                sharedPrefs.edit().putInt("zoom_level", progress).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
