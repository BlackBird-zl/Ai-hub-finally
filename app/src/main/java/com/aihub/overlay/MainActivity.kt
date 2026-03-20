package com.aihub.overlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
        } else {
            startOverlayService()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(
                    this,
                    "Permissão necessária para o overlay funcionar.",
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
    }

    companion object {
        const val REQUEST_OVERLAY = 1001
    }
}
