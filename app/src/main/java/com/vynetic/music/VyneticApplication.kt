package com.vynetic.music

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VyneticApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    appendLine("========================================")
                    appendLine("VYNETIC CRASH REPORT")
                    appendLine("Timestamp: $timestamp")
                    appendLine("Thread: ${thread.name} (id: ${thread.id})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Exception: ${throwable.javaClass.name}")
                    appendLine("Message: ${throwable.message}")
                    appendLine("========================================")
                    appendLine("STACK TRACE:")
                    appendLine(stackTrace)
                    appendLine("========================================")
                }

                // 1. Save to SharedPreferences so MainActivity can display it upon restart
                getSharedPreferences("vynetic_prefs", 0)
                    .edit()
                    .putString("last_crash", report)
                    .commit()

                // 2. Save to internal app files
                try {
                    File(filesDir, "vynetic_crash.txt").writeText(report)
                } catch (_: Throwable) {}

                // 3. Try direct write to Download
                try {
                    val publicFile = File("/storage/emulated/0/Download/vynetic_crash.txt")
                    publicFile.writeText(report)
                } catch (_: Throwable) {}

                // 4. Write to MediaStore Downloads (works on Android 10+ without storage permissions)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "vynetic_crash.txt")
                            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        uri?.let {
                            contentResolver.openOutputStream(it)?.use { stream ->
                                stream.write(report.toByteArray())
                            }
                        }
                    }
                } catch (_: Throwable) {}

                Log.e("VyneticApp", report)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
