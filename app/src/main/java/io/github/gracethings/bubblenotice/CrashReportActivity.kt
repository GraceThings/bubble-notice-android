/*
 * Copyright (C) 2026 Grace Chan <velviagris@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.gracethings.bubblenotice

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.gracethings.bubblenotice.util.LanguageSettings
import java.io.File

class CrashReportActivity : Activity() {

    companion object {
        const val EXTRA_CRASH_INFO = "extra_crash_info"
        const val EXTRA_CRASH_FILE_PATH = "extra_crash_file_path"
        private const val DEVELOPER_EMAIL = "velviagris@outlook.com"
    }

    override fun attachBaseContext(newBase: Context) {
        val wrapped = try {
            LanguageSettings.wrap(newBase, LanguageSettings.getSelected(newBase))
        } catch (_: Throwable) {
            newBase
        }
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashReport = loadCrashReport()
        showCrashDialog(crashReport)
    }

    private fun loadCrashReport(): String {
        val filePath = intent?.getStringExtra(EXTRA_CRASH_FILE_PATH)
        if (!filePath.isNullOrBlank()) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val text = file.readText()
                    if (text.isNotBlank()) return text
                }
            } catch (_: Throwable) {
            }
        }
        return intent?.getStringExtra(EXTRA_CRASH_INFO) ?: "No crash details available."
    }

    private fun showCrashDialog(crashReport: String) {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = dpToPx(24)
            val verticalPadding = dpToPx(8)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        val messageView = TextView(this).apply {
            text = getString(R.string.crash_dialog_message)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (isDarkMode) 0xFFDDDDDD.toInt() else 0xFF333333.toInt())
            setPadding(0, 0, 0, dpToPx(12))
        }
        container.addView(messageView)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(220)
            )
            background = GradientDrawable().apply {
                setColor(if (isDarkMode) 0xFF1E1E1E.toInt() else 0xFFF5F5F5.toInt())
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), if (isDarkMode) 0xFF3E3E3E.toInt() else 0xFFDDDDDD.toInt())
            }
        }

        val logView = TextView(this).apply {
            text = crashReport
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setTextColor(if (isDarkMode) 0xFFE0E0E0.toInt() else 0xFF222222.toInt())
            val p = dpToPx(10)
            setPadding(p, p, p, p)
        }
        scrollView.addView(logView)
        container.addView(scrollView)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.crash_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.crash_dialog_send_email, null)
            .setNeutralButton(R.string.crash_dialog_copy, null)
            .setNegativeButton(R.string.crash_dialog_close) { _, _ ->
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .create()

        dialog.show()

        // 覆盖 Positive 与 Neutral 按钮，避免点击后直接导致弹窗关闭
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            sendEmail(crashReport)
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            copyToClipboard(crashReport)
        }
    }

    private fun sendEmail(crashReport: String) {
        val subject = "[Bubble Notice Crash Report] ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        val uri = Uri.parse("mailto:$DEVELOPER_EMAIL?subject=${Uri.encode(subject)}")
        val emailIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_TEXT, crashReport)
        }

        try {
            startActivity(emailIntent)
        } catch (_: Exception) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, crashReport)
            }
            try {
                startActivity(Intent.createChooser(sendIntent, getString(R.string.crash_dialog_share_chooser)))
            } catch (_: Exception) {
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, crashReport)
                }
                try {
                    startActivity(Intent.createChooser(textIntent, getString(R.string.crash_dialog_share_chooser)))
                } catch (_: Exception) {
                    Toast.makeText(this, "No app available to send email.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun copyToClipboard(crashReport: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bubble Notice Crash Log", crashReport)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.crash_dialog_copied, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Failed to copy log.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }
}
