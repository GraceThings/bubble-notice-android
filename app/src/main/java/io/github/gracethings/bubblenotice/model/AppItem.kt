package io.github.gracethings.bubblenotice.model

import android.graphics.drawable.Drawable

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable
)
