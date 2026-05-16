package com.calllog.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * ExportManager — Call logs CSV export आणि HTML report generate करतो.
 */
object ExportManager {

    private val dateFormat     = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // ── CSV Export ──────────────────────────────────────────────────────────
    fun exportToCsv(context: Context, calls: List<CallLog>): File? {
        Timber.d("CSV export started — ${calls.size} records")
        return try {
            val fileName = "CallLog_${fileNameFormat.format(Date())}.csv"
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                fileName
            )

            FileWriter(file).use { writer ->
                writer.append("Name,Number,Call Type,Duration,Date & Time,SIM,Deleted from Phone?\n")
                calls.forEach { call ->
                    writer.append("${call.name},")
                    writer.append("${call.phoneNumber},")
                    writer.append("${callTypeLabel(call.callType)},")
                    writer.append("${formatDuration(call.duration)},")
                    writer.append("${dateFormat.format(Date(call.callDate))},")
                    writer.append("SIM ${call.simSlot + 1},")
                    writer.append("${if (call.isDeletedFromPhone) "Yes" else "No"}\n")
                }
            }

            Timber.i("CSV exported successfully — ${file.absolutePath} (${file.length() / 1024} KB)")
            file
        } catch (e: Exception) {
            Timber.e(e, "CSV export failed")
            null
        }
    }

    // ── Share via Intent ────────────────────────────────────────────────────
    fun shareFile(context: Context, file: File, mimeType: String = "text/csv") {
        Timber.d("Sharing file: ${file.name}")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Call Log Backup - ${dateFormat.format(Date())}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Call Log"))
    }

    // ── HTML Report ─────────────────────────────────────────────────────────
    fun generateHtmlReport(calls: List<CallLog>): String {
        Timber.d("Generating HTML report — ${calls.size} records")
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
              body { font-family: sans-serif; padding: 20px; }
              h1 { color: #333; }
              table { width: 100%; border-collapse: collapse; font-size: 13px; }
              th { background: #6200EE; color: white; padding: 8px; text-align: left; }
              td { padding: 6px 8px; border-bottom: 1px solid #eee; }
              tr:nth-child(even) { background: #f9f9f9; }
              .missed { color: #C62828; } .incoming { color: #2E7D32; } .outgoing { color: #1565C0; }
            </style></head><body>
            <h1>Call Log Report</h1>
            <p>Generated: ${dateFormat.format(Date())} | Total: ${calls.size} calls</p>
            <table><tr><th>Name</th><th>Number</th><th>Type</th><th>Duration</th><th>Date &amp; Time</th><th>SIM</th></tr>
        """.trimIndent())

        calls.forEach { call ->
            val typeClass = call.callType.name.lowercase()
            sb.append("""
                <tr>
                <td>${call.name}</td><td>${call.phoneNumber}</td>
                <td class="$typeClass">${callTypeLabel(call.callType)}</td>
                <td>${formatDuration(call.duration)}</td>
                <td>${dateFormat.format(Date(call.callDate))}</td>
                <td>SIM ${call.simSlot + 1}</td>
                </tr>
            """.trimIndent())
        }

        sb.append("</table></body></html>")
        Timber.d("HTML report generated — ${sb.length} chars")
        return sb.toString()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private fun callTypeLabel(type: CallType) = when (type) {
        CallType.INCOMING -> "Incoming"
        CallType.OUTGOING -> "Outgoing"
        CallType.MISSED   -> "Missed"
        CallType.REJECTED -> "Rejected"
        CallType.UNKNOWN  -> "Unknown"
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds == 0L) return "Not connected"
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return "${h}h ${m}m ${s}s"
    }
}
