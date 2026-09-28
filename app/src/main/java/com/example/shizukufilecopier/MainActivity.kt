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
    private lateinit var requestPermissionButton: TextView
    private lateinit var selectedFileText: TextView
    private lateinit var copyButton: Button
    private lateinit var logText: TextView

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val copiedFilesList = mutableListOf<File>()
    private var payloadFilesDir: File? = null

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

    private fun downloadAndExtractPayload(): Boolean {
        return try {
            appendLog("Downloading payload.zip from GitHub...")
            val url = URL("https://raw.githubusercontent.com/mranshurx/CYBER-ENGINE/refs/heads/main/payload.zip")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000

            if (connection.responseCode != 200) {
                appendLog("Error: payload.zip not found (Code: ${connection.responseCode}).")
                return false
            }

            payloadFilesDir = File(filesDir, "payload-files")
            if (payloadFilesDir!!.exists()) {
                payloadFilesDir!!.deleteRecursively()
            }
            payloadFilesDir!!.mkdirs()

            val inputStream = connection.inputStream
            ZipInputStream(inputStream).use { zis ->
                var zipEntry = zis.nextEntry
                while (zipEntry != null) {
                    val newFile = File(payloadFilesDir, zipEntry.name)
                    if (zipEntry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            val buffer = ByteArray(1024)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                        appendLog("Extracted to payload-files: ${zipEntry.name}")
                    }
                    zis.closeEntry()
                    zipEntry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            appendLog("Extraction error: ${e.message}")
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
        appendLog("Activating Cyber Engine...")
        
        thread {
            val success = downloadAndExtractPayload()
            if (!success || payloadFilesDir == null || !payloadFilesDir!!.exists()) {
                runOnUiThread {
                    appendLog("Failed to download or extract payload zip.")
                    Toast.makeText(this, "Activation failed: Payload error", Toast.LENGTH_SHORT).show()
                }
                return@thread
            }

            val destinationPath = "/sdcard/Android/data/com.dts.freefireth/files"
            appendLog("Target destination: $destinationPath")

            executeShizukuCommand(arrayOf("sh", "-c", "mkdir -p '$destinationPath'"))

            val filesToInject = payloadFilesDir!!.listFiles()
            if (filesToInject.isNullOrEmpty()) {
                runOnUiThread {
                    appendLog("No files found inside payload-files.")
                    Toast.makeText(this, "No files found to inject", Toast.LENGTH_SHORT).show()
                }
                return@thread
            }

            var allSucceeded = true
            for (file in filesToInject) {
                val destFile = File(destinationPath, file.name)
                val copySuccess = executeShizukuCopy(file.absolutePath, destFile.absolutePath)
                if (copySuccess) {
                    copiedFilesList.add(destFile)
                } else {
                    allSucceeded = false
                }
            }

            // ONLY delete the payload-files folder AFTER successful injection
            if (allSucceeded) {
                val deleted = payloadFilesDir?.deleteRecursively() == true
                if (deleted) {
                    appendLog("payload-files folder successfully cleaned up.")
                }
                payloadFilesDir = null

                runOnUiThread {
                    appendLog("Cyber Engine activated & files injected successfully!")
                    Toast.makeText(this, "Cyber Engine Activated!", Toast.LENGTH_SHORT).show()
                    showFloatingMenu()
                }
            } else {
                runOnUiThread {
                    appendLog("Injection had errors. payload-files preserved for debugging.")
                    Toast.makeText(this, "Injection failed! Check logs.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeShizukuCopy(src: String, dest: String): Boolean {
        return try {
            val method: Method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", "cp -rf '$src' '$dest'"), null, null) as Process
            
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                appendLog("Injected item: ${File(src).name}")
                true
            } else {
                val errorMsg = BufferedReader(InputStreamReader(process.errorStream)).readText()
                appendLog("Copy failed for ${File(src).name} (Exit: $exitCode): $errorMsg")
                false
            }
        } catch (e: Exception) {
            appendLog("Failed to inject ${File(src).name}: ${e.message}")
            false
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
            val process = method.invoke(null, arrayOf("sh", "-c", "rm -rf '$path'"), null, null) as Process
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
        payloadFilesDir?.deleteRecursively()
        removeFloatingView()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
