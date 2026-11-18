package pt.sergiotaveira.mercedes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pt.sergiotaveira.mercedes.api.ConnStatus

@Composable
fun StatusLine(status: ConnStatus, hasCar: Boolean) {
    val (color, text) = when (status) {
        ConnStatus.CONNECTED -> Color(0xFF4CAF50) to if (hasCar) "Ligado à Mercedes" else "Ligado à Mercedes · à espera dos dados do carro"
        ConnStatus.CONNECTING -> Color(0xFFFFB300) to "A ligar à Mercedes…"
        ConnStatus.DISCONNECTED -> Color(0xFF9E9E9E) to "Desligado · puxa para atualizar"
        ConnStatus.BLOCKED -> Color(0xFFE53935) to "Bloqueado pela Mercedes · tenta mais tarde"
        ConnStatus.AUTH_EXPIRED -> Color(0xFFE53935) to "Sessão recusada · entra de novo"
        ConnStatus.ERROR -> Color(0xFFE53935) to "Erro de ligação · a tentar de novo"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
