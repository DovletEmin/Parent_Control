package com.parentcontrol.child.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.parentcontrol.child.service.CameraStreamService

class CameraRequestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val parentId = intent.getStringExtra(EXTRA_PARENT_ID)
        
        val serviceIntent = Intent(this, CameraStreamService::class.java).apply {
            putExtra(CameraStreamService.EXTRA_PARENT_ID, parentId)
        }
        startForegroundService(serviceIntent)
        
        finish()
    }

    companion object {
        const val EXTRA_PARENT_ID = "extra_parent_id"
    }
}
