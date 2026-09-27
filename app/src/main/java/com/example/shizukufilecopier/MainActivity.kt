package com.example.shizukufilecopier

import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
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

        // Check key authorization from GitHub raw link on startup
        checkKeyAndAuthorize()
    }

    private fun checkKeyAndAuthorize() {
        thread {
            try {
                val url = URL("https://raw.githubusercontent.com/mranshurx/CYBER-ENGINE-V1/refs/heads/main/key.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val keyContent = reader.readLine()?.trim() ?: ""
                reader.close()

                runOnUiThread {
                    if (keyContent.isNotEmpty()) {
                        Toast.makeText(this, "Key Authorized Successfully!", Toast.LENGTH_SHORT).show()
                        setupAppLogic()
                    } else {
                        Toast.makeText(this, "Authorization Failed: Invalid Key", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error checking key: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun setupAppLogic() {
        val btnActivate = findViewById<Button>(R.id.btnActivate)
        btnActivate.setOnClickListener {
            prepareAndCopyFiles()
            showFloatingMenu()
        }
    }

    private fun prepareAndCopyFiles() {
        val baseDir = getExternalFilesDir(null) ?: return
        val anshuTopDir = File(baseDir, "anshu-on-top")
        val pasteHereDir = File(baseDir, "paste-here")

        if (!anshuTopDir.exists()) anshuTopDir.mkdirs()
        if (!pasteHereDir.exists()) pasteHereDir.mkdirs()

        // Read destination path from paste-here folder (defaults to /sdcard/Download/ if text file is empty/missing)
        val pathConfigFile = File(pasteHereDir, "path.txt")
        val destinationPath = if (pathConfigFile.exists()) {
            pathConfigFile.readText().trim()
        } else {
            "/sdcard/Download/"
        }

        anshuTopDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val destFile = File(destinationPath, file.name)
                executeShizukuCopy(file.absolutePath, destFile.absolutePath)
                copiedFilesList.add(destFile)
            }
        }
        Toast.makeText(this, "Files Pasted Successfully via Shizuku", Toast.LENGTH_SHORT).show()
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
        params.y = 200

        val btnOffline = floatingView?.findViewById<Button>(R.id.btnOffline)
        btnOffline?.setOnClickListener {
            cleanupFiles()
            Toast.makeText(this, "Offline Mode: Pasted files deleted", Toast.LENGTH_SHORT).show()
            removeFloatingView()
        }

        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupFiles() {
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
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Automatically delete pasted files when app is exited completely
        cleanupFiles()
        removeFloatingView()
    }
}
