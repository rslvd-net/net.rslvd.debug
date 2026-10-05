package net.rslvd.terminal

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val TerminalBackground = Color(0xFF0A0A0F)

private val Colors = darkColorScheme(
    primary = Color(0xFF818CF8),
    secondary = Color(0xFF22D3EE),
    background = TerminalBackground,
    surface = Color(0xFF15151C),
)

@Composable
fun RslvdTerminalTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = Colors, content = content)
}
