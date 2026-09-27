package com.example.shizukufilecopier

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var authLayout: LinearLayout
    private lateinit var mainDashboardLayout: LinearLayout
    private lateinit var keyInputEditText: TextInputEditText
    private lateinit var verifyKeyButton: Button

    private lateinit var statusText: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var selectedFileText: TextView
    private lateinit var copyButton: Button
    private lateinit var logText: TextView

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val copiedFilesList = mutableListOf<File>()
    private val tempStagingFiles = mutableListOf<File>()

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

        authLayout = findViewById(R.id.authLayout)
        mainDashboardLayout = findViewById(R.id.mainDashboardLayout)
        keyInputEditText = findViewById(R.id.keyInputEditText)
        verifyKeyButton = findViewById(R.id.verifyKeyButton)

        statusText = findViewById(R.id.statusText)
        requestPermissionButton = findViewById(R.id.requestPermissionButton)
        selectedFileText = findViewById(R.id.selectedFileText)
        copyButton = findViewById(R.id.copyButton)
        logText = findViewById(R.id.logText)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        verifyKeyButton.setOnClickListener {
            val userKey = keyInputEditText.text.toString().trim()
            if (userKey.isNotEmpty()) {
                validateKeyWithServer(userKey)
            } else {
                Toast.makeText(this, "Please enter a key", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateKeyWithServer(inputKey: String) {
        verifyKeyButton.isEnabled = false
        Toast.makeText(this, "Verifying key...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                // Change 'main' to 'master' here if your repo uses master branch
                val url = URL("https://raw.githubusercontent.com/mranshurx/CYBER-ENGINE/refs/heads/main/key.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val remoteKey = reader.readLine()?.trim() ?: ""
                    reader.close()

                    runOnUiThread {
                        verifyKeyButton.isEnabled = true
                        if (inputKey == remoteKey) {
                            Toast.makeText(this, "Authorization Successful!", Toast.LENGTH_SHORT).show()
                            authLayout.visibility = View.GONE
                            mainDashboardLayout.visibility = View.VISIBLE
                            checkShizukuStatus()
                            setupUI()
                        } else {
                            Toast.makeText(this, "Invalid Key! Access Denied.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        verifyKeyButton.isEnabled = true
                        Toast.makeText(this, "Server error code: $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    verifyKeyButton.isEnabled = true
                    Toast.makeText(this, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadAndExtractToCache(): Boolean {
        return try {
            appendLog("Fetching payload from GitHub into memory...")
            val url = URL("https://raw.githubusercontent.com/mranshurx/CYBER-ENGINE/refs/heads/main/payload.zip")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000

            if (connection.responseCode != 200) {
                appendLog("Error: payload.zip not found on remote repository (Code: ${connection.responseCode}).")
                return false
            }

            val cacheDir = cacheDir
            val inputStream = connection.inputStream
            ZipInputStream(inputStream).use { zis ->
                var zipEntry = zis.nextEntry
                while (zipEntry != null) {
                    if (!zipEntry.isDirectory) {
                        val tempFile = File(cacheDir, zipEntry.name)
                        tempStagingFiles.add(tempFile)
                        FileOutputStream(tempFile).use { fos ->
                            val buffer = ByteArray(1024)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                        appendLog("Streamed & staged: ${zipEntry.name}")
                    }
                    zis.closeEntry()
                    zipEntry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            appendLog("Streaming error: ${e.message}")
            false
        }
    }

    private fun checkShizukuStatus() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    statusText.text = "Shizuku: Running & Authorized"
                    requestPermissionButton.visibility = View.GONE
                } else {
                    statusText.text = "Shizuku: Permission needed"
                    requestPermissionButton.visibility = View.VISIBLE
                }
            } else {
                statusText.text = "Shizuku: Not running / service dead"
            }
        } catch (e: Exception) {
            statusText.text = "Shizuku: Error checking status"
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

        copyButton.setOnClickListener {
            executeEngineActivation()
        }
    }

    private fun executeEngineActivation() {
        appendLog("Activating Cyber Engine (Zero-Storage Mode)...")
        
        thread {
            tempStagingFiles.clear()
            val success = downloadAndExtractToCache()
            if (!success || tempStagingFiles.isEmpty()) {
                runOnUiThread {
                    appendLog("Failed to fetch or extract payload.")
                    Toast.makeText(this, "Activation failed: No files retrieved", Toast.LENGTH_SHORT).show()
                }
                return@thread
            }

            val destinationPath = "/sdcard/Android/data/com.dts.freefireth/files"
            appendLog("Target destination: $destinationPath")

            executeShizukuCommand(arrayOf("mkdir", "-p", destinationPath))

            for (file in tempStagingFiles) {
                if (file.exists()) {
                    val destFile = File(destinationPath, file.name)
                    executeShizukuCopy(file.absolutePath, destFile.absolutePath)
                    copiedFilesList.add(destFile)
                }
            }

            for (file in tempStagingFiles) {
                if (file.exists()) file.delete()
            }
            tempStagingFiles.clear()

            runOnUiThread {
                appendLog("Cyber Engine activated successfully! Zero storage footprint.")
                Toast.makeText(this, "Cyber Engine Activated!", Toast.LENGTH_SHORT).show()
                showFloatingMenu()
            }
        }
    }

    private fun executeShizukuCopy(src: String, dest: String) {
        try {
            val method: Method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("cp", "-rf", src, dest), null, null) as Process
            process.waitFor()
            appendLog("Injected: ${File(src).name}")
        } catch (e: Exception) {
            appendLog("Failed to inject ${File(src).name}: ${e.message}")
        }
    }

    private fun executeShizukuCommand(cmd: Array<String>) {
        try {
            val method: Method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, cmd, null, null) as Process
            process.waitFor()
        } catch (e: Exception) {
            appendLog("Command error: ${e.message}")
        }
    }

    private fun showFloatingMenu() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (floatingView != null) return

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
            appendLog("Offline mode triggered. Injected files cleaned up.")
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
                if (file.exists() || true) {
                    executeShizukuRm(file.absolutePath)
                }
            }
            copiedFilesList.clear()
        }
    }

    private fun executeShizukuRm(path: String) {
        try {
            val method: Method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("rm", "-rf", path), null, null) as Process
            process.waitFor()
            appendLog("Cleaned up: $path")
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
        cleanupCopiedFiles()
        for (file in tempStagingFiles) {
            if (file.exists()) file.delete()
        }
        removeFloatingView()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
