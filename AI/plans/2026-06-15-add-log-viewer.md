# LogViewerScreen Implementation Plan (Step 7) — Updated

Expose diagnostic logs stored at `<cacheDir>/logs/app-*.log` to users in-app.

## Problem
Currently, diagnostic logs are generated on-device, but users have no in-app way to view or share them when troubleshooting issues. Exposing these logs in a secure, readable way with two-pane support on tablet devices will greatly improve usability and debuggability.

## Proposed Changes

### Routes
* **Routes.kt**: Add `const val LOGS = "logs"` route.
* **AppNav.kt**: Add route destination rendering `LogViewerScreen` with `hiltViewModel()`.

### Settings Screen
* **SettingsScreen.kt**: Add a row under battery-optimization to navigate to `Routes.LOGS`.
  * Title: "View diagnostic logs"
  * Subtitle: "Stored locally for 7 days; never sent automatically"
  * Icon: `Icons.Default.BugReport`

### Hilt & Lifecycle Integration
* Keep `App.onCreate` log planting unchanged.
* `LogViewerViewModel` will not inject `FileLoggingTree` or `LogPurger`.
* `FileLoggingTree` exposes static companion methods: `FileLoggingTree.flushIfPlanted()` and `FileLoggingTree.currentFileName()`.

### ViewModel & State
* **LogViewerViewModel.kt**:
  * Exposes `StateFlow<LogViewerState>` and `SharedFlow<Intent>` for share chooser sheet.
  * Implements `refresh()`, `selectFile(name)`, `loadFull()`, `share(name)`, and `clearAllLogs()`.
  * Correctly flushes the tree via `FileLoggingTree.flushIfPlanted()` before reading logs.
  * Implements size limit truncation (> 1MB loads only the last 500KB and sets `truncated = true`).
  * Splits content into line data objects on `Dispatchers.IO` before updating state to prevent main thread lag.

### UI (Compose)
* **LogViewerScreen.kt**:
  * TopAppBar with share action, overflow menu to clear logs, and back navigation.
  * Single pane on phone (list view or detail view), two-pane layout on tablets (`screenWidthDp >= 600`).
  * Monospace log lines text with line numbers on the left margin, wrapped in a `SelectionContainer`.
  * Auto-scrolls to bottom on load.
  * Truncated header/banner displayed at the top of the content pane.
  * Pinned footer explaining storage retention (7 days) and local storage disclosure.
  * Small fade-bottom gradient on content pane.

## Tests
* **LogViewerViewModelTest.kt**:
  * Unit tests validating list sorting, log truncation on files > 1MB, `loadFull()` clearing truncation, and `clearAllLogs` removing only matching logs while truncating active log.
