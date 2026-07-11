package com.example.ebook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectPdf: Button
    private lateinit var tvFileName: TextView
    private var selectedPdfUri: Uri? = null

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedPdfUri = it
            tvFileName.text = "Seçilen dosya: ${getFileName(it)}"
            showConfirmDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        btnSelectPdf = findViewById(R.id.btnSelectPdf)
        tvFileName = findViewById(R.id.tvFileName)

        btnSelectPdf.setOnClickListener {
            pdfLauncher.launch("application/pdf")
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_open)
            .setMessage(R.string.confirm_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                openPdfReader()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                selectedPdfUri = null
                tvFileName.text = ""
            }
            .show()
    }

    private fun openPdfReader() {
        selectedPdfUri?.let { uri ->
            val intent = Intent(this, ReaderActivity::class.java).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } ?: run {
            Toast.makeText(this, "PDF seçilmedi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    name = cursor.getString(displayNameIndex)
                }
            }
        }
        return name.ifEmpty { uri.lastPathSegment ?: "Bilinmeyen dosya" }
    }
}
