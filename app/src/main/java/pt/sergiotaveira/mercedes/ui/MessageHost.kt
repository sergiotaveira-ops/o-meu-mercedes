package pt.sergiotaveira.mercedes.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Shows [UiState.message] once as a snackbar, then clears it. */
@Composable
fun ShowMessages(ui: UiState, controller: CarController, host: SnackbarHostState) {
    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        controller.messageShown()
        host.showSnackbar(msg)
    }
}
