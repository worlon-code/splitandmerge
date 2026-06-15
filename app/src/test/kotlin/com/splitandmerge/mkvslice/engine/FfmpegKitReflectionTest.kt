package com.splitandmerge.mkvslice.engine

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import org.junit.Test
import java.lang.reflect.Method

class FfmpegKitReflectionTest {
    @Test
    fun dumpMethods() {
        println("=== FFmpegKitConfig ===")
        FFmpegKitConfig::class.java.methods.forEach { method ->
            println(formatMethod(method))
        }
        
        println("\n=== FFmpegKit ===")
        FFmpegKit::class.java.methods.forEach { method ->
            println(formatMethod(method))
        }
    }

    private fun formatMethod(m: Method): String {
        val params = m.parameterTypes.joinToString(", ") { it.simpleName }
        return "${m.name}($params) : ${m.returnType.simpleName}"
    }
}
