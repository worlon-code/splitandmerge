═══════════════════════════════════════════════════════
TESTING AGENT — VIDEO SPLITTER
Sub-agent rules file. Inherits from AGENTS.md.
Applies to ANY agent running tests, writing tests, or
validating builds against a real Android device.
Last updated: 2026-06-12
═══════════════════════════════════════════════════════

This file answers, in one place, the 11 standard testing questions:
  1.  Project setup (Gradle dependencies + config)
  2.  Sample unit test (JUnit + MockK)
  3.  Sample Espresso instrumented test
  4.  Sample UI Automator cross-app test
  5.  Compose UI test (since the app is 100 % Compose)
  6.  PowerShell run commands (build, install, test, single test, HTML report)
  7.  Monkey smoke test
  8.  Automation script (run-tests.ps1)
  9.  GitHub Actions workflow
  10. Common failure modes + fixes
  11. Agent rules (what THIS agent must / must not do)

The goal: a single PowerShell command kicks off a complete pass —
build → install → unit + instrumented tests → HTML report → exit code.

═══════════════════════════════════════════════════════
0. GOLDEN RULES (TESTING-SPECIFIC, in addition to AGENTS.md §0)
═══════════════════════════════════════════════════════
- ALWAYS check `adb devices` BEFORE running any instrumented test.
- ALWAYS save reports under `./logs/test-reports/<ts>/` (and the index.html path printed).
- NEVER auto-uninstall the app between test runs (it deletes the user's manually-set output folder URI grant). Use `adb install -r` instead.
- NEVER run tests on `release` build type (use `debug` or a dedicated `androidTest` build type).
- NEVER mark a flaky test "@Ignore" without filing a row in `AI/KNOWN_ISSUES.md`.
- NEVER run `monkey` for more than 5000 events in a single batch on a real device. Long monkey runs uninstall input methods on some OEMs.
- ALWAYS scrub local user identifiers from any Espresso screenshot before committing.
- ALWAYS use the FFmpeg test fixture (a 60-second 720p HEVC clip with embedded ASS subs and 2 audio tracks) under `app/src/androidTest/assets/fixture.mkv`. NEVER ship a real movie file as a test asset.

═══════════════════════════════════════════════════════
1. PROJECT SETUP — `app/build.gradle.kts`
═══════════════════════════════════════════════════════

Add these blocks to the existing `app/build.gradle.kts`. Order matters:
the `defaultConfig` and `testOptions` go inside `android { ... }`,
the dependencies go inside `dependencies { ... }`.

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.splitandmerge.mkvslice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.splitandmerge.mkvslice"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"

        // The instrumentation runner. AndroidJUnitRunner is enough for v1.
        // If we need Hilt-graph injection in androidTest later, swap to a custom
        // runner (com.splitandmerge.mkvslice.HiltTestRunner extends AndroidJUnitRunner).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Animations off for stable UI tests.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        // Required for unit tests that touch Android types (e.g. android.net.Uri).
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        // Re-install the app between modules so Room state is clean.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // -------- App runtime (already present in your scaffold) --------
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // -------- Local unit tests (`src/test/`) --------
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")     // for Flow assertions
    testImplementation("com.google.truth:truth:1.4.4")       // fluent assertions
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.13")   // when you need Android types in JVM tests

    // -------- Instrumented tests (`src/androidTest/`) --------
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("io.mockk:mockk-android:1.13.13")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Required runtime artefact for orchestrator (referenced in testOptions above).
    androidTestUtil("androidx.test:orchestrator:1.5.1")

    // Compose preview / debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

Add to `gradle.properties` for stable test execution:
```
android.useAndroidX=true
android.testInstrumentationRunnerArguments.clearPackageData=true
android.experimental.enableNewResourceShrinker=true
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
```

═══════════════════════════════════════════════════════
2. SAMPLE — UNIT TEST (JVM, MockK, Truth)
═══════════════════════════════════════════════════════
File: `app/src/test/kotlin/com/splitandmerge/mkvslice/domain/CleanupEngineTest.kt`

```kotlin
package com.splitandmerge.mkvslice.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CleanupEngineTest {

    private val engine = CleanupEngine(rules = CleanupEngine.builtIns())

    @Test fun `strips URL prefix and resolution token`() {
        val raw = "www.5MovieRulz.graphics - Kantara.Chapter.1.2024.4K.WEB-DL.x265.mkv"
        val result = engine.clean(raw)
        assertThat(result.title).isEqualTo("Kantara Chapter 1 (2024)")
        assertThat(result.folder).isEqualTo("Kantara Chapter 1 (2024)")
    }

    @Test fun `keeps year wrapped in parens`() {
        val raw = "Devara.2024.2160p.HDR.HEVC.mkv"
        val result = engine.clean(raw)
        assertThat(result.title).isEqualTo("Devara (2024)")
    }

    @Test fun `falls back to original name when cleanup yields too short`() {
        val raw = "abc.mkv"
        val result = engine.clean(raw)
        assertThat(result.title).isEqualTo("abc")
    }

    @Test fun `partNumberPattern produces zero-padded names`() {
        val name = engine.partName(base = "Kantara Chapter 1 (2024)", index = 3, ext = "mkv")
        assertThat(name).isEqualTo("Kantara Chapter 1 (2024).part003.mkv")
    }
}
```

ViewModel-level example using MockK + Turbine:

```kotlin
package com.splitandmerge.mkvslice.ui.splitconfig

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplitConfigViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val probeRepo = mockk<ProbeRepository>()
    private lateinit var vm: SplitConfigViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { probeRepo.probe(any()) } returns FakeProbes.bahubali4K()
        vm = SplitConfigViewModel(probeRepo)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `summary updates when cap changes`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()                        // initial loading
            vm.onProbeFinished(FakeProbes.bahubali4K())
            val ready = awaitItem()
            assertThat(ready.partsEstimate).isEqualTo(7)
            vm.onCapChanged(bytes = 4L * 1024 * 1024 * 1024)
            val recomputed = awaitItem()
            assertThat(recomputed.partsEstimate).isEqualTo(13)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

═══════════════════════════════════════════════════════
3. SAMPLE — INSTRUMENTED COMPOSE UI TEST (Espresso replacement for v1)
═══════════════════════════════════════════════════════
File: `app/src/androidTest/kotlin/com/splitandmerge/mkvslice/ui/SplitConfigScreenTest.kt`

The app is 100 % Compose. Use Compose UI test, not Espresso `onView`,
for any Composable. Espresso is reserved for legacy `View` interop and
cross-app scenarios (Section 4).

```kotlin
package com.splitandmerge.mkvslice.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitandmerge.mkvslice.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitConfigScreenTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun changingCapUpdatesSummary() {
        composeRule.activity.setContent {
            MaterialTheme { SplitConfigScreen(initial = FakeStates.bahubali()) }
        }

        composeRule.onNodeWithText("Configure split").assertIsDisplayed()

        // Change cap from 9 GB to 4 GB
        composeRule.onNodeWithContentDescription("Size cap value")
            .performTextReplacement("4")

        // Summary should now read "13 parts"
        composeRule.onNodeWithText("13").assertIsDisplayed()
        composeRule.onNodeWithText("parts").assertIsDisplayed()

        // Continue button enabled
        composeRule.onNodeWithText("Continue").performClick()
    }
}
```

For the ONE classic Espresso example asked for, against a hypothetical settings dialog hosted in an `AndroidView`:

```kotlin
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*

@Test fun setSizeCapAndAssertSummary() {
    onView(withId(R.id.edit_cap)).perform(replaceText("9"), closeSoftKeyboard())
    onView(withId(R.id.btn_apply)).perform(click())
    onView(withId(R.id.text_summary)).check(matches(withText("7 parts")))
}
```

═══════════════════════════════════════════════════════
4. SAMPLE — UI AUTOMATOR (cross-app: SAF picker)
═══════════════════════════════════════════════════════
SAF picker is system UI; Compose tests can't drive it. Use UI Automator.
File: `app/src/androidTest/kotlin/com/splitandmerge/mkvslice/ui/SafPickerFlowTest.kt`

```kotlin
package com.splitandmerge.mkvslice.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SafPickerFlowTest {

    private lateinit var device: UiDevice

    @Before fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
    }

    @Test fun pickFolderViaSafFromOnboarding() {
        // Launch app
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val pkg = ctx.packageName
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)!!
            .apply { addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        ctx.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10_000)

        // Onboarding "Pick output folder" button
        device.findObject(By.text("Pick output folder")).click()

        // System SAF picker — by package
        device.wait(Until.hasObject(By.pkg("com.google.android.documentsui").depth(0)), 10_000)
        // Tap overflow → Show internal storage if hidden (OEM-dependent; safe to skip if absent)
        device.findObject(By.desc("More options"))?.click()
        device.findObject(By.text("Show internal storage"))?.click()

        // Walk to /Movies (existence varies; just confirm we're in the picker)
        val movies = device.wait(Until.findObject(By.text("Movies")), 5_000)
        movies?.click()
        device.findObject(By.text("USE THIS FOLDER"))?.click()
        device.findObject(By.text("ALLOW"))?.click()

        // Back in our app — Library should now be reachable
        device.wait(Until.hasObject(By.text("Video Splitter")), 10_000)
    }
}
```

═══════════════════════════════════════════════════════
5. SAMPLE — SCREENSHOTS ON FAILURE
═══════════════════════════════════════════════════════
Add to `app/src/androidTest/kotlin/.../TakeScreenshotRule.kt`:

```kotlin
import android.graphics.Bitmap
import androidx.test.core.app.takeScreenshot
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileOutputStream
import androidx.test.platform.app.InstrumentationRegistry

class TakeScreenshotRule : TestWatcher() {
    override fun failed(e: Throwable?, description: Description?) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.getExternalFilesDir(null), "test-failures").apply { mkdirs() }
        val name = "${description?.className?.substringAfterLast('.')}_${description?.methodName}.png"
        FileOutputStream(File(dir, name)).use { os ->
            takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, os)
        }
    }
}
```

Add to any test class:
```kotlin
@get:Rule(order = 1) val screenshotRule = TakeScreenshotRule()
```

═══════════════════════════════════════════════════════
6. POWERSHELL RUN COMMANDS (manual)
═══════════════════════════════════════════════════════

Variables (set once per shell):
```powershell
$adb     = "D:\idm\platform-tools-latest-windows\platform-tools\adb.exe"
$gradle  = ".\gradlew.bat"
$pkg     = "com.splitandmerge.mkvslice.debug"
$apkDbg  = "app\build\outputs\apk\debug\app-debug.apk"
$apkTest = "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
```

A — Build debug + androidTest APKs
```powershell
& $gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --warning-mode=summary
```

B — Verify device
```powershell
& $adb devices
# expect exactly one row with status "device"
```

C — Install app + test APK and run all instrumented tests
```powershell
& $gradle :app:connectedDebugAndroidTest --no-daemon
# Or, if you prefer manual install:
& $adb install -r $apkDbg
& $adb install -r $apkTest
& $adb shell am instrument -w -r `
    -e debug false `
    "com.splitandmerge.mkvslice.test/androidx.test.runner.AndroidJUnitRunner"
```

D — Run a single test class
```powershell
& $gradle :app:connectedDebugAndroidTest `
    -Pandroid.testInstrumentationRunnerArguments.class=com.splitandmerge.mkvslice.ui.SplitConfigScreenTest
```

E — Run a single test method
```powershell
& $gradle :app:connectedDebugAndroidTest `
    -Pandroid.testInstrumentationRunnerArguments.class=com.splitandmerge.mkvslice.ui.SplitConfigScreenTest#changingCapUpdatesSummary
```

F — Quick monkey smoke test (5 000 events, seed 42, on debug app)
```powershell
& $adb shell monkey -p $pkg --throttle 50 -s 42 -v 5000
# `--throttle` prevents flooding; `-v` increases verbosity; `-s` seeds for reproducibility.
# If it crashes, the stderr contains a Java stack trace.
```

G — HTML test report
```powershell
$report = "app\build\reports\androidTests\connected\debug\index.html"
if (Test-Path $report) { Start-Process $report }
```

H — Coverage (optional, JaCoCo)
```powershell
& $gradle :app:createDebugCoverageReport
$cov = "app\build\reports\coverage\androidTest\debug\connected\index.html"
if (Test-Path $cov) { Start-Process $cov }
```

I — Pull screenshots taken on failure
```powershell
& $adb shell "run-as $pkg sh -c 'ls files/test-failures'"  # list
& $adb pull "/sdcard/Android/data/$pkg/files/test-failures" "logs/test-failures"
```

═══════════════════════════════════════════════════════
7. AUTOMATION SCRIPT — `run-tests.ps1`
═══════════════════════════════════════════════════════
A complete script lives at the repo root: `run-tests.ps1`.
Usage:
```powershell
.\run-tests.ps1                       # build + unit + instrumented + report
.\run-tests.ps1 -SkipUnit             # skip JVM unit tests
.\run-tests.ps1 -SkipInstrumented     # skip instrumented tests
.\run-tests.ps1 -OnlyClass com.splitandmerge.mkvslice.ui.SplitConfigScreenTest
.\run-tests.ps1 -OnlyMethod ".SplitConfigScreenTest#changingCapUpdatesSummary"
.\run-tests.ps1 -Monkey 2000          # additionally fire a monkey smoke
.\run-tests.ps1 -OpenReport           # opens index.html when done
```

The script:
  1. Verifies `./gradlew.bat` and ADB exist.
  2. Runs `adb devices`. Aborts unless exactly one device is online.
  3. Creates `logs/test-reports/<ts>/`.
  4. Runs unit tests (unless skipped) → log → fail-fast on red.
  5. Builds `assembleDebug` + `assembleDebugAndroidTest`.
  6. Runs `connectedDebugAndroidTest` (or filtered by class/method).
  7. Optionally fires `adb shell monkey`.
  8. Pulls the HTML report into `logs/test-reports/<ts>/`.
  9. Pulls failure screenshots if any.
  10. Prints a colour-coded pass/fail summary with the report path.
  11. Exits non-zero on any red.

The script body is in `run-tests.ps1` — see Part 2 of this file for the
contents and the matching `.github/workflows/android-test.yml`.

═══════════════════════════════════════════════════════
8. GITHUB ACTIONS — `.github/workflows/android-test.yml`
═══════════════════════════════════════════════════════
The CI runs unit tests on every PR and instrumented tests on an emulator.
Local physical-device runs (your case) keep using `run-tests.ps1`.

```yaml
name: Android Tests

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  unit-tests:
    name: JVM unit tests
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Gradle cache
        uses: gradle/actions/setup-gradle@v4

      - name: Run unit tests
        run: ./gradlew :app:testDebugUnitTest --no-daemon --warning-mode=summary

      - name: Upload unit test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-report
          path: app/build/reports/tests/testDebugUnitTest

  instrumented-tests:
    name: Instrumented tests (emulator)
    runs-on: ubuntu-latest
    timeout-minutes: 60
    strategy:
      fail-fast: false
      matrix:
        api-level: [30]
        target: [google_apis]
        arch: [x86_64]

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Gradle cache
        uses: gradle/actions/setup-gradle@v4

      - name: Enable KVM (Linux runner)
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: AVD cache
        uses: actions/cache@v4
        id: avd-cache
        with:
          path: |
            ~/.android/avd/*
            ~/.android/adb*
          key: avd-${{ matrix.api-level }}-${{ matrix.target }}-${{ matrix.arch }}

      - name: Create AVD snapshot
        if: steps.avd-cache.outputs.cache-hit != 'true'
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          target: ${{ matrix.target }}
          arch: ${{ matrix.arch }}
          force-avd-creation: false
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          disable-animations: true
          script: echo "Generated AVD snapshot."

      - name: Run instrumented tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          target: ${{ matrix.target }}
          arch: ${{ matrix.arch }}
          force-avd-creation: false
          emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          disable-animations: true
          script: ./gradlew :app:connectedDebugAndroidTest --no-daemon

      - name: Upload instrumented test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: instrumented-test-report
          path: app/build/reports/androidTests/connected

      - name: Upload screenshots on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: instrumented-test-screenshots
          path: app/build/outputs/connected_android_test_additional_output
```

NOTES
- The default API-level matrix is 30. Add 33 / 34 / 35 once stable; emulator boot
  is slower the higher you go.
- KVM is enabled because Ubuntu runners now ship with hardware-accel.
- Cache miss means a fresh AVD takes ~7 minutes; subsequent runs are <2 min.
- For the engine smoke test (a real FFmpeg invocation), the test fixture must be
  small (~10 MB) — keep `app/src/androidTest/assets/fixture.mkv` under 20 MB so
  CI doesn't time out.

═══════════════════════════════════════════════════════
9. COMMON FAILURE MODES + FIXES
═══════════════════════════════════════════════════════

F1 — `INSTALL_FAILED_INSUFFICIENT_STORAGE` on `connectedDebugAndroidTest`
  Cause:   Old test APKs accumulate; Android keeps installed test packages.
  Fix:     `& $adb uninstall com.splitandmerge.mkvslice.debug.test` (test pkg only,
           NOT the app), then re-run. The script handles this when `-Clean` is set.

F2 — `Test orchestrator` fails to start
  Cause:   `androidTestUtil("androidx.test:orchestrator:...")` missing or
           `testOptions.execution` not set to `ANDROIDX_TEST_ORCHESTRATOR`.
  Fix:     Re-check `app/build.gradle.kts` against Section 1.

F3 — Compose tests fail with "No Compose hierarchy found"
  Cause:   `createAndroidComposeRule` was used but Activity didn't call
           `setContent { ... }`, or the test triggered before Activity was ready.
  Fix:     Add a `composeRule.waitForIdle()` after launching content.

F4 — UI Automator can't find SAF buttons
  Cause:   OEMs (Xiaomi MIUI, OnePlus) replace DocumentsUI with their own.
  Fix:     The script uses `By.pkg("com.google.android.documentsui")`. On affected
           devices the picker is `com.android.documentsui` or an OEM package; add
           an explicit fallback.

F5 — Tests pass locally but fail in GitHub Actions
  Cause:   Animations, dynamic colour, locale, timezone, SAF picker availability.
  Fix:     The CI emulator has `-no-window -no-boot-anim -noaudio` and animations
           disabled. SAF tests are gated by `assumeTrue(...)` on physical devices
           only — see `app/src/androidTest/.../SafPickerFlowTest.kt#@Before`.

F6 — Monkey crashes the app
  Cause:   Real bugs OR system input getting confused by long monkey runs.
  Fix:     Capture the stack trace from `adb shell monkey` stderr; reproduce
           manually before filing. Cap monkey to 5 000 events on real devices.

F7 — Emulator boots but tests time out
  Cause:   `adb wait-for-device` finished before all Compose code was JITted.
  Fix:     Add `device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10_000)`
           before the first interaction.

F8 — `androidTestUtil` cannot be found
  Cause:   AGP < 8.0 or a stale Gradle cache.
  Fix:     `./gradlew --stop` then `./gradlew :app:connectedDebugAndroidTest --refresh-dependencies`.

F9 — FFmpeg engine test fails on CI but passes on device
  Cause:   The CI emulator is x86_64; our `abiFilters` only ships `arm64-v8a`.
  Fix:     Two options: (a) skip FFmpeg engine tests on CI by tagging
           `@RequiresAbi("arm64-v8a")` and gating with `assumeTrue`. (b) ship a
           secondary `androidTestImplementation` flavour with all ABIs. v1 uses (a).

═══════════════════════════════════════════════════════
10. AGENT RULES — what THIS agent must / must not do
═══════════════════════════════════════════════════════

DO
  - Always read AGENTS.md §0 + §6 BEFORE running any test command.
  - Always run unit tests before instrumented tests; fail fast on red.
  - Always log to `logs/test-reports/<ts>/`.
  - Always upload the HTML report path in the final summary.
  - Always assert exit code: a green run is exit 0; any red is non-zero.
  - Always write a one-line summary line at the end:
        TESTS: passed=X failed=Y skipped=Z report="<path>"
    so the orchestrating agent can grep it.
  - When asked to "add a test for X", first locate the closest existing
    test file by package and append a method there; only create new files
    when the package has no existing test.

DON'T
  - Don't change `compileSdk`, `targetSdk`, `minSdk`, or `abiFilters`
    to make a test pass.
  - Don't `@Ignore` a flaky test without a row in `AI/KNOWN_ISSUES.md`.
  - Don't introduce new test dependencies without listing them in this file
    AND in `AI/TESTING.md`.
  - Don't run instrumented tests on a release variant — always debug.
  - Don't push test fixtures larger than 20 MB to the repo.
  - Don't commit a test that requires the user's specific output folder URI.

WHEN TO ESCALATE TO THE MAIN AGENT (AGENTS.md)
  - More than 10 red tests across a single run.
  - Any test failure that points to a missing engine binary (FFmpeg .so).
  - Any test failure that requires a permission addition.
  - A change in `app/build.gradle.kts` outside the `dependencies` block.

═══════════════════════════════════════════════════════
11. FILE LAYOUT (created by the testing agent)
═══════════════════════════════════════════════════════
  app/
    build.gradle.kts                        (edited per Section 1)
    src/
      test/kotlin/com/splitandmerge/mkvslice/
        domain/CleanupEngineTest.kt
        ui/splitconfig/SplitConfigViewModelTest.kt
      androidTest/kotlin/com/splitandmerge/mkvslice/
        ui/SplitConfigScreenTest.kt
        ui/SafPickerFlowTest.kt
        engine/EngineSmokeTest.kt           (real FFmpeg, optional)
        TakeScreenshotRule.kt
      androidTest/assets/
        fixture.mkv                         (~10 MB synthetic 720p HEVC + ASS)
  run-tests.ps1                             (Section 7)
  .github/workflows/android-test.yml        (Section 8)
  AI/TESTING.md                             (mirrors this file's user-facing parts)

═══════════════════════════════════════════════════════
END OF TESTING-AGENT.md
═══════════════════════════════════════════════════════
