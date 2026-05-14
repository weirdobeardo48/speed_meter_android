package com.truongnx.speedmeter

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

class CrashApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Convert stacktrace to string
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                pw.flush()
                val crashText = """
                    Thread: ${thread.name}
                    ${sw.toString()}
                """.trimIndent()

                // Save to SharedPreferences (or a file)
                getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", crashText)
                    .commit() // must be synchronous — process dies immediately after

                Log.e("CrashApp", "Uncaught exception captured", throwable)
            } catch (e: Exception) {
                // best-effort only
                Log.e("CrashApp", "Error while saving crash", e)
            }

            // Let the system still show its normal crash dialog
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
