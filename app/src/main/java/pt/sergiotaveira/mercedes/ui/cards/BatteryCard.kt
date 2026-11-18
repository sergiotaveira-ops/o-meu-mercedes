package pt.sergiotaveira.mercedes.ui.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.sergiotaveira.mercedes.api.CarState
import pt.sergiotaveira.mercedes.ui.Format
import java.time.ZonedDateTime

@Composable
fun BatteryCard(car: CarState, now: ZonedDateTime) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Bateria", style = MaterialTheme.typography.titleMedium)
            Text(car.socPercent?.let { "$it %" } ?: "-- %", style = MaterialTheme.typography.displayMedium)
            car.socPercent?.let {
                LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            car.electricRangeKm?.let { Text("$it km elétricos", style = MaterialTheme.typography.bodyLarge) }
            Text(Format.charging(car, now), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
