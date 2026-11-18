package pt.sergiotaveira.mercedes.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.sergiotaveira.mercedes.api.LockState
import pt.sergiotaveira.mercedes.ui.Action

@Composable
fun DoorsCard(lock: LockState, busy: Set<Action>, lastResult: String?, onLock: () -> Unit, onUnlock: () -> Unit) {
    val working = Action.LOCK in busy || Action.UNLOCK in busy
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Portas", style = MaterialTheme.typography.titleMedium)
            Text(
                when (lock) {
                    LockState.LOCKED -> "Trancado"
                    LockState.UNLOCKED -> "Destrancado"
                    LockState.UNKNOWN -> "Estado desconhecido"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (working) {
                Text("A enviar ao carro… pode demorar até 1 minuto.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            } else if (lastResult != null) {
                Text("Último comando: $lastResult", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onLock, enabled = !working, modifier = Modifier.weight(1f)) {
                    if (Action.LOCK in busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Trancar")
                }
                OutlinedButton(onClick = onUnlock, enabled = !working, modifier = Modifier.weight(1f)) {
                    if (Action.UNLOCK in busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Destrancar")
                }
            }
        }
    }
}
