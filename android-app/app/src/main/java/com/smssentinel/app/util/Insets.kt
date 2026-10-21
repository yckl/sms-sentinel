package com.smssentinel.app.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object Insets {
    fun apply(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                view.paddingLeft,
                bars.top,
                view.paddingRight,
                bars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
