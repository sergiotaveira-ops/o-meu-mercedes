package pt.sergiotaveira.mercedes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.osmdroid.config.Configuration
import pt.sergiotaveira.mercedes.ui.App
import pt.sergiotaveira.mercedes.ui.CarViewModel

class MainActivity : ComponentActivity() {
    private val vm: CarViewModel by viewModels {
        viewModelFactory { initializer { CarViewModel(ServiceLocator.repository(applicationContext)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = cacheDir.resolve("tiles")
        }
        setContent { App(vm.controller) }
    }
}
