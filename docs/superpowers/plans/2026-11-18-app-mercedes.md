# App Mercedes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android app (Kotlin) that logs into a Mercedes me account and lets Sergio lock/unlock his C300de, see its location, start/stop pre-climatisation, and see battery % and predicted end of charge.

**Architecture:** Single-module Android app. A pure-Kotlin `api` layer (OAuth login, protobuf WebSocket client, state parser, command builder) ported from `ReneNulschDE/mbapi2020` (MIT, cloned at `/home/claude/ref`), a `CarRepository` + `CarViewModel` on top, and a Compose UI. No server.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (Material 3), OkHttp 4 (HTTP + WebSocket), protobuf-kotlin-lite via `com.google.protobuf` Gradle plugin, kotlinx-coroutines, androidx.security-crypto, osmdroid; tests with JUnit4, OkHttp MockWebServer, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-18-app-mercedes-design.md`

## Global Constraints

- Build environment: this workspace cannot reach `dl.google.com`/Maven Central, so **all compilation and unit tests run in GitHub Actions** (`.github/workflows/build.yml`) and in Android Studio. Every "Run" step below means: push and read the workflow result, or `./gradlew <task>` where the SDK exists.
- `minSdk 26`, `targetSdk 35`, `applicationId pt.sergiotaveira.mercedes`, app name "O Meu Mercedes".
- Region EMEA only: REST/WebSocket hosts `*.emea-prod.mobilesdk.mercedes-benz.com`, WebSocket `wss://websocket.emea-prod.mobilesdk.mercedes-benz.com/v2/ws`.
- All user-visible text in European Portuguese.
- Password, tokens: `EncryptedSharedPreferences` only. PIN: never persisted.
- Unlock requires a confirmation dialog + PIN every time.
- No background polling; data refreshes on app open and pull-to-refresh.
- Header values (user agents, `ris-*`, app version) copied from reference `const.py` / `app_version.py` into one file, `api/MbConstants.kt`, so they can be updated in one place.

## Review Focus

1. **Access token expires while the app is open** → refreshed silently with the refresh token; only a failed refresh sends the user back to login.
2. **Mercedes answers 429 / blocks the WebSocket** → message "A Mercedes bloqueou temporariamente os pedidos. Tenta daqui a uns minutos." and no automatic retry loop.
3. **Command sent but no result within 60 s** → button returns to normal, message "O carro não respondeu."; never shows success without a `CommandStatus` FINISHED.
4. **Car not charging / no end-of-charge value** → card shows "Não está a carregar", never "00:00" or a past time.
5. **Wrong PIN on unlock** → command FAILED is shown as "PIN incorreto ou comando recusado." and the doors state is unchanged.

---

### Task 0: Project skeleton, CI and protobuf definitions

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/*` (wrapper 8.14.3), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (INTERNET permission), `app/src/main/java/pt/sergiotaveira/mercedes/MainActivity.kt` (empty Compose screen "O Meu Mercedes")
- Create: `tools/extract_protos.py`, output `app/src/main/proto/*.proto`
- Create: `.github/workflows/build.yml`
- Test: `app/src/test/java/pt/sergiotaveira/mercedes/ProtoSmokeTest.kt`

**Interfaces:**
- Produces: generated Kotlin/Java classes for `vehicle_events.proto` (`PushMessage`, `VEPUpdate`, `VehicleAttributeStatus`), `client.proto` (`ClientMessage`), `vehicle_commands.proto` (`CommandRequest`, `DoorsLock`, `DoorsUnlock`, `ZEVPreconditioningStart`, `ZEVPreconditioningStop`, `ZEVPreconditioningType`), and their imports.

- [ ] **Step 1:** Write `tools/extract_protos.py`: imports each `*_pb2.py` from the reference `proto/` dir (run with `pip install protobuf`), reads `DESCRIPTOR.serialized_pb` into `FileDescriptorProto`, and writes `.proto` source text (proto3/proto2 as declared, packages and imports preserved, `option java_package = "pt.sergiotaveira.mercedes.proto"`). Run it; expect one `.proto` per `_pb2.py`.
- [ ] **Step 2:** Verify locally: `pip install grpcio-tools` then `python -m grpc_tools.protoc -Iapp/src/main/proto --python_out=/tmp/out app/src/main/proto/*.proto` exits 0.
- [ ] **Step 3:** Write the failing test `ProtoSmokeTest.buildsDoorsLockCommand`: builds `ClientMessage` with `commandRequest.vin = "W1K0000000000000"`, `requestId = "r1"`, `doorsLock` set; asserts round-trip `ClientMessage.parseFrom(bytes).commandRequest.vin == "W1K0000000000000"` and `hasDoorsLock()`.
- [ ] **Step 4:** Gradle files + workflow: workflow on push runs `./gradlew testDebugUnitTest assembleDebug` (JDK 17, `android-actions/setup-android`), uploads `app/build/outputs/apk/debug/*.apk` as artifact `app-debug`.
- [ ] **Step 5:** Run CI. Expected: test passes, `app-debug` artifact present.
- [ ] **Step 6:** Commit `feat: esqueleto do projeto, CI e protos`.

### Task 1: Login (OAuth2 + PKCE) and token storage

**Files:**
- Create: `app/src/main/java/pt/sergiotaveira/mercedes/api/MbConstants.kt`, `api/MbAuth.kt`, `api/TokenStore.kt`, `api/Pkce.kt`
- Test: `app/src/test/java/.../api/PkceTest.kt`, `api/MbAuthTest.kt`

**Interfaces:**
- Produces:
  - `data class Tokens(val accessToken: String, val refreshToken: String, val expiresAtEpochSec: Long)`
  - `interface TokenStore { fun load(): Tokens?; fun save(t: Tokens); fun clear(); fun saveCredentials(email: String, password: String); fun credentials(): Pair<String,String>? }` with `EncryptedTokenStore(context)` and `InMemoryTokenStore` (tests)
  - `class MbAuth(http: OkHttpClient, store: TokenStore, baseUrls: MbUrls = MbUrls.EMEA)` with `suspend fun login(email: String, password: String): Tokens`, `suspend fun validAccessToken(): String` (refreshes when `expiresAtEpochSec - now < 60`), `sealed class AuthError : Exception` { `WrongCredentials`, `Blocked`, `Network`, `Unexpected(msg)` }
  - `object Pkce { fun verifier(): String; fun challenge(verifier: String): String }`

- [ ] **Step 1:** Failing tests: `PkceTest.verifierIsUrlSafeAndLongEnough` (length in 43..128, matches `^[A-Za-z0-9\-._~]+$`); `PkceTest.challengeMatchesRfc7636Example` (verifier `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` → `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`).
- [ ] **Step 2:** Failing tests in `MbAuthTest` with MockWebServer replaying the reference flow (`oauth.py` lines 106-435: authorization resume → user-agent info → username → password → legal consent if asked → resume → code → token exchange): `loginStoresTokens`, `wrongPasswordThrowsWrongCredentials` (password endpoint returns 401), `expiredTokenIsRefreshed` (store holds token expiring now; refresh endpoint returns new access token; `validAccessToken()` returns it and store updated), `failedRefreshFallsBackToStoredCredentials`, `http429ThrowsBlocked`.
- [ ] **Step 3:** Run tests (CI) → FAIL.
- [ ] **Step 4:** Implement by porting `oauth.py` step by step; URLs, client id, redirect URI and headers into `MbConstants.kt` / `MbUrls`. Cookies via an in-memory `CookieJar` per login. `EncryptedTokenStore` uses `MasterKey` AES256_GCM.
- [ ] **Step 5:** Run tests → PASS.
- [ ] **Step 6:** Commit `feat: login Mercedes com PKCE e tokens encriptados`.

### Task 2: Car state parser

**Files:**
- Create: `api/CarState.kt`, `api/CarStateParser.kt`
- Test: `api/CarStateParserTest.kt`

**Interfaces:**
- Produces:
  - `enum class LockState { LOCKED, UNLOCKED, UNKNOWN }`
  - `enum class ChargingState { CHARGING, PLUGGED_NOT_CHARGING, UNPLUGGED, UNKNOWN }`
  - `data class CarState(val vin: String, val socPercent: Int?, val electricRangeKm: Int?, val charging: ChargingState, val endOfCharge: ZonedDateTime?, val lock: LockState, val lat: Double?, val lon: Double?, val climateOn: Boolean?, val updatedAt: Instant)`
  - `object CarStateParser { fun merge(previous: CarState?, update: VEPUpdate, zone: ZoneId, now: ZonedDateTime): CarState }` — partial updates keep previous values for absent attributes.

Attribute mapping (from reference `const.py`/`client.py`/`lock.py`): `soc` int → socPercent; `rangeelectric` → km; `doorlockstatusvehicle` 1|2 → LOCKED, 0|3 → UNLOCKED; `positionLat`/`positionLong` double; `precondNow` bool (fallback `precondActive`) → climateOn; `chargingstatus` (reference `vsu_enums.py`): 0, 5, 6, 9, 10, 11, 13, 14 → CHARGING; 1, 2, 4, 7, 8, 12, 15 → PLUGGED_NOT_CHARGING; 3 → UNPLUGGED; 16 or absent → UNKNOWN; `endofchargetime` int = minutes after midnight local, weekday from `endofChargeTimeWeekday` (Monday=0) else today/tomorrow rule as in `client.py` 950-975; ignore attributes whose status is not VALID (status 3 = not available).

- [ ] **Step 1:** Failing tests: `parsesSocRangeAndLock` (soc 67, rangeelectric 38, doorlockstatusvehicle 2 → 67, 38, LOCKED); `partialUpdateKeepsPrevious` (second update only changes doorlockstatusvehicle 0 → soc still 67, lock UNLOCKED); `endOfChargeTodayLaterUsesToday` (now Wed 22:00 Lisbon, endofchargetime 1410, weekday 2 → Wed 23:30); `endOfChargeEarlierMeansTomorrowWhenNoWeekday` (now 22:00, value 90, no weekday → Thu 01:30); `chargingStatusMapping` (13 → CHARGING, 1 → PLUGGED_NOT_CHARGING, 3 → UNPLUGGED, 16 → UNKNOWN); `notChargingHasNoEndOfCharge` (chargingstatus 3 → `endOfCharge == null` even if an old endofchargetime is present); `invalidStatusIgnored` (soc with status 3 → null).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement `merge`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: interpretar estado do carro`.

### Task 3: Commands and WebSocket client

**Files:**
- Create: `api/Commands.kt`, `api/MbSocket.kt`
- Test: `api/CommandsTest.kt`, `api/MbSocketTest.kt`

**Interfaces:**
- Consumes: `MbAuth.validAccessToken()`, `CarStateParser.merge`, proto classes.
- Produces:
  - `object Commands { fun lock(vin: String, requestId: String): ClientMessage; fun unlock(vin: String, pin: String, requestId: String): ClientMessage; fun climateStart(vin: String, requestId: String): ClientMessage; fun climateStop(vin: String, requestId: String): ClientMessage }` (climate = `ZEVPreconditioningStart/Stop` type `now`, as reference `preheat_start_immediate` / `preheat_stop`)
  - `sealed class CommandResult { object Success; data class Failed(val reason: String); object Timeout }`
  - `class MbSocket(http: OkHttpClient, auth: MbAuth, scope: CoroutineScope, url: String = MbUrls.EMEA.websocket)` with `val states: StateFlow<Map<String, CarState>>`, `val errors: SharedFlow<SocketError>`, `fun connect()`, `fun disconnect()`, `suspend fun send(cmd: ClientMessage, timeoutMs: Long = 60_000): CommandResult`; `sealed class SocketError { object Blocked; object AuthExpired; data class Network(val msg: String) }`

Behaviour from reference `websocket.py`: headers as in `_websocket_connection_headers` (lines 559-581); each `PushMessage` is acknowledged with the ack message the reference builds in `client.py` `_on_data_received`; `vepUpdates` → `CarStateParser.merge`; `apptwinCommandStatusUpdatesByVin` → resolves the pending `send` by `request_id` (FINISHED → Success, FAILED → Failed with first error text). Handshake 429 → emit `Blocked`, no reconnect. Other drops: reconnect with 10 s, 20 s, 40 s… max 120 s, only while connected-on-purpose.

- [ ] **Step 1:** Failing tests: `CommandsTest.unlockCarriesPin` (pin "1234" in `doorsUnlock.pin`), `CommandsTest.climateStartIsNow`; `MbSocketTest` (MockWebServer WebSocket): `sendsAuthHeaders`, `vepUpdateUpdatesState`, `pushMessagesAreAcknowledged`, `commandFinishedReturnsSuccess`, `commandFailedReturnsFailed`, `commandWithoutStatusTimesOut` (timeoutMs 200 → Timeout), `handshake429EmitsBlockedAndDoesNotReconnect` (server sees exactly 1 connection).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement with OkHttp `WebSocketListener`; pending commands in `ConcurrentHashMap<String, CompletableDeferred<CommandResult>>`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: ligação ao carro e comandos`.

### Task 4: Repository and ViewModel

**Files:**
- Create: `data/CarRepository.kt`, `ui/CarViewModel.kt`, `ui/Messages.kt`
- Test: `ui/CarViewModelTest.kt`

**Interfaces:**
- Consumes: `MbAuth`, `MbSocket`, `TokenStore`, `Commands`.
- Produces:
  - `interface CarRepository { val state: StateFlow<CarState?>; val errors: Flow<String>; suspend fun login(email: String, password: String); fun isLoggedIn(): Boolean; fun refresh(); suspend fun lock(): CommandResult; suspend fun unlock(pin: String): CommandResult; suspend fun setClimate(on: Boolean): CommandResult; fun logout() }` — uses the first VIN on the account.
  - `data class UiState(val loggedIn: Boolean, val car: CarState?, val busy: Set<Action>, val message: String?)`, `enum class Action { LOCK, UNLOCK, CLIMATE, REFRESH, LOGIN }`
  - `class CarViewModel(repo: CarRepository) : ViewModel` with `val ui: StateFlow<UiState>` and `fun login(e,p)`, `fun refresh()`, `fun lock()`, `fun unlock(pin)`, `fun toggleClimate()`, `fun messageShown()`
  - `object Messages` with the exact PT strings: `BLOCKED = "A Mercedes bloqueou temporariamente os pedidos. Tenta daqui a uns minutos."`, `TIMEOUT = "O carro não respondeu."`, `UNLOCK_FAILED = "PIN incorreto ou comando recusado."`, `WRONG_LOGIN = "Email ou palavra-passe errados."`, `NO_NETWORK = "Sem ligação à internet."`, `LOCKED_OK = "Carro trancado."`, `UNLOCKED_OK = "Carro destrancado."`

- [ ] **Step 1:** Failing tests with a `FakeCarRepository`: `lockSuccessShowsMessageAndClearsBusy`, `unlockFailedShowsUnlockFailed`, `timeoutShowsTimeout`, `blockedErrorShowsBlocked`, `buttonsDisabledWhileBusy` (second `lock()` while busy is ignored).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement `MbCarRepository` (real) and `CarViewModel`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat: lógica do ecrã`.

### Task 5: Compose UI

**Files:**
- Create: `ui/LoginScreen.kt`, `ui/CarScreen.kt`, `ui/cards/BatteryCard.kt`, `ui/cards/DoorsCard.kt`, `ui/cards/ClimateCard.kt`, `ui/cards/LocationCard.kt`, `ui/UnlockDialog.kt`, `ui/Format.kt`
- Modify: `MainActivity.kt`
- Test: `ui/FormatTest.kt`

**Interfaces:**
- Consumes: `CarViewModel.ui` and its actions.
- Produces: `object Format { fun remaining(end: ZonedDateTime?, now: ZonedDateTime): String; fun updatedAgo(at: Instant, now: Instant): String }`

Screens: Login (email, password, "Entrar"). Car screen, dark theme, pull-to-refresh, cards in order: **Bateria** (big "67 %", "38 km elétricos", line from `Format.remaining`: "Termina às 23:30 · faltam 1 h 30 min" / "Não está a carregar"); **Portas** (state + "Trancar"/"Destrancar"; Destrancar opens `UnlockDialog`: "Destrancar o carro?", numeric PIN field, "Destrancar"/"Cancelar"); **Clima** (switch "Pré-climatização"); **Localização** (osmdroid map with marker, button "Navegar até lá" → `geo:lat,lon?q=lat,lon` intent). Footer "Atualizado há X min". Snackbar for `message`. Menu: "Terminar sessão".

- [ ] **Step 1:** Failing tests: `remainingWhenCharging` (end 23:30, now 22:00 → "Termina às 23:30 · faltam 1 h 30 min"); `remainingUnderOneHour` (→ "faltam 25 min"); `remainingNullOrPast` (null or end before now → "Não está a carregar"); `updatedAgoMinutes` (5 min → "Atualizado há 5 min"; <1 min → "Atualizado agora").
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement `Format` and the screens.
- [ ] **Step 4:** Run tests + `assembleDebug` in CI → PASS, APK artifact.
- [ ] **Step 5:** Commit `feat: interface da app`.

### Task 6: Hand-off

- [ ] **Step 1:** `README.md` in Portuguese: how to download the APK from GitHub Actions, allow installation from unknown sources, create the dedicated Mercedes account and share the car with it, log in once in the official app with that account.
- [ ] **Step 2:** Final CI run green; send the APK (or the link to the artifact) to Sergio.
- [ ] **Step 3:** Commit `docs: instruções de instalação`.
