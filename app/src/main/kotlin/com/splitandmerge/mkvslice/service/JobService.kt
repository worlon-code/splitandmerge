package com.splitandmerge.mkvslice.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class JobService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
