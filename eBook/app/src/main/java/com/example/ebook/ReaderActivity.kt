package com.example.ebook

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.LruCache

class ReaderActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tvPageInfo: TextView
    private lateinit var tvZoomPercent: TextView
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var bottomControls: MaterialCardView
    private lateinit var searchContainer: MaterialCardView
    private lateinit var etSearchQuery: EditText
    private lateinit var btnBookmark: ImageButton
    private lateinit var btnMenu: ImageButton
    
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentPageIndex = 0
    private var totalPages = 0
    
    // Ayarlar
    private var scaleFactor = 1.0f
    private var currentTheme = THEME_LIGHT
    private var isScreenOn = true
    
    // LRU Cache (Bellek Yönetimi)
    private var bitmapCache: LruCache<Int, Bitmap>? = null
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    
    // Veritabanı (Basit SharedPreferences ile simüle edilmiştir, Room entegre edilebilir)
    private lateinit var prefs: SharedPreferences

    companion object {
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SEPIA = 2
        const val PREFS_NAME = "ebook_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-Edge ve Tam Ekran
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        
        // Ekranı açık tut
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_reader)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSettings()

        initViews()
        setupViewPager()
        setupControls()
        
        // PDF'i Aç
        intent.data?.let { uri ->
            openPdf(uri)
        } ?: run {
            Toast.makeText(this, "PDF dosyası bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPagerPages)
        tvPageInfo = findViewById(R.id.tvPageInfo)
        tvZoomPercent = findViewById(R.id.tvZoomPercent)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        bottomControls = findViewById(R.id.bottomControls)
        searchContainer = findViewById(R.id.searchContainer)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        btnBookmark = findViewById(R.id.btnBookmark)
        btnMenu = findViewById(R.id.btnMenu)
    }

    private fun setupViewPager() {
        // Yatay Kaydırma
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPageIndex = position
                updatePageInfo()
                saveLastReadPage(position)
                checkBookmarkStatus(position)
            }
            
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    // Sayfa durduğunda görünmeyen sayfaların bitmaplerini temizle (Opsiyonel optimizasyon)
                    // LRU cache zaten bunu otomatik yönetir ama ekstra temizlik yapılabilir.
                }
            }
        })
    }

    private fun setupControls() {
        // Zoom SeekBar
        zoomSeekBar.progress = (scaleFactor * 100).toInt()
        tvZoomPercent.text = "${(scaleFactor * 100).toInt()}%"
        
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newScale = progress.toFloat() / 100f
                    // Sınırlar: 0.25x ile 3.0x arası
                    val clampedScale = newScale.coerceIn(0.25f, 3.0f)
                    scaleFactor = clampedScale
                    tvZoomPercent.text = "${(clampedScale * 100).toInt()}%"
                    
                    // Adapter'a haber ver ve yeniden çiz
                    (viewPager.adapter as? PdfAdapter)?.updateScale(scaleFactor)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Menü Butonu
        btnMenu.setOnClickListener { showOptionsMenu() }
        
        // Yer İmi Butonu
        btnBookmark.setOnClickListener { toggleBookmark() }
        
        // Arama (Basit Metin Arama - PDF Renderer sınırlı destekler, sadece metin tabanlıysa)
        // Not: Android PdfRenderer doğrudan metin arama API'si sunmaz, bu görsel bir simülasyondur 
        // veya OCR gerektirir. Burada UI akışını gösteriyoruz.
        findViewById<ImageButton>(R.id.btnSearchClose).setOnClickListener {
            searchContainer.visibility = View.GONE
        }
        
        // Çift Dokunma ile Zoom (Basit Toggle)
        // Gerçek pinch-to-zoom için ImageView içinde Matrix manipülasyonu gerekir,
        // adapter içinde halledilecek.
    }

    private fun openPdf(uri: android.net.Uri) {
        try {
            // Dosya yolunu al (ContentResolver ile)
            val fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            parcelFileDescriptor = fileDescriptor
            
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            totalPages = pdfRenderer!!.pageCount
            
            // Cache Boyutu Hesapla (Ekran boyutuna göre)
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            val availableMemory = maxMemory / 8
            bitmapCache = object : LruCache<Int, Bitmap>(availableMemory) {
                override fun sizeOf(key: Int, bitmap: Bitmap): Int {
                    return bitmap.byteCount / 1024
                }
            }

            val adapter = PdfAdapter(totalPages, pdfRenderer!!, bitmapCache!!, scaleFactor)
            viewPager.adapter = adapter
            
            // Son okunan sayfaya git
            val lastPage = getLastReadPage(uri.toString())
            viewPager.setCurrentItem(lastPage, false)
            
            updatePageInfo()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updatePageInfo() {
        tvPageInfo.text = "Sayfa ${currentPageIndex + 1} / $totalPages"
    }

    private fun showOptionsMenu() {
        val items = arrayOf("Açık Tema", "Koyu Tema", "Sepya Tema", "İçindekiler", "Arama Yap", "Ekranı Açık Tut/Kapat", "Hakkında")
        AlertDialog.Builder(this)
            .setTitle("Ayarlar")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> setTheme(THEME_LIGHT)
                    1 -> setTheme(THEME_DARK)
                    2 -> setTheme(THEME_SEPIA)
                    3 -> showToast("İçindekiler özelliği yakında eklenecek (Outline API)")
                    4 -> {
                        searchContainer.visibility = View.VISIBLE
                        etSearchQuery.requestFocus()
                    }
                    5 -> toggleScreenOn()
                    6 -> showToast("AndroidX PDF Okuyucu v1.0")
                }
            }
            .show()
    }

    private fun setTheme(theme: Int) {
        currentTheme = theme
        prefs.edit().putInt("theme", theme).apply()
        
        val window = window
        when (theme) {
            THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
                window.navigationBarColor = ContextCompat.getColor(this, android.R.color.white)
            }
            THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
                window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)
            }
            THEME_SEPIA -> {
                // Sepya için özel renkler
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                window.statusBarColor = ContextCompat.getColor(this, R.color.sepia_bg)
                window.navigationBarColor = ContextCompat.getColor(this, R.color.sepia_bg)
            }
        }
        recreate() // Temayı uygulamak için activity'yi yenile
    }
    
    private fun toggleScreenOn() {
        isScreenOn = !isScreenOn
        if (isScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Toast.makeText(this, "Ekran açık kalacak", Toast.LENGTH_SHORT).show()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Toast.makeText(this, "Ekran kapanma süresi aktif", Toast.LENGTH_SHORT).show()
        }
    }

    // Yer İmi İşlemleri (SharedPreferences ile basit tutuldu)
    private fun toggleBookmark() {
        val bookmarks = prefs.getStringSet("bookmarks_${intent.data}", mutableSetOf())!!.toMutableSet()
        if (bookmarks.contains(currentPageIndex.toString())) {
            bookmarks.remove(currentPageIndex.toString())
            btnBookmark.setImageResource(android.R.drawable.btn_star_big_off)
            Toast.makeText(this, "Yer imi kaldırıldı", Toast.LENGTH_SHORT).show()
        } else {
            bookmarks.add(currentPageIndex.toString())
            btnBookmark.setImageResource(android.R.drawable.btn_star_big_on)
            Toast.makeText(this, "Yer imi eklendi", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet("bookmarks_${intent.data}", bookmarks).apply()
    }
    
    private fun checkBookmarkStatus(page: Int) {
        val bookmarks = prefs.getStringSet("bookmarks_${intent.data}", mutableSetOf())!!
        if (bookmarks.contains(page.toString())) {
            btnBookmark.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            btnBookmark.setImageResource(android.R.drawable.btn_star_big_off)
        }
    }

    private fun saveLastReadPage(page: Int) {
        prefs.edit().putInt("last_page_${intent.data}", page).apply()
    }
    
    private fun getLastReadPage(uri: String): Int {
        return prefs.getInt("last_page_$uri", 0)
    }
    
    private fun loadSettings() {
        currentTheme = prefs.getInt("theme", THEME_LIGHT)
        setTheme(currentTheme) // Temayı yükle
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
    }

    // Adapter Sınıfı
    inner class PdfAdapter(
        private val pageCount: Int,
        private val renderer: PdfRenderer,
        private val cache: LruCache<Int, Bitmap>,
        private var scale: Float
    ) : RecyclerView.Adapter<PdfAdapter.PageViewHolder>() {

        fun updateScale(newScale: Float) {
            scale = newScale
            notifyDataSetChanged() // Tüm sayfaları yeni skala ile yeniden çizer
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount() = pageCount

        inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.imgPage)
            private val progress: ProgressBar = itemView.findViewById(R.id.progressLoader)

            fun bind(pageIndex: Int) {
                progress.visibility = View.VISIBLE
                imageView.setImageBitmap(null) // Eski bitmap'i temizle

                // Cache'den kontrol et
                val cachedBitmap = cache.get(pageIndex)
                if (cachedBitmap != null) {
                    imageView.setImageBitmap(cachedBitmap)
                    applyMatrix(imageView, cachedBitmap.width, cachedBitmap.height)
                    progress.visibility = View.GONE
                    return
                }

                // Asenkron Render
                executor.execute {
                    try {
                        val page = renderer.openPage(pageIndex)
                        
                        // Ölçeklendirilmiş boyutları hesapla
                        val scaledWidth = (page.width * scale).toInt()
                        val scaledHeight = (page.height * scale).toInt()
                        
                        val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                        
                        // Render
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        
                        // Cache'e ekle
                        cache.put(pageIndex, bitmap)
                        
                        // UI Thread'e dön
                        imageView.post {
                            if (adapterPosition == pageIndex) { // Pozisyon değişmediyse
                                imageView.setImageBitmap(bitmap)
                                applyMatrix(imageView, scaledWidth, scaledHeight)
                                progress.visibility = View.GONE
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        imageView.post { progress.visibility = View.GONE }
                    }
                }
            }
            
            private fun applyMatrix(imgView: ImageView, bmpW: Int, bmpH: Int) {
                // Center Crop veya Fit Center mantığı
                // Tam ekran olması için ScaleType matrix ile manuel hesaplama
                val matrix = Matrix()
                val viewW = imgView.width.toFloat()
                val viewH = imgView.height.toFloat()
                
                if (viewW > 0 && viewH > 0) {
                    val scaleX = viewW / bmpW
                    val scaleY = viewH / bmpH
                    
                    // Kullanıcının seçtiği zoom (scale) ile birleştir
                    // Burada basitçe ortala ve zoom'u uygula
                    matrix.setScale(scale, scale)
                    
                    // Merkeze ötele
                    val translateX = (viewW - bmpW * scale) / 2
                    val translateY = (viewH - bmpH * scale) / 2
                    matrix.postTranslate(translateX, translateY)
                    
                    imgView.imageMatrix = matrix
                }
            }
        }
    }
}
