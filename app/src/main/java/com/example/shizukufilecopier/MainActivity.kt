package com.example.shizukufilecopier

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var pickFileButton: Button
    private lateinit var selectedFileText: TextView
    private lateinit var destPathEditText: TextInputEditText
    private lateinit var copyButton: Button
    private lateinit var logText: TextView

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val copiedFilesList = mutableListOf<File>()

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                appendLog("Shizuku permission granted.")
                statusText.text = "Shizuku: Running & Authorized"
                requestPermissionButton.visibility = View.GONE
            } else {
                appendLog("Shizuku permission denied.")
                statusText.text = "Shizuku: Permission Denied"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind layout views from your XML
        statusText = findViewById(R.id.statusText)
        requestPermissionButton = findViewById(R.id.requestPermissionButton)
        pickFileButton = findViewById(R.id.pickFileButton)
        selectedFileText = findViewById(R.id.selectedFileText)
        destPathEditText = findViewById(R.id.destPathEditText)
        copyButton = findViewById(R.id.copyButton)
        logText = findViewById(R.id.logText)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        // 1. Authorize via GitHub Key
        checkKeyAuthorization()

        // 2. Setup UI Handlers
        setupUI()
    }

    private fun checkKeyAuthorization() {
        appendLog("Checking key authorization from GitHub...")
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
                        appendLog("Key authorization successful.")
                        checkShizukuStatus()
                    } else {
                        appendLog("Authorization failed: Key is empty.")
                        Toast.makeText(this, "Invalid Key!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendLog("Key check error: ${e.message}")
                    Toast.makeText(this, "Failed to connect to authorization server.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun checkShizukuStatus() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    statusText.text = "Shizuku: Running & Authorized"
                    requestPermissionButton.visibility = View.GONE
                    appendLog("Shizuku is ready.")
                } else {
                    statusText.text = "Shizuku: Permission needed"
                    requestPermissionButton.visibility = View.VISIBLE
                    appendLog("Shizuku permission required.")
                }
            } else {
                statusText.text = "Shizuku: Not running / service dead"
                appendLog("Shizuku service is not running.")
            }
        } catch (e: Exception) {
            statusText.text = "Shizuku: Error checking status"
            appendLog("Shizuku check error: ${e.message}")
        }
    }

    private fun setupUI() {
        requestPermissionButton.setOnClickListener {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) {
                Toast.makeText(this, "Shizuku version too old", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    Shizuku.requestPermission(1001)
                } catch (e: Exception) {
                    appendLog("Error requesting Shizuku permission: ${e.message}")
                }
            }
        }

        pickFileButton.setOnClickListener {
            val baseDir = getExternalFilesDir(null)
            val anshuTopDir = File(baseDir, "anshu-on-top")
            if (!anshuTopDir.exists()) anshuTopDir.mkdirs()
            selectedFileText.text = "Files source folder ready: anshu-on-top"
            appendLog("Target storage folder initialized at: ${anshuTopDir.absolutePath}")
        }

        copyButton.setOnClickListener {
            executeCopyProcess()
        }
    }

    private fun executeCopyProcess() {
        val baseDir = getExternalFilesDir(null) ?: return
        val anshuTopDir = File(baseDir, "anshu-on-top")
        val pasteHereDir = File(baseDir, "paste-here")

        if (!anshuTopDir.exists()) anshuTopDir.mkdirs()
        if (!pasteHereDir.exists()) pasteHereDir.mkdirs()

        // Save input destination path into paste-here/path.txt or read directly from text input
        val customPath = destPathEditText.text.toString().trim()
        val destinationPath = if (customPath.isNotEmpty()) {
            File(pasteHereDir, "path.txt").writeText(customPath)
            customPath
        } else {
            val pathFile = File(pasteHereDir, "path.txt")
            if (pathFile.exists()) pathFile.readText().trim() else "/sdcard/Download/"
        }

        appendLog("Copying files from 'anshu-on-top' to: $destinationPath")

        val files = anshuTopDir.listFiles()
        if (files.isNullOrEmpty()) {
            appendLog("No files found inside 'anshu-on-top' folder to copy.")
            Toast.makeText(this, "No files found in anshu-on-top", Toast.LENGTH_SHORT).show()
            return
        }

        thread {
            for (file in files) {
                if (file.isFile) {
                    val destFile = File(destinationPath, file.name)
                    executeShizukuCopy(file.absolutePath, destFile.absolutePath)
                    copiedFilesList.add(destFile)
                }
            }
            runOnUiThread {
                appendLog("All files copied successfully!")
                Toast.makeText(this, "Files Activated & Copied!", Toast.LENGTH_SHORT).show()
                showFloatingMenu()
            }
        }
    }

    private fun executeShizukuCopy(src: String, dest: String) {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) return
        try {
            val process = Shizuku.newProcess(arrayOf("cp", src, dest), null, null)
            process.waitFor()
            appendLog("Copied: ${File(src).name}")
        } catch (e: Exception) {
            appendLog("Failed to copy ${File(src).name}: ${e.message}")
        }
    }

    private fun showFloatingMenu() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (floatingView != null) return // Avoid duplicate views

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
            cleanupCopiedFiles()
            appendLog("Offline mode triggered via floating menu. Files deleted.")
            Toast.makeText(this, "Offline mode: Files deleted", Toast.LENGTH_SHORT).show()
            removeFloatingView()
        }

        try {
            windowManager.addView(floatingView, params)
            appendLog("Floating menu displayed.")
        } catch (e: Exception) {
            appendLog("Error showing floating menu: ${e.message}")
        }
    }

    private fun cleanupCopiedFiles() {
        thread {
            for (file in copiedFilesList) {
                if (file.exists()) {
                    executeShizukuRm(file.absolutePath)
                }
            }
            copiedFilesList.clear()
        }
    }

    private fun executeShizukuRm(path: String) {
        try {
            val process = Shizuku.newProcess(arrayOf("rm", path), null, null)
            process.waitFor()
            appendLog("Deleted: $path")
        } catch (e: Exception) {
            appendLog("Failed to delete $path: ${e.message}")
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

    private fun appendLog(message: String) {
        runOnUiThread {
            logText.append("$message\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Automatically clean up pasted files and floating window when application is exited
        cleanupCopiedFiles()
        removeFloatingView()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
