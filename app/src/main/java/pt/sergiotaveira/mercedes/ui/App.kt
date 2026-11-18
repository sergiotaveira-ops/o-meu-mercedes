package pt.sergiotaveira.mercedes.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val Colors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    background = Color(0xFF101114),
    surface = Color(0xFF101114),
    surfaceVariant = Color(0xFF1D1F24),
)

@Composable
fun App(controller: CarController) {
    val ui by controller.ui.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = Colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (ui.loggedIn) CarScreen(ui, controller) else LoginScreen(ui, controller)
        }
    }
}
