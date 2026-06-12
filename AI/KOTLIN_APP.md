# Kotlin / Compose Conventions

> The "house style" for this codebase. Mirrors Google Android Architecture
> Samples + Compose 1.7+ best practices. Disagreement with the local
> AndroidStudio template is intentional.

## 1. Toolchain

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 | Android Studio bundled is fine. |
| Kotlin | 2.x | Compose plugin must match. |
| AGP | 8.x stable | Always Gradle wrapper. |
| Gradle | 8.x | Wrapper checked into repo. |
| KSP | latest | Used for Hilt + Room. **Never** kapt. |
| compileSdk | 35 | Android 15. |
| targetSdk | 35 | Same. |
| minSdk | 26 | Android 8 floor. |
| NDK | pinned in `app/build.gradle.kts` `ndkVersion = "..."` | needed for FFmpeg `.so`. |

## 2. Naming

- **Packages**: `com.splitandmerge.mkvslice.<layer>.<feature>` lower case.
- **Composable functions**: PascalCase, like classes. `LibraryScreen()`,
  `JobRow()`.
- **Composable parameters**: `modifier: Modifier = Modifier` is **always
  first non-state param**. State first, modifier second per Google's rule.
- **ViewModels**: `<Screen>ViewModel`. Hilt-injectable with
  `@HiltViewModel`.
- **Use cases / interactors**: noun-verb, e.g. `RunSplit`, `BuildManifest`.
  Avoid `*UseCase` suffix.
- **Tests**: `<ClassUnderTest>Test`. Method names: backticked sentences.
- **Files**: one top-level class / object per file when feasible.

## 3. Compose patterns

### State hoisting

A composable receives state, emits events. Never owns its own state if it
matters beyond the screen.

```kotlin
@Composable
fun SplitConfigScreen(
    state: SplitConfigUiState,
    onIntent: (SplitConfigIntent) -> Unit,
    modifier: Modifier = Modifier,
)
```

### Stable types

`@Stable` / `@Immutable` on data classes used as Compose params. Avoid passing
mutable lists (`mutableListOf<>`) directly — wrap in `ImmutableList` from
`kotlinx.collections.immutable`.

### Side effects

| Effect | When |
|---|---|
| `LaunchedEffect(key)` | One-shot per key change (e.g. job start). |
| `DisposableEffect(key)` | Listener lifecycle. |
| `rememberCoroutineScope()` | UI-driven coroutines (button taps). |
| `produceState`, `derivedStateOf` | Computed UI state. |

Never call suspend functions directly in `@Composable`; always go through one
of the above or via the ViewModel.

### Lists

`LazyColumn` for any list that may exceed ~12 items.
`Column` is fine for fixed lists (≤ 12).

### Theme

```kotlin
@Composable
fun VideoSplitterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= 31,
    content: @Composable () -> Unit
) { /* ... */ }
```

The Stitch design tokens live in `theme/Color.kt` (light + dark + amoled
overrides). See [STITCH-BRIEF.md](../STITCH-BRIEF.md) §1 for the seed.

## 4. State management

`StateFlow<UiState>` exposed by ViewModels. UI collects via
`collectAsStateWithLifecycle()`.

```kotlin
@HiltViewModel
class SplitConfigViewModel @Inject constructor(
    private val probeRepo: ProbeRepository,
    private val planner: CutPlanner,
) : ViewModel() {

    private val _ui = MutableStateFlow<SplitConfigUiState>(SplitConfigUiState.Loading)
    val ui: StateFlow<SplitConfigUiState> = _ui.asStateFlow()

    fun onIntent(intent: SplitConfigIntent) { /* ... */ }
}
```

`UiState` is sealed:

```kotlin
sealed interface SplitConfigUiState {
    data object Loading : SplitConfigUiState
    data class Ready(
        val title: String,
        val mode: SplitMode,
        val parts: Int,
        val capBytes: Long,
        val partsEstimate: Int,
        val avgPartBytes: Long,
        val totalEtaSeconds: Long,
        val outputDir: String,
        val canContinue: Boolean,
    ) : SplitConfigUiState
    data class Error(val msg: String) : SplitConfigUiState
}
```

Intents are sealed too:

```kotlin
sealed interface SplitConfigIntent {
    data class TitleChanged(val value: String) : SplitConfigIntent
    data class ModeSelected(val mode: SplitMode) : SplitConfigIntent
    data class PartsChanged(val n: Int) : SplitConfigIntent
    data class CapChanged(val bytes: Long) : SplitConfigIntent
    data class OutputDirChanged(val uri: String) : SplitConfigIntent
    data object Continue : SplitConfigIntent
    data object Cancel : SplitConfigIntent
}
```

## 5. Dependency injection (Hilt)

- `@HiltAndroidApp` on `App`.
- `@AndroidEntryPoint` on `MainActivity` and `JobService`.
- Modules under `di/`. Each `@InstallIn(SingletonComponent::class)` unless
  scoped tighter.
- Test override: `@HiltAndroidTest` + `HiltTestRunner` (only when needed in
  androidTest; v1 starts without it).

## 6. Persistence (Room)

- Entities + DAOs in `data/db/`. Type converters for `Long` (timestamps),
  `JobStatus` (enum), `Uri` (string).
- One single `@Database` (`AppDatabase`).
- Migrations are required from day one; never destroy and recreate.
- See [DATA_MODELS.md](DATA_MODELS.md) for schemas.

## 7. Coroutines

- Always launch from the right scope: `viewModelScope` (ViewModel),
  service-managed scope (Service), or `LaunchedEffect` (Compose).
- `flowOn(Dispatchers.IO)` only on the producing flow, not at the collector.
- Use `kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000)` for
  hot UI flows.

## 8. Logging

`Timber` only. Never `Log.x()` directly. Tag is auto-derived; we don't ship a
debug `DebugTree` in release (release uses `Timber.plant(ReleaseTree)` that
swallows VERBOSE/DEBUG).

```kotlin
Timber.i("Split job %s started for %s", job.id, job.title)
Timber.e(t, "FFmpeg exited with code %d", exitCode)
```

Never log file content, never log full URIs (truncate display strings).

## 9. Resources

- Strings in `res/values/strings.xml`. No string literals in UI code (except
  compile-time constants like icon names).
- One drawable per asset; vector preferred.
- Icons: Material Symbols Rounded loaded via `androidx.compose.material.icons`
  + `material-icons-extended` for the long tail. The Compose icon enum names
  match the Material Symbols name (e.g. `Icons.Rounded.ContentCut`).

## 10. Comments policy (matches AGENTS.md)

Default: **no comments**. Add one only when the *why* is non-obvious — a
hidden constraint, a subtle invariant, a workaround. Never restate the *what*.
Never reference the current task or fix in code (that's PR description
territory).

## 11. Linting

- Built-in Android Lint runs on every build (`./gradlew lint`).
- No third-party static analyser in v1 (no Detekt, no Ktlint formatter).
- Errors must be 0 before any build (per AGENTS.md §4).
- Warnings are ignored unless the rule is escalated to `error` in
  `app/build.gradle.kts` `lint { ... }`.

## 12. Imports

- Wildcard imports allowed for `kotlinx.coroutines.flow.*`,
  `androidx.compose.runtime.*`, `androidx.compose.material3.*`,
  `androidx.compose.foundation.*`. Otherwise explicit imports.
- Sort order: stdlib → 3rd party → app code (Android Studio default).

## 13. Versioning

- `versionName = "0.0.1"`, `versionCode = 1` to start.
- Version bump only on release, never on debug. Per [AGENTS.md §3A](../AGENTS.md).

## 14. Anti-patterns to avoid

- ❌ Using `LiveData` (we are pure-Compose; `StateFlow` only).
- ❌ Using `View` / XML layouts.
- ❌ Using `kapt` (use KSP).
- ❌ Catching `Throwable` to "be safe". Catch the specific exceptions FFmpeg
  / Room emit; let the rest crash and be logged.
- ❌ Adding helpers / abstractions for one-time operations.
- ❌ Reflection when an annotation processor or sealed class would do.
- ❌ Hand-rolled JSON parsing — use `kotlinx.serialization`.
- ❌ Hand-rolled HTTP — use Retrofit + OkHttp (added in Phase 7 for update).
