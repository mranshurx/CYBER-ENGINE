package com.example.shizukufilecopier

import android.app.AlertDialog
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val copiedFilesList = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Key Authorization Check
        checkKeyAndProceed()
    }

    private fun checkKeyAndProceed() {
        thread {
            try {
                val url = URL("https://raw.githubusercontent.com/mranshurx/CYBER-ENGINE-V1/refs/heads/main/key.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val validKey = reader.readLine()?.trim() ?: ""
                reader.close()

                runOnUiThread {
                    // For demonstration, we assume validKey is checked or entered. 
                    // You can add an EditText for user input if the key needs to match a specific value, 
                    // or check if the fetched content is active.
                    setupMainInterface()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Key authorization failed: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun setupMainInterface() {
        val btnActivate = findViewById<Button>(R.id.btnActivate)
        btnActivate.setOnClickListener {
            prepareDirectoriesAndCopy()
            showFloatingMenu()
        }
    }

    private fun prepareDirectoriesAndCopy() {
        val baseDir =getExternalFilesDir(null) ?: return
        val anshuTopDir = File(baseDir, "anshu-on-top")
        val pasteHereDir = File(baseDir, "paste-here")

        if (!anshuTopDir.exists()) anshuTopDir.mkdirs()
        if (!pasteHereDir.exists()) pasteHereDir.mkdirs()

        // Read paths from paste-here and copy files from anshu-on-top using Shizuku
        val destinationPathFile = File(pasteHereDir, "paths.txt")
        val destinationPath = if (destinationPathFile.exists()) {
            destinationPathFile.readText().trim()
        } else {
            "/sdcard/Download/" // Default fallback path
        }

        anshuTopDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val destFile = File(destinationPath, file.name)
                executeShizukuCopy(file.absolutePath, destFile.absolutePath)
                copiedFilesList.add(destFile)
            }
        }
        Toast.makeText(this, "Files Activated & Copied!", Toast.LENGTH_SHORT).show()
    }

    private fun executeShizukuCopy(src: String, dest: String) {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) return
        try {
            val process = Shizuku.newProcess(arrayOf("cp", src, dest), null, null)
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFloatingMenu() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_menu, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        val btnOffline = floatingView?.findViewById<Button>(R.id.btnOffline)
        btnOffline?.setOnClickListener {
            cleanupCopiedFiles()
            Toast.makeText(this, "Offline mode: Files deleted", Toast.LENGTH_SHORT).show()
            removeFloatingView()
        }

        windowManager.addView(floatingView, params)
    }

    private fun cleanupCopiedFiles() {
        for (file in copiedFilesList) {
            if (file.exists()) {
                executeShizukuRm(file.absolutePath)
            }
        }
        copiedFilesList.clear()
    }

    private fun executeShizukuRm(path: String) {
        try {
            val process = Shizuku.newProcess(arrayOf("rm", path), null, null)
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingView() {
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupCopiedFiles()
        removeFloatingView()
    }
}
