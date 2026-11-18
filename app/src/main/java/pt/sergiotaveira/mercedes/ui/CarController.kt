package pt.sergiotaveira.mercedes.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.sergiotaveira.mercedes.api.AuthError
import pt.sergiotaveira.mercedes.api.CarState
import pt.sergiotaveira.mercedes.api.CommandResult
import pt.sergiotaveira.mercedes.data.CarRepository

enum class Action { LOCK, UNLOCK, CLIMATE, REFRESH, LOGIN }

data class UiState(
    val loggedIn: Boolean,
    val car: CarState? = null,
    val busy: Set<Action> = emptySet(),
    val message: String? = null,
)

/** Screen logic, free of Android classes so it runs in plain JVM tests. [CarViewModel] hosts it. */
class CarController(private val repo: CarRepository, private val scope: CoroutineScope) {
    private val _ui = MutableStateFlow(UiState(loggedIn = repo.isLoggedIn()))
    val ui: StateFlow<UiState> = _ui

    init {
        scope.launch { repo.state.collect { car -> _ui.update { it.copy(car = car) } } }
        scope.launch {
            repo.errors.collect { msg ->
                _ui.update { it.copy(message = msg, loggedIn = if (msg == Messages.SESSION_EXPIRED) false else it.loggedIn) }
            }
        }
        if (repo.isLoggedIn()) repo.refresh()
    }

    private fun run(action: Action, block: suspend () -> String?) {
        val started = synchronized(this) {
            if (action in _ui.value.busy) false else { _ui.update { it.copy(busy = it.busy + action) }; true }
        }
        if (!started) return
        scope.launch {
            val msg = try { block() } finally { _ui.update { it.copy(busy = it.busy - action) } }
            if (msg != null) _ui.update { it.copy(message = msg) }
        }
    }

    private fun commandMessage(r: CommandResult, ok: String, failed: (String) -> String = Messages::refused) = when (r) {
        CommandResult.Success -> ok
        CommandResult.Timeout -> Messages.TIMEOUT
        is CommandResult.Failed -> failed(r.reason)
    }

    fun login(email: String, password: String) = run(Action.LOGIN) {
        try {
            repo.login(email, password)
            _ui.update { it.copy(loggedIn = true) }
            null
        } catch (e: AuthError.WrongCredentials) { Messages.WRONG_LOGIN
        } catch (e: AuthError.Blocked) { Messages.BLOCKED
        } catch (e: AuthError.Network) { Messages.NO_NETWORK
        } catch (e: AuthError) { Messages.loginFailed(e.message ?: "")
        }
    }

    fun refresh() = run(Action.REFRESH) { repo.refresh(); null }

    fun lock() = run(Action.LOCK) { commandMessage(repo.lock(), Messages.LOCKED_OK) }

    fun unlock(pin: String) = run(Action.UNLOCK) { commandMessage(repo.unlock(pin), Messages.UNLOCKED_OK) { Messages.UNLOCK_FAILED } }

    fun toggleClimate() = run(Action.CLIMATE) {
        val on = _ui.value.car?.climateOn != true
        commandMessage(repo.setClimate(on), if (on) Messages.CLIMATE_ON_OK else Messages.CLIMATE_OFF_OK)
    }

    fun messageShown() = _ui.update { it.copy(message = null) }

    fun logout() {
        repo.logout()
        _ui.value = UiState(loggedIn = false)
    }
}
