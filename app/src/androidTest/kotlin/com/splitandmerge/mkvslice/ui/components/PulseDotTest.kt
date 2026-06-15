package com.splitandmerge.mkvslice.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PulseDotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun test_pulseDot_smokeTest() {
        composeTestRule.setContent { PulseDot() }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().assertExists()
    }
}
