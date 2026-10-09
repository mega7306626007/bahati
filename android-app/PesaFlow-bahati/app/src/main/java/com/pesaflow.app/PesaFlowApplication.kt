package com.pesaflow.app

import android.app.Application
import android.content.Context
import com.pesaflow.app.data.time.KenyaTime

class PesaFlowApplication : Application() {
    override fun attachBaseContext(base: Context) {
        KenyaTime.installAsDefault()
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        // pdfbox-android stages its font/glyph resources from app assets on
        // first use — without this, statement text extraction fails on-device
        // with "glyphlist.txt not found" even though the files ship in the AAR.
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        } catch (e: Exception) {
            android.util.Log.w("PesaFlowApp", "PDF resource staging failed; statement PDFs may not parse", e)
        }
    }
}
