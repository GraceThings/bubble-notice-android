package io.github.gracethings.bubblenotice.util

import android.content.Context
import io.github.gracethings.bubblenotice.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun formatMessageTime(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        // Less than 1 minute -> Just now (小于 1 分钟 -> 刚刚)
        if (diff < 60_000) {
            return context.getString(R.string.time_just_now)
        }
        
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val currentDay = cal.get(Calendar.DAY_OF_YEAR)
        val currentYear = cal.get(Calendar.YEAR)
        
        cal.timeInMillis = timestamp
        val msgDay = cal.get(Calendar.DAY_OF_YEAR)
        val msgYear = cal.get(Calendar.YEAR)
        
        return if (currentYear == msgYear && currentDay == msgDay) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        } else {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
