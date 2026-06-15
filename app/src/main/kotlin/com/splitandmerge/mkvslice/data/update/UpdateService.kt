package com.splitandmerge.mkvslice.data.update

import retrofit2.http.GET

interface UpdateService {
    @GET("update.json")
    suspend fun fetchManifest(): UpdateManifest
}
