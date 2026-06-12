# State Management

> StateFlow + sealed UI states + sealed intents. No LiveData, no AAC
> ViewModelKt — just `viewModelScope` + Compose-friendly `StateFlow`s.

## 1. The pattern

For every screen we have:

- A `*UiState` (sealed interface).
- A `*Intent` (sealed interface) for what the user can do.
- A `*ViewModel` exposing `StateFlow<UiState>` and `onIntent(intent: *Intent)`.

The ViewModel never references Compose, Android views, Hilt component, or
Context directly. It depends only on repositories / domain interfaces injected
by Hilt.

## 2. Example — Split Config

```kotlin
sealed interface SplitConfigUiState {
    data object Loading : SplitConfigUiState
    data class Ready(
        val title: String,
        val originalFilename: String,
        val mode: SplitMode,
        val parts: Int,
        val targetCapBytes: Long,
        val ceilingBytes: Long,
        val outputDirUri: String,
        val outputContainer: String,            // ".mkv" / ".mp4"
        val containerLockedToMkv: Boolean,      // true when bitmap subs present
        val partsEstimate: Int,
        val avgPartBytes: Long,
        val totalEtaSeconds: Long,
        val canContinue: Boolean,
    ) : SplitConfigUiState
    data class Error(val message: String) : SplitConfigUiState
}

sealed interface SplitConfigIntent {
    data class TitleChanged(val value: String)         : SplitConfigIntent
    data class ModeSelected(val mode: SplitMode)       : SplitConfigIntent
    data class PartsChanged(val n: Int)                : SplitConfigIntent
    data class CapChanged(val bytes: Long)             : SplitConfigIntent
    data class OutputDirChanged(val uri: String)       : SplitConfigIntent
    data object Continue                                : SplitConfigIntent
    data object Cancel                                  : SplitConfigIntent
}

@HiltViewModel
class SplitConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val probeRepo: ProbeRepository,
    private val planner: CutPlanner,
    private val cleanup: CleanupEngine,
    private val settings: SettingsStore,
) : ViewModel() {

    private val sourceUri: String = checkNotNull(savedStateHandle["sourceUri"])

    private val _ui = MutableStateFlow<SplitConfigUiState>(SplitConfigUiState.Loading)
    val ui: StateFlow<SplitConfigUiState> = _ui.asStateFlow()

    init { reload() }

    fun onIntent(intent: SplitConfigIntent) {
        val current = _ui.value as? SplitConfigUiState.Ready ?: return
        _ui.value = when (intent) {
            is SplitConfigIntent.TitleChanged       -> recompute(current.copy(title = intent.value))
            is SplitConfigIntent.ModeSelected       -> recompute(current.copy(mode = intent.mode))
            is SplitConfigIntent.PartsChanged       -> recompute(current.copy(parts = intent.n))
            is SplitConfigIntent.CapChanged         -> recompute(current.copy(targetCapBytes = intent.bytes))
            is SplitConfigIntent.OutputDirChanged   -> current.copy(outputDirUri = intent.uri)
            SplitConfigIntent.Continue              -> { /* navigation handled by host */ ; current }
            SplitConfigIntent.Cancel                -> current
        }
    }

    private fun recompute(state: SplitConfigUiState.Ready): SplitConfigUiState.Ready {
        val plan = planner.plan(
            mode = state.mode,
            requestedParts = state.parts,
            targetCapBytes = state.targetCapBytes,
            ceilingBytes = state.ceilingBytes,
            durationSeconds = currentProbe.durationSeconds,
            totalSizeBytes = currentProbe.sizeBytes,
            keyframes = currentKeyframes,
        )
        return state.copy(
            partsEstimate = plan.expectedPartCount,
            avgPartBytes  = plan.expectedPartSizes.average().toLong(),
            totalEtaSeconds = (currentProbe.sizeBytes / WRITE_SPEED_BYTES_PER_SEC).coerceAtLeast(60),
            canContinue = plan.cuts.isNotEmpty(),
        )
    }

    private fun reload() = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            currentProbe = probeRepo.probe(sourceUri)
            currentKeyframes = probeRepo.keyframes(sourceUri)
            val cleaned = cleanup.clean(currentProbe.originalFilename)
            val outputDir = settings.defaultOutputDirUri.first()
            val containerLocked = currentProbe.subtitles.any { it.isBitmap }
            val ready = SplitConfigUiState.Ready(
                title = cleaned.title,
                originalFilename = currentProbe.originalFilename,
                mode = SplitMode.BOTH,
                parts = 3,
                targetCapBytes = settings.defaultSizeCap.first(),
                ceilingBytes = settings.defaultCeiling.first(),
                outputDirUri = outputDir,
                outputContainer = if (containerLocked) ".mkv" else ".${currentProbe.containerExtension}",
                containerLockedToMkv = containerLocked,
                partsEstimate = 0,
                avgPartBytes = 0,
                totalEtaSeconds = 0,
                canContinue = false,
            )
            _ui.value = recompute(ready)
        }.onFailure { e ->
            _ui.value = SplitConfigUiState.Error(e.message ?: "Failed to probe file")
        }
    }

    private companion object {
        const val WRITE_SPEED_BYTES_PER_SEC = 80_000_000L  // 80 MB/s heuristic
    }

    private lateinit var currentProbe: ProbeResult
    private lateinit var currentKeyframes: List<Double>
}
```

## 3. Compose collection

```kotlin
@Composable
fun SplitConfigScreen(
    viewModel: SplitConfigViewModel = hiltViewModel(),
    onContinue: (SplitJob) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    SplitConfigContent(
        state = state,
        onIntent = viewModel::onIntent,
        onContinueNav = onContinue,
        onBack = onBack,
    )
}
```

`SplitConfigContent` is a stateless `@Composable` that **only** receives state
+ callbacks. This keeps it preview-friendly and unit-testable.

## 4. Cross-screen state — the running job

A single `JobsRepository` (singleton, Hilt) exposes:

```kotlin
class JobsRepository @Inject constructor(
    private val jobDao: JobDao,
    private val partDao: PartDao,
    private val progressBus: MutableSharedFlow<JobProgress>,
) {
    fun observeAll(): Flow<List<JobUi>> = jobDao.observeAll().map { it.map(::toUi) }
    fun observeProgress(jobId: String): Flow<JobProgress> =
        progressBus.filter { it.jobId == jobId }
    suspend fun enqueue(job: JobEntity)
    suspend fun cancel(jobId: String)
}
```

`JobsViewModel` (Library + Jobs screens) exposes a single
`StateFlow<List<JobUi>>` derived from this repository. `ProgressViewModel`
(Progress screen) subscribes to `observeProgress(currentJobId)`.

## 5. SavedStateHandle usage

Used only for navigation arguments — never for ephemeral UI state.
Compose's `rememberSaveable` is preferred for transient state on a screen.

## 6. One-shot effects (snackbars, navigation)

For "do this once and forget" events, use a `Channel` (capacity = `Channel.BUFFERED`)
exposed as a `Flow` to the screen, which collects in a `LaunchedEffect`.

```kotlin
private val _events = Channel<UiEvent>(Channel.BUFFERED)
val events: Flow<UiEvent> = _events.receiveAsFlow()

fun onIntent(...) {
    viewModelScope.launch { _events.send(UiEvent.NavigateToProgress(jobId)) }
}
```

Compose:
```kotlin
LaunchedEffect(Unit) {
    viewModel.events.collect { e ->
        when (e) {
            is UiEvent.NavigateToProgress -> onContinue(e.jobId)
        }
    }
}
```

## 7. Error handling

Any thrown exception inside a state recompute lands in `Error(message)`.
ViewModels never crash. Engine errors → `EngineError` (see
[ARCHITECTURE.md §8](ARCHITECTURE.md)) → mapped to user-facing strings in the
ViewModel, with a "Show technical details" tap revealing the stderr tail.

## 8. Loading states

Every screen that touches IO has a `Loading` state in its sealed
`UiState`. The screen renders a centred `CircularProgressIndicator`. Avoid
`null`-as-loading or `isLoading: Boolean` flags.

## 9. Testing rules

Every ViewModel under test must:

1. Use `Dispatchers.setMain(StandardTestDispatcher())` in `@Before`.
2. Use Turbine for flow assertions.
3. Assert state transitions, not just final state.
4. Use MockK for repositories / engine interfaces.
5. Be JVM-only (no Robolectric unless the VM transitively touches `android.net.Uri`).

Example in [TESTING-AGENT.md §2](../TESTING-AGENT.md).

## 10. ViewModel inventory (v1)

| Screen | ViewModel | Repository deps |
|---|---|---|
| S1 Onboarding | `OnboardingViewModel` | `SettingsStore` |
| S2 Library | `LibraryViewModel` | `JobsRepository`, `SettingsStore` |
| S4 File Details | `FileDetailsViewModel` | `ProbeRepository` |
| S5 Split Config | `SplitConfigViewModel` | `ProbeRepository`, `CutPlanner`, `CleanupEngine`, `SettingsStore` |
| S6 Confirm | (no VM — derived from S5 state) | — |
| S7 Progress | `JobProgressViewModel` | `JobsRepository` |
| S8 Split Result | `SplitResultViewModel` | `JobsRepository` |
| S10 Merge Order | `MergeOrderViewModel` | `MergeValidator` |
| S11 Merge Config | `MergeConfigViewModel` | `JobsRepository` |
| S13 Merge Result | `MergeResultViewModel` | `JobsRepository` |
| S14 Jobs | (reuses `LibraryViewModel`) | — |
| S15 Settings | `SettingsViewModel` | `SettingsStore`, `UpdateService` |
| S15a Cleanup Patterns | `CleanupPatternsViewModel` | `CleanupRepository`, `CleanupEngine` |
| S16 OSS Notices | `OssNoticesViewModel` | bundled JSON |

## 11. ViewModel + service contract

Long-running operations (split, merge) start the foreground service via an
`Intent`. The ViewModel does **not** wait for completion synchronously. It
subscribes to `JobsRepository.observeProgress(jobId)` and updates UI from
that flow. The service drives the engine and writes progress to the bus.

Cancel: VM calls `JobsRepository.cancel(jobId)`. Repository sends a cancel
intent to the service, which interrupts the running engine job.

## 12. Anti-patterns to reject

- ❌ `LiveData` (use `StateFlow`).
- ❌ `MutableState` exposed by ViewModel (Compose-only state lives in the screen).
- ❌ ViewModel referencing Context, Application, or Activity.
- ❌ Suspend functions called directly from `@Composable` (use ViewModel + intents).
- ❌ Two ViewModels owning overlapping state for the same screen.
- ❌ Shared `var` between ViewModels (route through Repository).
