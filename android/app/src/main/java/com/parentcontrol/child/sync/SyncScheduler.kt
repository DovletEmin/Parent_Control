package com.parentcontrol.child.sync

import android.content.Context

object SyncScheduler {

    fun scheduleAll(context: Context) {
        AppUsageSyncWorker.schedule(context)
        CallLogSyncWorker.schedule(context)
        SmsSyncWorker.schedule(context)
        LocationSyncWorker.schedule(context)
        MediaSyncWorker.schedule(context)
    }
}
