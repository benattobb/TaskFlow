package com.example.taskflow.widget

import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews

/** Android 17 keeps widget spacing consistent when it is displayed on another screen. */
object WidgetStyle {
    fun applySurfacePadding(views: RemoteViews, rootId: Int) {
        if (Build.VERSION.SDK_INT >= 37) {
            runCatching {
                // The API 37 overload resolves dimensions on the host display, including external displays.
                RemoteViews::class.java.getMethod(
                    "setViewPadding",
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(views, rootId, 16f, 12f, 16f, 12f, TypedValue.COMPLEX_UNIT_DIP)
            }
        }
    }
}
