package pt.sergiotaveira.mercedes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pt.sergiotaveira.mercedes.ui.cards.BatteryCard
import pt.sergiotaveira.mercedes.ui.cards.ClimateCard
import pt.sergiotaveira.mercedes.ui.cards.DoorsCard
import pt.sergiotaveira.mercedes.ui.cards.LocationCard
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarScreen(ui: UiState, controller: CarController) {
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(ui, controller, snackbar)
    var menu by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    val now by produceState(ZonedDateTime.now()) { while (true) { delay(30_000); value = ZonedDateTime.now() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("O Meu Mercedes") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Terminar sessão") }, onClick = { menu = false; controller.logout() })
                    }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = Action.REFRESH in ui.busy,
            onRefresh = controller::refresh,
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val car = ui.car
                StatusLine(ui.connection, hasCar = car != null)
                if (car == null) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(16.dp))
                            Text("A ligar ao carro…")
                        }
                    }
                } else {
                    BatteryCard(car, now)
                    DoorsCard(car.lock, ui.busy, ui.lastResult, onLock = controller::lock, onUnlock = { askPin = true })
                    ClimateCard(car.climateOn, car.targetTempCelsius, ui.busy, onToggle = controller::toggleClimate, onSetTemperature = controller::setTemperature)
                    LocationCard(car.lat, car.lon)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(Format.updatedAgo(car.updatedAt, now.toInstant()), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (askPin) UnlockDialog(onConfirm = { pin -> askPin = false; controller.unlock(pin) }, onDismiss = { askPin = false })
}
