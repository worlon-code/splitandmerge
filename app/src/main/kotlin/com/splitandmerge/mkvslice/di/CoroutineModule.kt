package com.splitandmerge.mkvslice.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the application-level [CoroutineScope] whose lifetime matches the process.
 * Injected into [com.splitandmerge.mkvslice.App] and
 * [com.splitandmerge.mkvslice.service.JobService].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Deferred completed by [com.splitandmerge.mkvslice.App.onCreate] after the startup
     * recovery sweep finishes. [com.splitandmerge.mkvslice.service.JobService] awaits
     * this before polling the queue, preventing a race on slow devices (A6).
     */
    @Provides
    @Singleton
    fun provideStartupReadyDeferred(): CompletableDeferred<Unit> = CompletableDeferred()
}
