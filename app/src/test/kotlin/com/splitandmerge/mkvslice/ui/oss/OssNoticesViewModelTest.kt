package com.splitandmerge.mkvslice.ui.oss

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OssNoticesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var mockAssets: AssetManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mockk()
        mockAssets = mockk()
        every { mockContext.assets } returns mockAssets
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun jsonStream(content: String) = content.byteInputStream()

    @Test
    fun test_init_loadsFromAsset_emitsNotices() = runTest {
        val json = """
            [{"name":"LibA","publisher":"PubA","license":"Apache-2.0","licenseTextOrUrl":"https://example.com"}]
        """.trimIndent()
        every { mockAssets.open("oss-licenses.json") } returns jsonStream(json)

        val vm = OssNoticesViewModel(mockContext)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertNull("error should be null on success", state.error)
        assertTrue("isLoading should be false after load", !state.isLoading)
        assertEquals(1, state.notices.size)
        assertEquals("LibA", state.notices[0].name)
        assertEquals("Apache-2.0", state.notices[0].license)
        assertEquals("https://example.com", state.notices[0].licenseTextOrUrl)
    }

    @Test
    fun test_init_assetMissing_emitsError() = runTest {
        every { mockAssets.open("oss-licenses.json") } throws java.io.FileNotFoundException("oss-licenses.json")

        val vm = OssNoticesViewModel(mockContext)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("isLoading should be false", !state.isLoading)
        assertNotNull("error should be non-null", state.error)
        assertEquals("Could not load license information.", state.error)
        assertTrue("notices should be empty on error", state.notices.isEmpty())
    }

    @Test
    fun test_init_assetMalformed_emitsError() = runTest {
        every { mockAssets.open("oss-licenses.json") } returns jsonStream("not valid json {{{")

        val vm = OssNoticesViewModel(mockContext)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("isLoading should be false", !state.isLoading)
        assertNotNull("error should be non-null on malformed json", state.error)
        assertEquals("Could not load license information.", state.error)
        assertTrue("notices should be empty on error", state.notices.isEmpty())
    }

    @Test
    fun test_init_validAsset_listSizeEqualsExpected() = runTest {
        val json = """
            [
              {"name":"A","publisher":"PA","license":"Apache-2.0","licenseTextOrUrl":"https://a.com"},
              {"name":"B","publisher":"PB","license":"MIT","licenseTextOrUrl":"https://b.com"},
              {"name":"C","publisher":"PC","license":"LGPL-3.0","licenseTextOrUrl":"https://c.com"}
            ]
        """.trimIndent()
        every { mockAssets.open("oss-licenses.json") } returns jsonStream(json)

        val vm = OssNoticesViewModel(mockContext)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, vm.uiState.value.notices.size)
        assertTrue("no error on valid json", vm.uiState.value.error == null)
    }
}
