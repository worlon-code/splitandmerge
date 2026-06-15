package com.splitandmerge.mkvslice.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShimmerSkeletonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun test_shimmerSkeleton_smokeTest() {
        composeTestRule.setContent {
            Box(modifier = Modifier.size(100.dp).shimmer())
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().assertExists()
    }
}
