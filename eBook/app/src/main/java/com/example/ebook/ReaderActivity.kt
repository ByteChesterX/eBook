package com.example.ebook

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

class ReaderActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var zoomValueText: TextView
    private lateinit var adapter: PdfPageAdapter

    private var pdfUri: Uri? = null
    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    
    // Varsayılan zoom %100
    private var currentZoomLevel: Int = 100 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Tema Ayarı (SharedPreferences'tan okunur)
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.activity_reader)

        recyclerView = findViewById(R.id.recyclerViewPages)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        zoomValueText = findViewById(R.id.zoomValueText)

        pdfUri = intent.data

        if (pdfUri == null) {
            Toast.makeText(this, "PDF dosyası bulunamadı.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        setupZoomListener()
        loadPdf()
    }

    private fun setupRecyclerView() {
        adapter = PdfPageAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupZoomListener() {
        // SharedPreferences'tan son zoom seviyesini çek (Varsayılan 100)
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        currentZoomLevel = prefs.getInt("zoom_level", 100)
        
        zoomSeekBar.progress = currentZoomLevel
        updateZoomText(currentZoomLevel)

        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentZoomLevel = progress
                    updateZoomText(progress)
                    // Adapter'ı güncelle
                    adapter.updateScale(progress)
                    // Tercihi kaydet
                    prefs.edit().putInt("zoom_level", progress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateZoomText(level: Int) {
        zoomValueText.text = "%$level"
    }

    private fun loadPdf() {
        try {
            // URI'yı geçici bir dosyaya kopyala (PdfRenderer FileDescriptor ister)
            val tempFile = File(cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(pdfUri!!)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fileDescriptor!!)

            val pageCount = renderer!!.pageCount
            val pages = mutableListOf<PdfRenderer.Page>()
            
            // Sayfaları hazırla (Render etme, sadece referans tutma)
            for (i in 0 until pageCount) {
                pages.add(renderer!!.openPage(i))
            }

            adapter = PdfPageAdapter(pages)
            recyclerView.adapter = adapter
            
            // Zoom'u uygula
            adapter.updateScale(currentZoomLevel)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF yüklenirken hata: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Bellek sızıntısını önlemek için kaynakları kapat
        for (page in adapter.pages) {
            page.close()
        }
        renderer?.close()
        fileDescriptor?.close()
    }
}

// Adapter Sınıfı
class PdfPageAdapter(
    val pages: List<PdfRenderer.Page>
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private var scaleFactor: Float = 1.0f

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.pageImage)
        val progressBar: View = itemView.findViewById(R.id.loadingProgress)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PageViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        
        // Render işlemi
        renderPage(page, holder.imageView, scaleFactor)
    }

    override fun getItemCount(): Int = pages.size

    // Zoom güncelleme metodu
    fun updateScale(newScalePercent: Int) {
        scaleFactor = newScalePercent / 100.0f
        notifyDataSetChanged() // Tüm sayfaları yeni ölçekle yeniden çiz
    }

    private fun renderPage(page: PdfRenderer.Page, imageView: ImageView, scale: Float) {
        // Sayfa boyutlarını al
        val width = page.width
        val height = page.height

        // Ölçeklendirilmiş boyutları hesapla
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()

        // Eski bitmap'i temizle (Bellek yönetimi)
        val oldDrawable = imageView.drawable
        if (oldDrawable is android.graphics.drawable.BitmapDrawable) {
            oldDrawable.bitmap?.recycle()
        }

        // Yeni Bitmap oluştur
        val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        
        // Render işlemini Matrix ile yap
        // PdfRenderer.render(bitmap, bounds, matrix)
        // bounds: Bitmap'in hangi kısmının doldurulacağı (genelde tamamı)
        // matrix: Dönüşüm matrisi (scale, translate, rotate)
        
        val matrix = Matrix()
        matrix.setScale(scale, scale)

        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        imageView.setImageBitmap(bitmap)
        imageView.visibility = View.VISIBLE
    }
}
