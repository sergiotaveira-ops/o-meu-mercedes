package pt.sergiotaveira.mercedes.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pt.sergiotaveira.mercedes.ui.Action

private const val MIN_TEMP = 16.0f
private const val MAX_TEMP = 28.0f

@Composable
fun ClimateCard(on: Boolean?, busy: Set<Action>, onToggle: () -> Unit, onSetTemperature: (Double) -> Unit) {
    val climateBusy = Action.CLIMATE in busy
    val tempBusy = Action.TEMPERATURE in busy
    var temp by remember { mutableFloatStateOf(21.0f) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pré-climatização", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (on) { true -> "Ligada"; false -> "Desligada"; null -> "Estado desconhecido" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (climateBusy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                else Switch(checked = on == true, onCheckedChange = { onToggle() })
            }

            Divider(Modifier.padding(vertical = 16.dp))

            Text("Temperatura", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { temp = (temp - 0.5f).coerceAtLeast(MIN_TEMP) },
                    enabled = !tempBusy && temp > MIN_TEMP,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Icon(Icons.Default.Remove, contentDescription = "Baixar") }

                Text(
                    "%.1f °C".format(temp),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )

                OutlinedButton(
                    onClick = { temp = (temp + 0.5f).coerceAtMost(MAX_TEMP) },
                    enabled = !tempBusy && temp < MAX_TEMP,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Icon(Icons.Default.Add, contentDescription = "Subir") }
            }
            Button(
                onClick = { onSetTemperature(temp.toDouble()) },
                enabled = !tempBusy,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (tempBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Definir temperatura")
            }
        }
    }
}
