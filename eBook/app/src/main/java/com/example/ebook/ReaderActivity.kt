package com.example.ebook

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener

class ReaderActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var sharedPrefs: SharedPreferences
    private var isDarkMode = false
    private var zoomLevel = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply fullscreen mode for distraction-free reading
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Hide action bar
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_reader)

        sharedPrefs = getSharedPreferences("ebook_settings", MODE_PRIVATE)
        loadSettings()

        pdfView = findViewById(R.id.pdfView)
        
        applyTheme()
        
        val pdfUri = intent.data
        if (pdfUri != null) {
            loadPdf(pdfUri)
        } else {
            finish()
        }
    }

    private fun loadSettings() {
        isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        zoomLevel = sharedPrefs.getInt("zoom_level", 100)
    }

    private fun applyTheme() {
        val container = findViewById<android.widget.FrameLayout>(R.id.readerContainer)
        if (isDarkMode) {
            container.setBackgroundColor(Color.parseColor("#121212"))
            window.statusBarColor = Color.parseColor("#121212")
            window.navigationBarColor = Color.parseColor("#121212")
        } else {
            container.setBackgroundColor(Color.WHITE)
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
        }
    }

    private fun loadPdf(uri: android.net.Uri) {
        // Convert zoom level to float (100% = 1.0f)
        val zoom = zoomLevel / 100f
        
        pdfView.fromUri(uri)
            .defaultPage(0)
            .spacing(10) // Space between pages in dp
            .pageFitPolicy(com.github.barteksc.pdfviewer.model.FitPolicy.WIDTH) // Fit to width for vertical scrolling
            .autoSpacing(true) // Enable auto spacing for smooth scrolling
            .pageFling(true) // Enable fling gesture
            .pageSnap(true) // Snap to pages
            .swipeHorizontal(false) // Vertical scrolling (pages one below another)
            .zoom(zoom)
            .onLoad { nbPages ->
                // PDF loaded successfully
            }
            .onError { throwable ->
                // Handle error
                throwable.printStackTrace()
                finish()
            }
            .load()
    }

    override fun onResume() {
        super.onResume()
        // Reload settings if changed
        loadSettings()
        applyTheme()
    }
}
