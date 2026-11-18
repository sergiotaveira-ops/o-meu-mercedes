package pt.sergiotaveira.mercedes.ui.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClimateCard(on: Boolean?, busy: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pré-climatização", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (on) { true -> "Ligada"; false -> "Desligada"; null -> "Estado desconhecido" },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            else Switch(checked = on == true, onCheckedChange = { onToggle() })
        }
    }
}
