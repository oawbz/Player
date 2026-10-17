package cn.awbz.davplayer.ui

import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

class SystemBarsTest {
    @Test fun modernWindowInstallsDecorBeforeReadingInsetsController() {
        mockStatic(WindowInsets.Type::class.java).use { types ->
            types.`when`<Int> { WindowInsets.Type.systemBars() }.thenReturn(3)
        for (fullscreen in listOf(false, true)) {
            val window = mock(Window::class.java)
            val decor = mock(View::class.java)
            val controller = mock(WindowInsetsController::class.java)
            var installed = false
            `when`(window.decorView).thenAnswer { installed = true; decor }
            doAnswer {
                check(installed) { "Decor must exist before PhoneWindow.getInsetsController" }
                controller
            }.`when`(window).insetsController
            SystemBars.applyModern(window, fullscreen)
            assertTrue(installed)
            if (fullscreen) verify(controller).hide(WindowInsets.Type.systemBars())
            else verify(controller).show(WindowInsets.Type.systemBars())
        }
        }
    }
    @Test fun api18UsesLegacyFlagsWithoutImmersiveOrModernInsets() {
        val window = mock(Window::class.java)
        val decor = mock(View::class.java)
        `when`(window.decorView).thenReturn(decor)
        SystemBars.apply(window, true)
        verify(decor).systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        verify(window, never()).insetsController
    }
}
