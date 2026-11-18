package pt.sergiotaveira.mercedes.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import pt.sergiotaveira.mercedes.api.AuthError
import pt.sergiotaveira.mercedes.api.CarState
import pt.sergiotaveira.mercedes.api.CommandResult
import pt.sergiotaveira.mercedes.api.ConnStatus
import pt.sergiotaveira.mercedes.data.CarRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeCarRepository : CarRepository {
    override val state = MutableStateFlow<CarState?>(null)
    override val connection = MutableStateFlow(ConnStatus.DISCONNECTED)
    val errorChannel = Channel<String>(Channel.BUFFERED)
    override val errors: Flow<String> = errorChannel.receiveAsFlow()
    var loggedIn = true
    var loginError: Throwable? = null
    var nextResult = CompletableDeferred<CommandResult>()
    var lockCalls = 0
    var lastPin: String? = null
    var climate: Boolean? = null
    var refreshes = 0
    override suspend fun login(email: String, password: String) { loginError?.let { throw it }; loggedIn = true }
    override fun isLoggedIn() = loggedIn
    override fun refresh() { refreshes++ }
    override suspend fun lock(): CommandResult { lockCalls++; return nextResult.await() }
    override suspend fun unlock(pin: String): CommandResult { lastPin = pin; return nextResult.await() }
    override suspend fun setClimate(on: Boolean): CommandResult { climate = on; return nextResult.await() }
    override fun logout() { loggedIn = false }
}

class CarControllerTest {
    private fun run(loggedIn: Boolean = true, block: suspend (CarController, FakeCarRepository) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = FakeCarRepository().apply { this.loggedIn = loggedIn }
        try { block(CarController(repo, scope), repo) } finally { scope.cancel() }
    }

    private suspend fun waitFor(cond: () -> Boolean) = withTimeout(2000) { while (!cond()) delay(5) }

    @Test
    fun lockSuccessShowsMessageAndClearsBusy() = run { c, repo ->
        c.lock()
        waitFor { Action.LOCK in c.ui.value.busy }
        repo.nextResult.complete(CommandResult.Success)
        waitFor { c.ui.value.message != null }
        assertEquals(Messages.LOCKED_OK, c.ui.value.message)
        assertTrue(c.ui.value.busy.isEmpty())
    }

    @Test
    fun unlockFailedShowsUnlockFailed() = run { c, repo ->
        c.unlock("0000")
        repo.nextResult.complete(CommandResult.Failed("PIN invalid"))
        waitFor { c.ui.value.message != null }
        assertEquals(Messages.UNLOCK_FAILED, c.ui.value.message)
        assertEquals("0000", repo.lastPin)
    }

    @Test
    fun timeoutShowsTimeout() = run { c, repo ->
        c.lock(); repo.nextResult.complete(CommandResult.Timeout)
        waitFor { c.ui.value.message != null }
        assertEquals(Messages.TIMEOUT, c.ui.value.message)
    }

    @Test
    fun blockedErrorShowsBlocked() = run { c, repo ->
        repo.errorChannel.send(Messages.BLOCKED)
        waitFor { c.ui.value.message != null }
        assertEquals(Messages.BLOCKED, c.ui.value.message)
    }

    @Test
    fun buttonsDisabledWhileBusy() = run { c, repo ->
        c.lock(); waitFor { Action.LOCK in c.ui.value.busy }
        c.lock(); delay(50)
        assertEquals(1, repo.lockCalls)
        repo.nextResult.complete(CommandResult.Success)
    }

    @Test
    fun toggleClimateUsesCurrentState() = run { c, repo ->
        repo.state.value = CarState(vin = "V", climateOn = false, updatedAt = Instant.EPOCH)
        waitFor { c.ui.value.car != null }
        c.toggleClimate(); waitFor { repo.climate != null }
        assertEquals(true, repo.climate)
        repo.nextResult.complete(CommandResult.Success)
        waitFor { c.ui.value.message != null }
        assertEquals(Messages.CLIMATE_ON_OK, c.ui.value.message)
    }

    @Test
    fun wrongLoginShowsMessageAndStaysLoggedOut() = run(loggedIn = false) { c, repo ->
        repo.loginError = AuthError.WrongCredentials()
        c.login("a@b.pt", "x")
        waitFor { c.ui.value.message != null }
        assertEquals(Messages.WRONG_LOGIN, c.ui.value.message)
        assertFalse(c.ui.value.loggedIn)
    }

    @Test
    fun messageShownClearsMessage() = run { c, repo ->
        c.lock(); repo.nextResult.complete(CommandResult.Success)
        waitFor { c.ui.value.message != null }
        c.messageShown()
        assertEquals(null, c.ui.value.message)
    }

    @Test
    fun lastResultStaysAfterMessageShown() = run { c, repo ->
        c.unlock("1234"); repo.nextResult.complete(CommandResult.Failed("PIN invalid"))
        waitFor { c.ui.value.message != null }
        c.messageShown()
        assertEquals(Messages.UNLOCK_FAILED, c.ui.value.lastResult)
    }

    @Test
    fun connectionIsReflected() = run { c, repo ->
        repo.connection.value = ConnStatus.CONNECTED
        waitFor { c.ui.value.connection == ConnStatus.CONNECTED }
    }

    @Test
    fun logoutClearsLogin() = run { c, _ ->
        c.logout()
        assertFalse(c.ui.value.loggedIn)
    }
}
