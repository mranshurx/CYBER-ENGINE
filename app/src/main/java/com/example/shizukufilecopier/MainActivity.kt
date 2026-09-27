package com.example.shizukufilecopier

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream

/**
 * Minimal "copy/paste anywhere" app powered by Shizuku.
 *
 * Flow:
 *  1. User grants Shizuku permission (Shizuku app must already be
 *     running - either paired over ADB/Wireless-debugging, or via root).
 *  2. User picks a file via the system file picker (SAF).
 *  3. The file's bytes are streamed into this app's private cache dir
 *     (apps can always read/write their own cache without special
 *     permission).
 *  4. Shizuku is used to run a shell "cp" command that copies the file
 *     from the cache dir to whatever destination path the user typed
 *     in (e.g. /sdcard/Download/, /storage/emulated/0/, or any other
 *     path the shell user can reach). This is what lets the app write
 *     to arbitrary paths without needing root or being limited by
 *     scoped storage.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var selectedFileText: TextView
    private lateinit var destPathEditText: EditText
    private lateinit var logText: TextView

    private var pickedUris: List<Uri> = emptyList()

    private val requestPermissionCode = 1001

    private val pickFilesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                pickedUris = uris
                selectedFileText.text = "${uris.size} file(s) selected"
                log("Selected ${uris.size} file(s).")
            } else {
                log("No file selected.")
            }
        }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                statusText.text = "Shizuku: permission granted"
                log("Shizuku permission granted.")
            } else {
                statusText.text = "Shizuku: permission denied"
                log("Shizuku permission denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        selectedFileText = findViewById(R.id.selectedFileText)
        destPathEditText = findViewById(R.id.destPathEditText)
        logText = findViewById(R.id.logText)

        findViewById<Button>(R.id.requestPermissionButton).setOnClickListener {
            requestShizukuPermission()
        }

        findViewById<Button>(R.id.pickFileButton).setOnClickListener {
            pickFilesLauncher.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.copyButton).setOnClickListener {
            copySelectedFilesToDestination()
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun refreshStatus() {
        statusText.text = try {
            when {
                !Shizuku.pingBinder() -> "Shizuku: service not running (open the Shizuku app first)"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    "Shizuku: permission granted"
                else -> "Shizuku: permission not granted yet"
            }
        } catch (e: Exception) {
            "Shizuku: not available (${e.message})"
        }
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            log("Shizuku service is not running. Open the Shizuku app and start the service first.")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            log("Already have Shizuku permission.")
            refreshStatus()
            return
        }
        Shizuku.requestPermission(requestPermissionCode)
    }

    private fun copySelectedFilesToDestination() {
        val destPath = destPathEditText.text.toString().trim()
        if (destPath.isEmpty()) {
            log("Enter a destination path first.")
            return
        }
        if (pickedUris.isEmpty()) {
            log("Pick at least one file first.")
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            log("Shizuku permission not granted yet.")
            return
        }

        // Make sure destination directory exists (via shell, so it can be
        // a path this app wouldn't normally be allowed to create).
        runShell("mkdir -p '${destPath.trimEnd('/')}'")

        for (uri in pickedUris) {
            try {
                val fileName = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
                val tempFile = File(cacheDir, fileName)

                contentResolver.openInputStream(uri).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input?.copyTo(output)
                    }
                }

                val destFile = "${destPath.trimEnd('/')}/$fileName"
                val result = runShell("cp '${tempFile.absolutePath}' '$destFile'")
                if (result.exitCode == 0) {
                    log("Copied: $fileName -> $destFile")
                } else {
                    log("Failed to copy $fileName: ${result.output}")
                }

                tempFile.delete()
            } catch (e: Exception) {
                log("Error copying file: ${e.message}")
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                return it.getString(nameIndex)
            }
        }
        return null
    }

    private data class ShellResult(val exitCode: Int, val output: String)

    /**
     * Runs a shell command through Shizuku's elevated process (adb shell
     * or root, depending on how Shizuku was started). This is what allows
     * writing to paths outside this app's normal sandbox.
     */
    private fun runShell(command: String): ShellResult {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().readText() +
                process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            ShellResult(exitCode, output)
        } catch (e: Exception) {
            ShellResult(-1, e.message ?: "unknown error")
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            logText.append("$message\n")
        }
    }
}
