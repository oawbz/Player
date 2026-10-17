package cn.awbz.davplayer.ui

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/** Hide app system bars in landscape; a system edge swipe can still reveal transient controls. */
internal object SystemBars {
    fun apply(window: Window, fullscreen: Boolean) {
        if (fullscreen) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= 30) applyModern(window, fullscreen)
        else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (fullscreen) View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                (if (Build.VERSION.SDK_INT >= 19) View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else 0) else View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    @androidx.annotation.RequiresApi(30)
    internal fun applyModern(window: Window, fullscreen: Boolean) {
        // PhoneWindow.getInsetsController requires an installed DecorView, even before setContentView.
        window.decorView
        window.setDecorFitsSystemWindows(!fullscreen)
        window.insetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) hide(WindowInsets.Type.systemBars()) else show(WindowInsets.Type.systemBars())
        }
    }
}
