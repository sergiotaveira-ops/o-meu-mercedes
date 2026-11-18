package pt.sergiotaveira.mercedes

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import pt.sergiotaveira.mercedes.api.EncryptedTokenStore
import pt.sergiotaveira.mercedes.api.MbAuth
import pt.sergiotaveira.mercedes.api.MbSocket
import pt.sergiotaveira.mercedes.api.OkHttpTransport
import pt.sergiotaveira.mercedes.api.OkHttpWsFactory
import pt.sergiotaveira.mercedes.data.CarRepository
import pt.sergiotaveira.mercedes.data.MbCarRepository
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Builds the app's single [CarRepository]; lives as long as the process. */
object ServiceLocator {
    @Volatile private var repo: CarRepository? = null

    fun repository(context: Context): CarRepository = repo ?: synchronized(this) {
        repo ?: create(context.applicationContext).also { repo = it }
    }

    private fun create(context: Context): CarRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        val store = EncryptedTokenStore(context)
        val auth = MbAuth(OkHttpTransport(http, store.deviceId()), store)
        val socket = MbSocket(OkHttpWsFactory(http), auth, scope, zone = ZoneId.systemDefault())
        return MbCarRepository(auth, socket, store, scope)
    }
}
