package com.splitandmerge.mkvslice.di

import android.content.Context
import com.splitandmerge.mkvslice.data.update.ApkMetaProvider
import com.splitandmerge.mkvslice.data.update.InstalledAppMetaProvider
import com.splitandmerge.mkvslice.data.update.RealApkMetaProvider
import com.splitandmerge.mkvslice.data.update.RealInstalledAppMetaProvider
import com.splitandmerge.mkvslice.data.update.RealSessionInstaller
import com.splitandmerge.mkvslice.data.update.SessionInstaller
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideInstalledAppMetaProvider(
        @ApplicationContext context: Context
    ): InstalledAppMetaProvider {
        return RealInstalledAppMetaProvider(context)
    }

    @Provides
    @Singleton
    fun provideApkMetaProvider(
        @ApplicationContext context: Context
    ): ApkMetaProvider {
        return RealApkMetaProvider(context)
    }

    @Provides
    @Singleton
    fun provideSessionInstaller(
        @ApplicationContext context: Context
    ): SessionInstaller {
        return RealSessionInstaller(context)
    }
}
