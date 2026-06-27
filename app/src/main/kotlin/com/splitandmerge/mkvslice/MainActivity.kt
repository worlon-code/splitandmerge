package com.splitandmerge.mkvslice

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.ThemeMode
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.onboarding.FirstRunChecker
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.theme.VideoSplitterTheme
import com.splitandmerge.mkvslice.ui.components.FirstRunDialog
import com.splitandmerge.mkvslice.ui.components.FolderValidationDialog
import com.splitandmerge.mkvslice.ui.nav.AppNav
import com.splitandmerge.mkvslice.ui.nav.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            timber.log.Timber.d("POST_NOTIFICATIONS permission granted")
        } else {
            timber.log.Timber.d("POST_NOTIFICATIONS permission denied")
        }
    }

    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var outputFolderValidator: OutputFolderValidator
    @Inject lateinit var firstRunChecker: FirstRunChecker
    @Inject lateinit var jobDao: JobDao

    // Pre-read result of the first-run DataStore check. Null means the read
    // hasn't completed yet; the splash screen is held open while it is null.
    // Using mutableStateOf so that the setContent closure recomposes as soon
    // as the value arrives, picking up the correct `initial` for collectAsState
    // before the splash dismisses and the first frame is shown to the user.
    private var preReadFirstRun by androidx.compose.runtime.mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        // Hold the splash until BOTH conditions are met:
        //   1. App-level startup recovery is complete (startupReadyDeferred).
        //   2. The first-run DataStore pre-read has resolved (preReadFirstRun != null).
        // This guarantees the first visible frame always has the correct state.
        val app = application as App
        splashScreen.setKeepOnScreenCondition {
            !app.startupReadyDeferred.isCompleted || preReadFirstRun == null
        }

        super.onCreate(savedInstanceState)

        // Pre-read DataStore on a lifecycle-aware coroutine. Keeps the splash
        // open via setKeepOnScreenCondition until this resolves. lifecycleScope
        // is cancelled automatically if the activity is destroyed before the
        // read completes (e.g. immediate back-press during splash).
        lifecycleScope.launch {
            preReadFirstRun = firstRunChecker.isFirstRun()
        }

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsState(
                initial = com.splitandmerge.mkvslice.data.settings.SettingsState()
            )
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
            }

            // Keep the screen on when the user has opted in AND a job is running.
            val anyJobRunning by remember {
                jobDao.observeAll().map { list ->
                    list.any { it.status == JobStatus.RUNNING }
                }
            }.collectAsState(initial = false)

            val view = LocalView.current
            DisposableEffect(settings.keepScreenOn, anyJobRunning) {
                val window = (view.context as android.app.Activity).window
                if (settings.keepScreenOn && anyJobRunning) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            VideoSplitterTheme(darkTheme = darkTheme) {
                // Track validation failures from the first-run folder picker.
                var firstRunValidationError by remember { mutableStateOf<OutputFolderValidation?>(null) }

                // preReadFirstRun is set by the lifecycleScope.launch above before the splash
                // dismisses (setKeepOnScreenCondition holds until non-null). So this `initial`
                // is always the real DataStore truth for the first visible frame. The ?: false
                // fallback is unreachable in normal operation but satisfies the non-null contract.
                val initialFirstRun = preReadFirstRun ?: false
                val firstRun by firstRunChecker.isFirstRunFlow.collectAsState(initial = initialFirstRun)

                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // The first-run experience is now handled by FirstRunDialog (overlay).
                    // Start directly at Library — the dialog blocks the UI until a valid
                    // folder is set.
                    AppNav(startDestination = Routes.LIBRARY)
                }

                if (firstRun) {
                    FirstRunDialog(onFolderPicked = { uri ->
                        val validation = outputFolderValidator.validate(
                            uriString = uri.toString(),
                            requiredBytes = 1L * 1024L * 1024L * 1024L,
                            assumePermissionPersisted = false
                        )
                        if (validation is OutputFolderValidation.Ok) {
                            try {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                )
                            } catch (_: Exception) {
                                // Permission take failed; folder validation already confirmed
                                // writability, so proceed anyway.
                            }
                            lifecycleScope.launch {
                                settingsRepository.setDefaultOutputFolderUri(uri.toString())
                                // DataStore emits → isFirstRunFlow emits false → dialog dismisses.
                            }
                        } else {
                            // Show the reusable validation error dialog; FirstRunDialog stays
                            // open underneath until the user picks again.
                            firstRunValidationError = validation
                        }
                    })

                    // Reuse the same FolderValidationDialog from the split/merge screens.
                    FolderValidationDialog(
                        validation = firstRunValidationError,
                        onPickAgain = { firstRunValidationError = null },
                        onDismiss = { firstRunValidationError = null }
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
