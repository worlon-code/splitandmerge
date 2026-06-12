package com.splitandmerge.mkvslice.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsScreen
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsScreenTablet
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsViewModel
import com.splitandmerge.mkvslice.ui.dialogs.CleanupPreviewSheet
import com.splitandmerge.mkvslice.ui.dialogs.ContainerPromotionSheet
import com.splitandmerge.mkvslice.ui.dialogs.FolderCollisionSheet
import com.splitandmerge.mkvslice.ui.filedetails.FileDetailsScreen
import com.splitandmerge.mkvslice.ui.filedetails.FileDetailsViewModel
import com.splitandmerge.mkvslice.ui.jobs.JobsScreen
import com.splitandmerge.mkvslice.ui.jobs.JobsScreenTablet
import com.splitandmerge.mkvslice.ui.jobs.JobsViewModel
import com.splitandmerge.mkvslice.ui.library.LibraryScreen
import com.splitandmerge.mkvslice.ui.library.LibraryScreenTablet
import com.splitandmerge.mkvslice.ui.library.LibraryViewModel
import com.splitandmerge.mkvslice.ui.mergeconfig.MergeConfigScreen
import com.splitandmerge.mkvslice.ui.mergeorder.MergeOrderScreen
import com.splitandmerge.mkvslice.ui.mergeorder.MergeOrderViewModel
import com.splitandmerge.mkvslice.ui.onboarding.OnboardingScreen
import com.splitandmerge.mkvslice.ui.oss.OssNoticesScreen
import com.splitandmerge.mkvslice.ui.progress.JobProgressScreen
import com.splitandmerge.mkvslice.ui.progress.JobProgressViewModel
import com.splitandmerge.mkvslice.ui.result.MergeResultScreen
import com.splitandmerge.mkvslice.ui.result.SplitResultScreen
import com.splitandmerge.mkvslice.ui.settings.SettingsScreen
import com.splitandmerge.mkvslice.ui.settings.SettingsViewModel
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigScreen
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigScreenTablet
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigViewModel
import com.splitandmerge.mkvslice.ui.splitconfirm.SplitConfirmScreen
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class ScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun saveScreenshot(name: String) {
        // Wait for Compose idle before capturing
        composeTestRule.waitForIdle()
        
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.getExternalFilesDir("screenshots") ?: return
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$name.png")
        
        val nodeToCapture = try {
            val roots = composeTestRule.onAllNodes(isRoot())
            val count = roots.fetchSemanticsNodes().size
            if (count > 1) {
                roots[count - 1]
            } else {
                composeTestRule.onRoot()
            }
        } catch (e: Throwable) {
            composeTestRule.onRoot()
        }
        
        val imageBitmap = nodeToCapture.captureToImage()
        val bitmap = imageBitmap.asAndroidBitmap()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun captureOnboardingScreen() {
        composeTestRule.setContent {
            OnboardingScreen(onFinished = {})
        }
        saveScreenshot("S1_onboarding")
    }

    @Test
    fun captureLibraryScreenPhone() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                LibraryScreen(
                    viewModel = LibraryViewModel(),
                    onNavigateToSettings = {},
                    onStartSplitFlow = {},
                    onStartMergeFlow = {},
                    onNavigateToJobDetail = {}
                )
            }
        }
        saveScreenshot("S2_library_phone")
    }

    @Test
    fun captureLibraryScreenTablet() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 800 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                LibraryScreenTablet(
                    viewModel = LibraryViewModel(),
                    onNavigateToSettings = {},
                    onStartSplitFlow = {},
                    onStartMergeFlow = {},
                    onNavigateToJobDetail = {}
                )
            }
        }
        saveScreenshot("S2_library_tablet")
    }

    @Test
    fun captureFileDetailsScreen() {
        composeTestRule.setContent {
            FileDetailsScreen(
                viewModel = FileDetailsViewModel(),
                onBack = {},
                onContinue = {}
            )
        }
        saveScreenshot("S4_file_details")
    }

    @Test
    fun captureSplitConfigScreenPhone() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                SplitConfigScreen(
                    viewModel = SplitConfigViewModel(),
                    onBack = {},
                    onConfirm = {}
                )
            }
        }
        saveScreenshot("S5_split_config_phone")
    }

    @Test
    fun captureSplitConfigScreenTablet() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 800 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                SplitConfigScreenTablet(
                    viewModel = SplitConfigViewModel(),
                    onBack = {},
                    onConfirm = {}
                )
            }
        }
        saveScreenshot("S5_split_config_tablet")
    }

    @Test
    fun captureSplitConfirmScreen() {
        composeTestRule.setContent {
            SplitConfirmScreen(
                viewModel = SplitConfigViewModel(),
                onBack = {},
                onConfirm = {}
            )
        }
        saveScreenshot("S6_split_confirm")
    }

    @Test
    fun captureJobProgressScreen() {
        composeTestRule.setContent {
            JobProgressScreen(
                viewModel = JobProgressViewModel(),
                jobId = "2",
                onNavigateToResult = {},
                onCancelOrBack = {}
            )
        }
        saveScreenshot("S7_S12_job_progress")
    }

    @Test
    fun captureSplitResultScreen() {
        composeTestRule.setContent {
            SplitResultScreen(
                jobId = "2",
                onMergePartsShortcut = {},
                onNavigateHome = {}
            )
        }
        saveScreenshot("S8_split_result")
    }

    @Test
    fun captureMergeOrderScreen() {
        composeTestRule.setContent {
            MergeOrderScreen(
                viewModel = MergeOrderViewModel(),
                onBack = {},
                onContinue = {}
            )
        }
        saveScreenshot("S10_merge_order")
    }

    @Test
    fun captureMergeConfigScreen() {
        composeTestRule.setContent {
            MergeConfigScreen(
                onBack = {},
                onConfirm = {}
            )
        }
        saveScreenshot("S11_merge_config")
    }

    @Test
    fun captureMergeResultScreen() {
        composeTestRule.setContent {
            MergeResultScreen(
                jobId = "2",
                onNavigateHome = {}
            )
        }
        saveScreenshot("S13_merge_result")
    }

    @Test
    fun captureJobsScreenPhone() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                JobsScreen(
                    viewModel = JobsViewModel(),
                    onBack = {},
                    onNavigateToJobDetail = {}
                )
            }
        }
        saveScreenshot("S14_jobs_history_phone")
    }

    @Test
    fun captureJobsScreenTablet() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 800 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                JobsScreenTablet(
                    viewModel = JobsViewModel(),
                    onBack = {},
                    onNavigateToJobDetail = {}
                )
            }
        }
        saveScreenshot("S14_jobs_history_tablet")
    }

    @Test
    fun captureSettingsScreen() {
        composeTestRule.setContent {
            SettingsScreen(
                viewModel = SettingsViewModel(),
                onBack = {},
                onNavigateToCleanupPatterns = {},
                onNavigateToOssNotices = {}
            )
        }
        saveScreenshot("S15_settings")
    }

    @Test
    fun captureCleanupPatternsScreenPhone() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                CleanupPatternsScreen(
                    viewModel = CleanupPatternsViewModel(),
                    onBack = {}
                )
            }
        }
        saveScreenshot("S15a_cleanup_patterns_phone")
    }

    @Test
    fun captureCleanupPatternsScreenTablet() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 800 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                CleanupPatternsScreenTablet(
                    viewModel = CleanupPatternsViewModel(),
                    onBack = {}
                )
            }
        }
        saveScreenshot("S15a_cleanup_patterns_tablet")
    }

    @Test
    fun captureOssNoticesScreen() {
        composeTestRule.setContent {
            OssNoticesScreen(onBack = {})
        }
        saveScreenshot("S16_oss_notices")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun captureCleanupPreviewSheet() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                CleanupPreviewSheet(
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    onDismiss = {},
                    onApply = {}
                )
            }
        }
        saveScreenshot("D1_cleanup_preview_sheet")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun captureFolderCollisionSheet() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                FolderCollisionSheet(
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    folderPath = "/storage/emulated/0/Movies",
                    onDismiss = {},
                    onOverwrite = {},
                    onSaveToSubfolder = {}
                )
            }
        }
        saveScreenshot("D2_folder_collision_sheet")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun captureContainerPromotionSheet() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ContainerPromotionSheet(
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    onDismiss = {},
                    onPromote = {},
                    onKeepRaw = {}
                )
            }
        }
        saveScreenshot("D3_container_promotion_sheet")
    }
}
