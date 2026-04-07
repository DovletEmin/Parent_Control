package com.parentcontrol.child.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.parentcontrol.child.service.ScreenStreamService

class ScreenCaptureActivity : AppCompatActivity() {

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenStreamService::class.java).apply {
                putExtra(ScreenStreamService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenStreamService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenStreamService.EXTRA_PARENT_ID, parentId)
            }
            startForegroundService(intent)
        }
        finish()
    }

    private var parentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentId = intent.getStringExtra(EXTRA_PARENT_ID)
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    companion object {
        const val EXTRA_PARENT_ID = "extra_parent_id"
    }
}
