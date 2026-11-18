package pt.sergiotaveira.mercedes.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Shows [UiState.message] once as a snackbar, then clears it. Clearing first would restart this effect and cancel the snackbar. */
@Composable
fun ShowMessages(ui: UiState, controller: CarController, host: SnackbarHostState) {
    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        host.showSnackbar(msg)
        controller.messageShown()
    }
}
