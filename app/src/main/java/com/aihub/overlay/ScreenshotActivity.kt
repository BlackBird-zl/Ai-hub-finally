package com.aihub.overlay

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class ScreenshotActivity : Activity() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projManager.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            val projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            // Android 14+ (API 34) exige callback registado antes de usar getMediaProjection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val projection = projManager.getMediaProjection(resultCode, data)
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { cleanup() }
                }, handler)
                mediaProjection = projection
            } else {
                mediaProjection = projManager.getMediaProjection(resultCode, data)
            }

            captureScreen()
        } else {
            Toast.makeText(this, "Captura cancelada.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun captureScreen() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AIHubCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        handler.postDelayed({
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()
                    val uri = saveBitmap(bitmap)
                    cleanup()
                    if (uri != null) {
                        OverlayService.instance?.onScreenshotReady(uri)
                    } else {
                        Toast.makeText(this, "Erro ao salvar print.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Nao foi possivel capturar a tela.", Toast.LENGTH_SHORT).show()
                    cleanup()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erro na captura.", Toast.LENGTH_SHORT).show()
                cleanup()
            }
            finish()
        }, 1000)
    }

    private fun saveBitmap(bitmap: Bitmap): Uri? {
        return try {
            val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(this, "$packageName.provider", file)
        } catch (e: Exception) { null }
    }

    private fun cleanup() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
    }

    companion object { const val REQ_CAPTURE = 2001 }
}
