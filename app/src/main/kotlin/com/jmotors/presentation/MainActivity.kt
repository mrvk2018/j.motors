package com.jmotors.presentation

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.jmotors.presentation.ar.ArShowroomScreen

/**
 * Entry: landscape SBS 3D showroom for XREAL Air 2 Pro —
 * pitch-black passthrough, glowing AI sphere, floating car.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                ArShowroomScreen()
            }
        }
    }
}
