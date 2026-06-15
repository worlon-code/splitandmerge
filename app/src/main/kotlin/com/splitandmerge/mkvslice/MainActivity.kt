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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.domain.onboarding.FirstRunChecker
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.theme.VideoSplitterTheme
import com.splitandmerge.mkvslice.ui.components.FirstRunDialog
import com.splitandmerge.mkvslice.ui.components.FolderValidationDialog
import com.splitandmerge.mkvslice.ui.nav.AppNav
import com.splitandmerge.mkvslice.ui.nav.Routes
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VideoSplitterTheme {
                // Track validation failures from the first-run folder picker.
                var firstRunValidationError by remember { mutableStateOf<OutputFolderValidation?>(null) }

                // isFirstRun is true while defaultOutputFolderUri is blank in DataStore.
                // initial = true: on a genuine fresh install DataStore hasn't emitted yet,
                // so we show the dialog immediately and let it auto-dismiss once DataStore
                // emits false (folder is set). Returning users get a single-frame flash at
                // most (~10 ms) because DataStore emits quickly from the cached proto file.
                val firstRun by firstRunChecker.isFirstRunFlow.collectAsState(initial = true)

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
