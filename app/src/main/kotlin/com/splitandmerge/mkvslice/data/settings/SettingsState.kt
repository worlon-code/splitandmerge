package com.splitandmerge.mkvslice.data.settings

enum class ThemeMode {
    LIGHT, DARK, AMOLED, DYNAMIC
}

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.DYNAMIC,
    val defaultCapGb: Double = 9.0,
    val improveReliability: Boolean = true,
    val keepScreenOn: Boolean = false,
    val defaultOutputFolderUri: String = "",
    val lastOfferedVersionCode: Int = 0
)
