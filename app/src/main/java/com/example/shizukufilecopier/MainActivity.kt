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
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var authLayout: LinearLayout
    private lateinit var mainDashboardLayout: LinearLayout
    private lateinit var keyInputEditText: TextInputEditText
    private lateinit var verifyKeyButton: Button

    private lateinit var statusText: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var pickFileButton: Button
    private lateinit var selectedFileText: TextView
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

        authLayout = findViewById(R.id.authLayout)
        mainDashboardLayout = findViewById(R.id.mainDashboardLayout)
        keyInputEditText = findViewById(R.id.keyInputEditText)
        verifyKeyButton = findViewById(R.id.verifyKeyButton)

        statusText = findViewById(R.id.statusText)
        requestPermissionButton = findViewById(R.id.requestPermissionButton)
        pickFileButton = findViewById(R.id.pickFileButton)
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
                val url = URL("https://raw.githubusercontent.com/mranshurx/CYBER-ENGINE-V1/refs/heads/main/key.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val remoteKey = reader.readLine()?.trim() ?: ""
                reader.close()

                runOnUiThread {
                    verifyKeyButton.isEnabled = true
                    if (inputKey == remoteKey) {
                        Toast.makeText(this, "Authorization Successful!", Toast.LENGTH_SHORT).show()
                        authLayout.visibility = View.GONE
                        mainDashboardLayout.visibility = View.VISIBLE
                        extractAssetsToAnshuFolder()
                        checkShizukuStatus()
                        setupUI()
                    } else {
                        Toast.makeText(this, "Invalid Key! Access Denied.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    verifyKeyButton.isEnabled = true
                    Toast.makeText(this, "Failed to connect to key server.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun extractAssetsToAnshuFolder() {
        val baseDir = getExternalFilesDir(null) ?: return
        val anshuTopDir = File(baseDir, "anshu-on-top")
        if (!anshuTopDir.exists()) anshuTopDir.mkdirs()

        try {
            val assetManager = assets
            val assetsList = assetManager.list("anshu-on-top")
            if (assetsList != null && assetsList.isNotEmpty()) {
                for (filename in assetsList) {
                    val outFile = File(anshuTopDir, filename)
                    assetManager.open("anshu-on-top/$filename").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                selectedFileText.text = "Bundled files loaded into anshu-on-top"
                appendLog("Successfully extracted hardcoded asset files into anshu-on-top.")
            } else {
                selectedFileText.text = "Source folder ready: anshu-on-top"
                appendLog("No hardcoded assets found, folder ready.")
            }
        } catch (e: Exception) {
            appendLog("Asset extraction error: ${e.message}")
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

        pickFileButton.setOnClickListener {
            extractAssetsToAnshuFolder()
        }

        copyButton.setOnClickListener {
            executeCopyProcess()
        }
    }

    private fun executeCopyProcess() {
        val baseDir = getExternalFilesDir(null) ?: return
        val anshuTopDir = File(baseDir, "anshu-on-top")

        if (!anshuTopDir.exists()) anshuTopDir.mkdirs()

        val destinationPath = "/sdcard/Android/data/com.dts.freefireth/files"
        appendLog("Target destination: $destinationPath")

        val files = anshuTopDir.listFiles()
        if (files.isNullOrEmpty()) {
            appendLog("No files found inside 'anshu-on-top' folder to copy.")
            Toast.makeText(this, "No files found in anshu-on-top", Toast.LENGTH_SHORT).show()
            return
        }

        thread {
            executeShizukuCommand(arrayOf("mkdir", "-p", destinationPath))

            for (file in files) {
                if (file.isFile) {
                    val destFile = File(destinationPath, file.name)
                    executeShizukuCopy(file.absolutePath, destFile.absolutePath)
                    copiedFilesList.add(destFile)
                }
            }
            runOnUiThread {
                appendLog("All bundled files pasted successfully to Free Fire directory!")
                Toast.makeText(this, "Files Copied to Free Fire!", Toast.LENGTH_SHORT).show()
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
            appendLog("Copied: ${File(src).name}")
        } catch (e: Exception) {
            appendLog("Failed to copy ${File(src).name}: ${e.message}")
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
        removeFloatingView()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
