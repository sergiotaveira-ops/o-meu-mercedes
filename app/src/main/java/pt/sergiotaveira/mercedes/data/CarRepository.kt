package pt.sergiotaveira.mercedes.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pt.sergiotaveira.mercedes.api.AuthError
import pt.sergiotaveira.mercedes.api.CarState
import pt.sergiotaveira.mercedes.api.CommandResult
import pt.sergiotaveira.mercedes.api.Commands
import pt.sergiotaveira.mercedes.api.ConnStatus
import pt.sergiotaveira.mercedes.api.MbAuth
import pt.sergiotaveira.mercedes.api.MbSocket
import pt.sergiotaveira.mercedes.api.SocketError
import pt.sergiotaveira.mercedes.api.TokenStore
import pt.sergiotaveira.mercedes.ui.Messages
import java.util.UUID

interface CarRepository {
    val state: StateFlow<CarState?>
    val connection: StateFlow<ConnStatus>
    /** Connection problems, already as user-facing Portuguese text. */
    val errors: Flow<String>
    /** @throws AuthError */
    suspend fun login(email: String, password: String)
    fun isLoggedIn(): Boolean
    fun refresh()
    suspend fun lock(): CommandResult
    suspend fun unlock(pin: String): CommandResult
    suspend fun setClimate(on: Boolean): CommandResult
    fun logout()
}

/** Uses the first car on the account. */
class MbCarRepository(
    private val auth: MbAuth,
    private val socket: MbSocket,
    private val store: TokenStore,
    scope: CoroutineScope,
) : CarRepository {
    override val state: StateFlow<CarState?> =
        socket.states.map { it.values.firstOrNull() }.stateIn(scope, SharingStarted.Eagerly, null)

    override val connection: StateFlow<ConnStatus> = socket.status

    override val errors: Flow<String> = socket.errors.map {
        when (it) {
            SocketError.Blocked -> Messages.BLOCKED
            SocketError.AuthExpired -> Messages.SESSION_EXPIRED
            is SocketError.Network -> Messages.NO_NETWORK
        }
    }

    override suspend fun login(email: String, password: String) {
        auth.login(email, password)
        socket.connect()
    }

    override fun isLoggedIn() = store.load() != null

    override fun refresh() {
        socket.disconnect()
        socket.connect()
    }

    private suspend fun command(build: (vin: String, requestId: String) -> pt.sergiotaveira.mercedes.proto.ClientMessage): CommandResult {
        val vin = state.value?.vin ?: return CommandResult.Failed(Messages.NO_CAR)
        return socket.send(build(vin, UUID.randomUUID().toString()))
    }

    override suspend fun lock() = command { v, r -> Commands.lock(v, r) }
    override suspend fun unlock(pin: String) = command { v, r -> Commands.unlock(v, pin, r) }
    override suspend fun setClimate(on: Boolean) =
        command { v, r -> if (on) Commands.climateStart(v, r) else Commands.climateStop(v, r) }

    override fun logout() {
        socket.disconnect()
        store.clear()
    }
}
