// ReaderActivity.kt
// NOTE: This version fixes the "Current page not closed" issue by
// opening each PdfRenderer.Page only while rendering and closing it immediately.
// Replace your existing ReaderActivity/PdfPageAdapter with this implementation
// and adapt imports if needed.

package com.example.ebook

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

class ReaderActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var seek: SeekBar
    private lateinit var zoomText: TextView

    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private var zoom = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        recyclerView = findViewById(R.id.recyclerViewPages)
        seek = findViewById(R.id.zoomSeekBar)
        zoomText = findViewById(R.id.zoomValueText)

        val uri: Uri = intent.data ?: run { finish(); return }

        val tmp = File(cacheDir, "reader.pdf")
        contentResolver.openInputStream(uri)?.use { i ->
            FileOutputStream(tmp).use { o -> i.copyTo(o) }
        }

        fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fd!!)

        val adapter = PdfPageAdapter(renderer!!)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        seek.progress = zoom
        zoomText.text = "%$zoom"
        adapter.scale = zoom / 100f

        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s: SeekBar?, p:Int, f:Boolean){
                zoom=p
                zoomText.text="%$p"
                adapter.scale=p/100f
                adapter.notifyDataSetChanged()
            }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
    }

    override fun onDestroy() {
        renderer?.close()
        fd?.close()
        super.onDestroy()
    }
}

class PdfPageAdapter(private val renderer: PdfRenderer)
    : RecyclerView.Adapter<PdfPageAdapter.Holder>() {

    var scale = 1f

    class Holder(v: View): RecyclerView.ViewHolder(v){
        val image: ImageView = v.findViewById(R.id.pageImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType:Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page,parent,false)
        return Holder(v)
    }

    override fun getItemCount() = renderer.pageCount

    override fun onBindViewHolder(holder: Holder, position:Int){
        val page = renderer.openPage(position)
        try{
            val w=(page.width*scale).toInt().coerceAtLeast(1)
            val h=(page.height*scale).toInt().coerceAtLeast(1)
            val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            val m=Matrix()
            m.setScale(scale,scale)
            page.render(bmp,null,m,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            holder.image.setImageBitmap(bmp)
        } finally {
            page.close()
        }
    }
}
