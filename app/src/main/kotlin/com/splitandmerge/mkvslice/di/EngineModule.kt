package com.splitandmerge.mkvslice.di

import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.impl.ProcessFfmpegEngine
import com.splitandmerge.mkvslice.engine.impl.ProcessFfprobeEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface EngineModule {

    @Binds
    @Singleton
    fun bindFfmpegEngine(impl: ProcessFfmpegEngine): FfmpegEngine

    @Binds
    @Singleton
    fun bindFfprobeEngine(impl: ProcessFfprobeEngine): FfprobeEngine
}
