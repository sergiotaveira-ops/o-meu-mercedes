package pt.sergiotaveira.mercedes.ui.cards

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun LocationCard(lat: Double?, lon: Double?) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Localização", style = MaterialTheme.typography.titleMedium)
            if (lat == null || lon == null) {
                Text("Localização ainda não recebida.", modifier = Modifier.padding(top = 8.dp))
                return@Column
            }
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                    }
                },
                update = { map ->
                    val point = GeoPoint(lat, lon)
                    map.overlays.removeAll { it is Marker }
                    map.overlays += Marker(map).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "O teu carro"
                    }
                    map.controller.setCenter(point)
                    map.invalidate()
                },
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp),
            )
            Button(
                onClick = {
                    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(O meu carro)")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Navegar até lá") }
        }
    }
}
