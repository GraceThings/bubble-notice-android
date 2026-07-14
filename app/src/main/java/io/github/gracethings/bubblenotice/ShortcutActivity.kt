package io.github.gracethings.bubblenotice

import android.app.Activity
import android.os.Bundle

class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity.sendBubbleNotification(this)
        finish()
    }
}
