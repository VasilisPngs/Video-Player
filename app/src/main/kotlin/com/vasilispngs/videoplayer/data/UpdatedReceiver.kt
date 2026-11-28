package com.vasilispngs.videoplayer.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED || !File(context.noBackupFilesDir, UPDATED_MARKER).isFile) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.consumeSelfUpdate()) context.notifyUpdated()
            } finally {
                pending.finish()
            }
        }
    }
}
