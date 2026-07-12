package com.example.ebook

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
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
    
    // Veritabanı
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
        @Suppress("DEPRECATION")
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
        
        // HATA DÜZELTMESİ: İlk açılışta temayı recreate() yapmadan yükle
        loadSettingsWithoutRecreate()

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
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPageIndex = position
                updatePageInfo()
                saveLastReadPage(position)
                checkBookmarkStatus(position)
            }
        })
    }

    private fun setupControls() {
        zoomSeekBar.progress = (scaleFactor * 100).toInt()
        tvZoomPercent.text = "${(scaleFactor * 100).toInt()}%"
        
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newScale = progress.toFloat() / 100f
                    val clampedScale = newScale.coerceIn(0.25f, 3.0f)
                    scaleFactor = clampedScale
                    tvZoomPercent.text = "${(clampedScale * 100).toInt()}%"
                    
                    (viewPager.adapter as? PdfAdapter)?.updateScale(scaleFactor)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnMenu.setOnClickListener { showOptionsMenu() }
        btnBookmark.setOnClickListener { toggleBookmark() }
        
        findViewById<ImageButton>(R.id.btnSearchClose).setOnClickListener {
            searchContainer.visibility = View.GONE
        }
    }

    private fun openPdf(uri: android.net.Uri) {
        try {
            val fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            if (fileDescriptor == null) {
                showToast("Dosya yüklenemedi.")
                finish()
                return
            }
            parcelFileDescriptor = fileDescriptor
            
            pdfRenderer = PdfRenderer(fileDescriptor)
            totalPages = pdfRenderer!!.pageCount
            
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            val availableMemory = maxMemory / 8
            bitmapCache = object : LruCache<Int, Bitmap>(availableMemory) {
                override fun sizeOf(key: Int, bitmap: Bitmap): Int {
                    return bitmap.byteCount / 1024
                }
            }

            val adapter = PdfAdapter(totalPages, pdfRenderer!!, bitmapCache!!, scaleFactor)
            viewPager.adapter = adapter
            
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
                    0 -> changeThemeAndRecreate(THEME_LIGHT)
                    1 -> changeThemeAndRecreate(THEME_DARK)
                    2 -> changeThemeAndRecreate(THEME_SEPIA)
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

    // HATA DÜZELTMESİ: Bu metot sadece kullanıcı menüden tıkladığında çalışır
    private fun changeThemeAndRecreate(theme: Int) {
        if (currentTheme != theme) {
            applyThemeSystem(theme)
            recreate() 
        }
    }

    // HATA DÜZELTMESİ: İlk açılışta tetiklenen ve recreate yapmayan fonksiyon
    private fun loadSettingsWithoutRecreate() {
        currentTheme = prefs.getInt("theme", THEME_LIGHT)
        applyThemeSystem(currentTheme)
    }

    // Tema mantığının tekilleştirilmiş hali
    private fun applyThemeSystem(theme: Int) {
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
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                // R.color.sepia_bg projenizde tanımlı olmalıdır.
                try {
                    window.statusBarColor = ContextCompat.getColor(this, R.color.sepia_bg)
                    window.navigationBarColor = ContextCompat.getColor(this, R.color.sepia_bg)
                } catch (e: Exception) {
                    // Eğer color tanımı yoksa çökmesin diye fallback
                    window.statusBarColor = ContextCompat.getColor(this, android.R.color.holo_orange_light)
                }
            }
        }
    }
    
    private fun toggleScreenOn() {
        isScreenOn = !isScreenOn
        if (isScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            showToast("Ekran açık kalacak")
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            showToast("Ekran kapanma süresi aktif")
        }
    }

    private fun toggleBookmark() {
        val bookmarks = prefs.getStringSet("bookmarks_${intent.data}", mutableSetOf())!!.toMutableSet()
        if (bookmarks.contains(currentPageIndex.toString())) {
            bookmarks.remove(currentPageIndex.toString())
            btnBookmark.setImageResource(android.R.drawable.btn_star_big_off)
            showToast("Yer imi kaldırıldı")
        } else {
            bookmarks.add(currentPageIndex.toString())
            btnBookmark.setImageResource(android.R.drawable.btn_star_big_on)
            showToast("Yer imi eklendi")
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

    // HATA DÜZELTMESİ: Eksik showToast fonksiyonu eklendi
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
            notifyDataSetChanged() 
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
                imageView.setImageBitmap(null) 

                val cachedBitmap = cache.get(pageIndex)
                if (cachedBitmap != null) {
                    imageView.setImageBitmap(cachedBitmap)
                    applyMatrix(imageView, cachedBitmap.width, cachedBitmap.height)
                    progress.visibility = View.GONE
                    return
                }

                executor.execute {
                    try {
                        val page = renderer.openPage(pageIndex)
                        
                        val scaledWidth = (page.width * scale).toInt()
                        val scaledHeight = (page.height * scale).toInt()
                        
                        val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                        
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        
                        cache.put(pageIndex, bitmap)
                        
                        imageView.post {
                            // HATA DÜZELTMESİ: adapterPosition yerine bindingAdapterPosition kullanıldı
                            if (bindingAdapterPosition == pageIndex) { 
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
                val matrix = Matrix()
                val viewW = imgView.width.toFloat()
                val viewH = imgView.height.toFloat()
                
                if (viewW > 0 && viewH > 0) {
                    matrix.setScale(scale, scale)
                    
                    val translateX = (viewW - bmpW * scale) / 2
                    val translateY = (viewH - bmpH * scale) / 2
                    matrix.postTranslate(translateX, translateY)
                    
                    imgView.imageMatrix = matrix
                }
            }
        }
    }
}
