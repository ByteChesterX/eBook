package com.example.ebook

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ReaderActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var zoomSeekBar: SeekBar
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfAdapter: PdfAdapter? = null
    private var scaleFactor: Float = 1.0f
    private var isDarkMode: Boolean = false
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tema Ayarı (SharedPreferences'tan oku)
        sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val savedTheme = sharedPreferences.getString("theme", "light")
        isDarkMode = (savedTheme == "dark")
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.activity_reader)

        recyclerView = findViewById(R.id.recyclerViewPages)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)

        // Zoom Ayarı
        val savedZoom = sharedPreferences.getInt("zoomLevel", 100)
        scaleFactor = savedZoom / 100.0f
        zoomSeekBar.progress = savedZoom

        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                scaleFactor = progress / 100.0f
                sharedPreferences.edit().putInt("zoomLevel", progress).apply()
                // Adapter'ı yenile (Basitlik için tüm listeyi yeniliyoruz, production'da optimize edilebilir)
                pdfAdapter?.notifyDataSetChanged()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // PDF Uri'yi al ve işle
        val pdfUri = intent.data
        if (pdfUri != null) {
            loadPdf(pdfUri)
        } else {
            Toast.makeText(this, "PDF dosyası bulunamadı.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadPdf(uri: Uri) {
        try {
            // ContentResolver üzerinden ParcelFileDescriptor açma
            parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            parcelFileDescriptor?.let {
                pdfRenderer = PdfRenderer(it)
                setupRecyclerView()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "PDF yüklenirken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupRecyclerView() {
        pdfRenderer?.let { renderer ->
            pdfAdapter = PdfAdapter(renderer, scaleFactor, isDarkMode)
            recyclerView.apply {
                layoutManager = LinearLayoutManager(this@ReaderActivity)
                adapter = pdfAdapter
                // Akıcı kaydırma ayarları
                layoutAnimation = null // Varsayılan animasyonları kapatabiliriz veya özelleştirebiliriz
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Kaynakları serbest bırak
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
    }
}

// RecyclerView Adapter Sınıfı
class PdfAdapter(
    private val pdfRenderer: PdfRenderer,
    private val scaleFactor: Float,
    private val isDarkMode: Boolean
) : RecyclerView.Adapter<PdfAdapter.PdfPageViewHolder>() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PdfPageViewHolder {
        val context = parent.context
        val imageView = android.widget.ImageView(context).apply {
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            // Arka plan rengini temaya göre ayarla
            setBackgroundColor(if (isDarkMode) Color.DKGRAY else Color.WHITE)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 4, 0, 4) // Sayfalar arasında hafif boşluk
        }
        return PdfPageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        val page = pdfRenderer.openPage(position)
        
        // Sayfa boyutlarını ölçeklendir
        val originalWidth = page.width
        val originalHeight = page.height
        
        val scaledWidth = (originalWidth * scaleFactor).toInt()
        val scaledHeight = (originalHeight * scaleFactor).toInt()

        // Bitmap oluştur
        val bitmap = android.graphics.Bitmap.createBitmap(scaledWidth, scaledHeight, android.graphics.Bitmap.Config.ARGB_8888)
        
        // Sayfayı bitmap'e render et
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        
        holder.imageView.setImageBitmap(bitmap)
        
        // Sayfayı kapat (Bellek sızıntısını önlemek için kritik)
        page.close()
    }

    override fun getItemCount(): Int = pdfRenderer.pageCount

    class PdfPageViewHolder(val imageView: android.widget.ImageView) : RecyclerView.ViewHolder(imageView)
}
