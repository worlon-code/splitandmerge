# Components

> Reusable Compose composables. Lives in `ui/components/`.
> Wide reuse means: at least three screens consume it.
> One-off composables stay in their owning screen package.

## 1. Inventory

| Component | File | Used by |
|---|---|---|
| `JobRow` | `ui/components/JobRow.kt` | S2, S14 |
| `StatTile` | `ui/components/StatTile.kt` | S5, S7, S8, S11, S13 |
| `StatusChip` | `ui/components/StatusChip.kt` | S2, S14, S7 |
| `CodecBadge` | `ui/components/CodecBadge.kt` | S2 (job thumbnail), S4 (chip strip), S10 (per-part chips) |
| `MetaRow` | `ui/components/MetaRow.kt` | S4 cards, S13 streams table |
| `LanguageAvatar` | `ui/components/LanguageAvatar.kt` | S4 audio + sub rows |
| `IndexCircle` | `ui/components/IndexCircle.kt` | S7 + S8 + S10 part lists |
| `SectionHeader` | `ui/components/SectionHeader.kt` | S2, S15, S15a, S16 |
| `SectionedList` | `ui/components/SectionedList.kt` | S15 |
| `BottomNavBar` | `ui/components/BottomNavBar.kt` | All primary phone screens |
| `NavRail` | `ui/components/NavRail.kt` | All primary tablet screens |
| `StickyActionBar` | `ui/components/StickyActionBar.kt` | S5, S6, S7, S8, S10, S11, S13, S15a |
| `ProbeCard` | `ui/components/ProbeCard.kt` | S4, S14 detail |
| `EmptyState` | `ui/components/EmptyState.kt` | S2 empty, S14 empty |
| `LoadingState` | `ui/components/LoadingState.kt` | every screen on initial load |
| `ErrorState` | `ui/components/ErrorState.kt` | every screen on terminal error |
| `Snackbars / events` | `ui/components/Snackbars.kt` | global host in `MainActivity` |
| `CleanupPreviewSheet` (D1) | `ui/dialogs/CleanupPreviewSheet.kt` | between S5 → S6 |
| `FolderCollisionSheet` (D2) | `ui/dialogs/FolderCollisionSheet.kt` | between S5 → S6 |
| `ContainerPromotionSheet` (D3) | `ui/dialogs/ContainerPromotionSheet.kt` | from S5 |
| `JobDetailSheet` | `ui/components/JobDetailSheet.kt` | S2, S14 |

## 2. Component contracts

Each section gives the API plus a usage example. Keep these stable —
agents add screens by composing existing components. New components go here
only when a third screen actually needs them.

### 2.1 `JobRow`

```kotlin
@Composable
fun JobRow(
    item: JobUi,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
)

data class JobUi(
    val id: String,
    val title: String,
    val type: JobUi.Type,                       // SPLIT | MERGE
    val status: JobStatus,                      // QUEUED, RUNNING, …
    val sizeBytesTotal: Long?,
    val partsTotal: Int?,
    val progressPct: Int,                       // 0..100
    val speedBytesPerSec: Long?,                // null when not RUNNING
    val updatedAt: Long,                        // epoch millis
    val codecBadge: String,                     // "HEVC", "H.264", "AV1"
)
```

Visuals: 56 dp leading codec badge, 1-line title (titleMedium ellipsis),
1-line status chip + size + relative time, trailing more icon. RUNNING rows
add an inline 4 dp `LinearProgressIndicator` under the meta line.

### 2.2 `StatTile`

```kotlin
@Composable
fun StatTile(
    label: String,                              // "Speed"
    value: String,                              // "82 MB/s"
    icon: ImageVector,                          // Icons.Rounded.Speed
    modifier: Modifier = Modifier,
)
```

OutlinedCard, ~100 dp tall, value displayed in `bodyLarge` weight 500.

### 2.3 `StatusChip`

```kotlin
@Composable
fun StatusChip(
    status: JobStatus,
    modifier: Modifier = Modifier,
)
```

Renders a small filled chip:

| Status | Background | Icon |
|---|---|---|
| QUEUED | `surface-variant` | `schedule` |
| RUNNING | `primary-container` | `sync` |
| DONE | `success-container` | `check_circle` |
| FAILED | `error-container` | `error` |
| CANCELLED | `surface` outlined | `close` |

Always include both colour AND icon (accessibility requirement from
[STITCH-BRIEF.md](../STITCH-BRIEF.md)).

### 2.4 `CodecBadge`

```kotlin
@Composable
fun CodecBadge(
    codec: String,                              // "HEVC", "H.264", "AV1"
    sizeDp: Dp = 56.dp,
    modifier: Modifier = Modifier,
)
```

A tonal rounded square with the codec abbreviation in monospace, centred.

### 2.5 `MetaRow`

```kotlin
@Composable
fun MetaRow(
    label: String,                              // "Resolution"
    value: String,                              // "3840 × 2160"
    modifier: Modifier = Modifier,
    valueIsMonospace: Boolean = true,
)
```

`Row` with label left (labelMedium onSurfaceVariant) and value right
(monospace bodyMedium). Used inside cards on S4 and S13.

### 2.6 `LanguageAvatar`

```kotlin
@Composable
fun LanguageAvatar(
    code: String,                               // "TE", "HI", "EN"
    modifier: Modifier = Modifier,
)
```

32 dp tonal circle with 2-letter code centred. Falls back to `??` on
unknown.

### 2.7 `IndexCircle`

```kotlin
@Composable
fun IndexCircle(
    index: Int,                                 // 1, 2, 3
    sizeDp: Dp = 32.dp,
    modifier: Modifier = Modifier,
)
```

Tonal circle with 1- or 2-digit number in monospace.

### 2.8 `SectionHeader`

```kotlin
@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

Title left (titleMedium weight 500), optional text-link right.

### 2.9 `BottomNavBar`

```kotlin
@Composable
fun BottomNavBar(
    current: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
)

enum class NavDestination(
    val icon: ImageVector,
    val label: String,
    val route: String,
) {
    Split(Icons.Rounded.ContentCut, "Split", "nav/split"),
    Merge(Icons.Rounded.MergeType, "Merge", "nav/merge"),
    Queue(Icons.Rounded.ListAlt,   "Queue", "nav/queue"),
    Settings(Icons.Rounded.Settings,"Settings","nav/settings"),
}
```

Material 3 `NavigationBar` with the violet secondary pill-shape selected
indicator (matches Stitch Round-1 designs).

### 2.10 `NavRail`

Same destinations as `BottomNavBar`, rendered as a Material 3
`NavigationRail` 80 dp wide for tablets.

### 2.11 `StickyActionBar`

```kotlin
@Composable
fun StickyActionBar(
    primary: ButtonSpec,
    secondary: ButtonSpec? = null,
    modifier: Modifier = Modifier,
)

data class ButtonSpec(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)
```

80 dp tall, top divider, primary right-aligned (filled), secondary left
(outlined). Sits **above** the bottom nav, separated by a 1 dp
`outlineVariant` divider per the bottom-nav rule.

### 2.12 `ProbeCard`

```kotlin
@Composable
fun ProbeCard(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    body: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
)
```

ElevatedCard with header row (icon + title + chevron) and a collapsible
body. Used for the four cards on S4.

### 2.13 `EmptyState`

```kotlin
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    primary: ButtonSpec? = null,
    modifier: Modifier = Modifier,
)
```

Centred 48 dp icon + headlineSmall + bodyMedium + optional primary button.

### 2.14 `LoadingState`

```kotlin
@Composable
fun LoadingState(
    label: String? = null,
    modifier: Modifier = Modifier,
)
```

Centred `CircularProgressIndicator` 56 dp + optional bodyMedium label below.

### 2.15 `ErrorState`

```kotlin
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    technicalDetails: String? = null,
    modifier: Modifier = Modifier,
)
```

Centred `error` icon + title + message + retry button + expandable
"Technical details" with monospace stderr tail.

### 2.16 Bottom-sheet dialogs (D1 / D2 / D3)

All built on Material 3 `ModalBottomSheet`:

```kotlin
@Composable
fun ModalSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
)
```

D1 `CleanupPreviewSheet` props:
```kotlin
data class CleanupPreviewState(
    val original: String,
    val cleanedTitle: String,
    val subfolder: String,
    val partName: String,
    val onEditTitle: () -> Unit,
    val onCancel: () -> Unit,
    val onUseAsIs: () -> Unit,
)
```

D2 `FolderCollisionSheet` props:
```kotlin
data class FolderCollisionState(
    val existingPath: String,
    val onUseExisting: () -> Unit,
    val onAddSuffix: () -> Unit,
    val onCancel: () -> Unit,
)
```

D3 `ContainerPromotionSheet` props:
```kotlin
data class ContainerPromotionState(
    val originalContainer: String,                  // ".mp4"
    val outputContainer: String,                    // ".mkv"
    val onContinueOriginal: () -> Unit,             // drops bitmap subs
    val onAcceptMkv: () -> Unit,
)
```

`JobDetailSheet` props:
```kotlin
@Composable
fun JobDetailSheet(
    job: Job,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
)
```

## 3. Theme tokens

Components consume tokens from `theme/Color.kt` (Stitch Kinetic Logic
palette). They **never** hard-code hex values. The agent must extend
`Color.kt` if a new role is needed; never inline.

```kotlin
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val onInfo: Color,
)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(...) }
```

## 4. Previews

Every component **must** have at least one `@Preview` showing its happy
path. Components with state variation (e.g. `StatusChip`) have a single
preview function rendering all variants in a column.

```kotlin
@Preview(showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun StatusChipPreview() {
    VideoSplitterTheme(darkTheme = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            StatusChip(status = JobStatus.QUEUED)
            StatusChip(status = JobStatus.RUNNING)
            StatusChip(status = JobStatus.DONE)
            StatusChip(status = JobStatus.FAILED)
            StatusChip(status = JobStatus.CANCELLED)
        }
    }
}
```

## 5. Test rules

Compose UI tests for components live under
`app/src/androidTest/.../ui/components/`. Each component test asserts:

- The component renders without crashing for every variant.
- Tap targets are ≥ 48 dp (use `MinimumTouchTargetSize` rule).
- Long content ellipsises rather than wraps when the spec says single-line.
- Screen-readable: every interactive element has a `contentDescription`.

## 6. Adding a new component

Process:

1. Verify the candidate is reused by ≥ 3 screens. If not, keep it local.
2. Add the file under `ui/components/`.
3. Add an entry to §1 of this document.
4. Add a `@Preview` and a Compose UI test.
5. If the component renders text, add the strings to `res/values/strings.xml`
   and pass them in via parameters (no inlined string literals).

## 7. Anti-patterns

- ❌ Components that read from a ViewModel directly. They take state via
  parameters and emit events via callbacks.
- ❌ Components that own their own coroutine scope. Side effects belong to
  screens.
- ❌ Components with mutable hoisted state (`MutableState<T>` in/out). Use
  immutable state + callbacks.
- ❌ Components depending on a specific screen's `UiState`. They take
  smaller, screen-agnostic data classes.
